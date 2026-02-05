package musicsearch.service;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

/**
 * Надёжный поиск лирики и вывод в простое окно с TextArea.
 */
public class FindLyrics {

    public void searchAndShowLyrics(Stage owner, String query) {
        String[] parts = query.split(" - ", 2);
        if (parts.length < 2) {
            showError(owner, "Неверный формат. Ожидается: \"исполнитель - название\"");
            return;
        }
        final String artist = parts[0].trim();
        final String title = parts[1].trim();

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                System.out.println("[FindLyrics] start for: " + artist + " - " + title);

                // 1) попытка прямого URL
                String direct = buildDirectAzlyricsUrl(artist, title);
                System.out.println("[FindLyrics] trying direct url: " + direct);
                try {
                    String txt = fetchLyricsPlainTextFromUrl(direct);
                    if (txt != null && !txt.isBlank()) {
                        System.out.println("[FindLyrics] found by direct URL");
                        return txt;
                    }
                } catch (IOException e) {
                    System.out.println("[FindLyrics] direct URL failed: " + e.getMessage());
                }

                // 2) поиск через поисковик (Google -> Bing)
                String found = findAzlyricsUrlBySearch(artist + " " + title);
                if (found != null) {
                    System.out.println("[FindLyrics] found via search: " + found);
                    try {
                        String txt = fetchLyricsPlainTextFromUrl(found);
                        if (txt != null && !txt.isBlank()) {
                            return txt;
                        }
                    } catch (IOException e) {
                        System.out.println("[FindLyrics] fetch from found URL failed: " + e.getMessage());
                    }
                } else {
                    System.out.println("[FindLyrics] search returned nothing");
                }

                throw new IOException("Лирика не найдена.");
            }
        };

        // UI: простое окно с индикатором и затем вывод TextArea
        Stage progress = new Stage();
        progress.initOwner(owner);
        progress.initModality(Modality.WINDOW_MODAL);
        VBox vb = new VBox(8, new ProgressIndicator());
        vb.setPadding(new Insets(12));
        progress.setScene(new Scene(vb));
        progress.setTitle("Поиск лирики...");
        task.setOnRunning(e -> progress.show());
        task.setOnSucceeded(e -> {
            progress.close();
            String lyrics = task.getValue();
            showLyricsWindow(owner, artist + " - " + title, lyrics);
        });
        task.setOnFailed(e -> {
            progress.close();
            Throwable ex = task.getException();
            showError(owner, "Не найдено: " + (ex != null ? ex.getMessage() : "Unknown"));
        });

        Thread t = new Thread(task, "find-lyrics");
        t.setDaemon(true);
        t.start();
    }

    // Собрать прямой URL по шаблону azlyrics
    private String buildDirectAzlyricsUrl(String artist, String title) {
        return "https://www.azlyrics.com/lyrics/" + normalizeForAzlyrics(artist) + "/" + normalizeForAzlyrics(title) + ".html";
    }

    // Основной парсер: возвращает plain text с переносами (\n) либо null
    private String fetchLyricsPlainTextFromUrl(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .referrer("https://www.google.com")
                .timeout(15_000)
                .maxBodySize(0)
                .followRedirects(true)
                .get();

        // Ищем комментарий с текстом "Usage of azlyrics.com content"
        for (Node node : doc.body().childNodes()) {
            if (node instanceof Comment) {
                Comment c = (Comment) node;
                String data = c.getData().toLowerCase();
                if (data.contains("usage of azlyrics.com content")) {
                    int idx = doc.body().childNodes().indexOf(node);
                    if (idx >= 0 && idx + 1 < doc.body().childNodes().size()) {
                        Node possible = doc.body().childNodes().get(idx + 1);
                        if (possible instanceof Element) {
                            Element lyricsDiv = (Element) possible;
                            String plain = convertLyricsElementToPlainText(lyricsDiv);
                            if (plain != null && !plain.isBlank()) return plain;
                        }
                    }
                }
            }
        }

        // Запасной вариант: пробуем большие div без класса и проверяем эвристикой
        List<Element> divs = doc.select("div:not([class])");
        for (Element d : divs) {
            String text = d.text().trim();
            if (looksLikeLyrics(text)) {
                return convertLyricsElementToPlainText(d);
            }
        }

        return null;
    }

    // Конвертация HTML-элемента с лирикой в plain text с сохранением переносов
    private String convertLyricsElementToPlainText(Element el) {
        StringBuilder sb = new StringBuilder();
        for (Node child : el.childNodes()) {
            if (child instanceof TextNode) {
                sb.append(((TextNode) child).text());
            } else if (child.nodeName().equals("br")) {
                sb.append('\n');
            } else if (child instanceof Element) {
                Element e = (Element) child;
                String name = e.tagName();
                if (name.equals("br")) {
                    sb.append('\n');
                } else if (name.equals("p")) {
                    String inner = e.text();
                    if (!inner.isBlank()) {
                        sb.append(inner).append('\n').append('\n');
                    }
                } else {
                    // рекурсивно обходим элемент — чтобы не терять вложенные теги
                    String inner = convertLyricsElementToPlainText(e);
                    if (inner != null && !inner.isEmpty()) {
                        sb.append(inner);
                    }
                }
            }
        }
        String res = sb.toString().replaceAll("\r", "");
        String[] lines = res.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            out.append(line);
            if (i < lines.length - 1) out.append('\n');
        }
        String finalText = out.toString().trim();
        return finalText.isEmpty() ? null : finalText;
    }

    private String findAzlyricsUrlBySearch(String q) {
        try {
            String google = "https://www.google.com/search?q=" + urlEncode("site:azlyrics.com " + q);
            String fromGoogle = extractAzlyricsFromSearchPage(google, true);
            if (fromGoogle != null) return fromGoogle;
        } catch (Exception e) {
            System.out.println("[FindLyrics] google search failed: " + e.getMessage());
        }

        try {
            String bing = "https://www.bing.com/search?q=" + urlEncode("site:azlyrics.com " + q);
            String fromBing = extractAzlyricsFromSearchPage(bing, false);
            if (fromBing != null) return fromBing;
        } catch (Exception e) {
            System.out.println("[FindLyrics] bing search failed: " + e.getMessage());
        }

        return null;
    }

    private String extractAzlyricsFromSearchPage(String searchUrl, boolean isGoogle) throws IOException {
        Document doc = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .referrer("https://www.google.com")
                .timeout(15_000)
                .maxBodySize(0)
                .get();

        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            String candidate = null;

            if (isGoogle) {
                if (href.startsWith("/url?q=")) {
                    int amp = href.indexOf('&', 7);
                    String raw = amp > 0 ? href.substring(7, amp) : href.substring(7);
                    try {
                        candidate = URLDecoder.decode(raw, "UTF-8");
                    } catch (UnsupportedEncodingException ignored) {}
                } else if (href.startsWith("http")) {
                    candidate = href;
                }
            } else {
                if (href.startsWith("http")) candidate = href;
            }

            if (candidate != null) {
                try {
                    URL u = new URL(candidate);
                    String host = u.getHost().toLowerCase();
                    String path = u.getPath().toLowerCase();
                    if (host.contains("azlyrics.com") && path.contains("/lyrics/")) {
                        return candidate;
                    }
                    // иногда ссылочный текст указывает на azlyrics (mirrors) — проверим текст
                    String text = a.text().toLowerCase();
                    if (text.contains("azlyrics") && candidate.contains("azlyrics")) {
                        return candidate;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s.replace(" ", "+");
        }
    }

    private String normalizeForAzlyrics(String s) {
        String lower = s.toLowerCase();
        lower = lower.replaceAll("&", "and");
        lower = lower.replaceAll("[’'`]", "");
        lower = lower.replaceAll("[^a-z0-9]", "");
        return lower;
    }

    private boolean looksLikeLyrics(String text) {
        if (text == null) return false;
        int words = text.split("\\s+").length;
        return words > 20 && text.length() > 100;
    }

    // Показываем окно с TextArea (plain text)
    private void showLyricsWindow(Stage owner, String title, String text) {
        Platform.runLater(() -> {
            musicsearch.widgets.LyricsWindow.show(owner, title, text);
        });
    }

    private void showError(Stage owner, String message) {
        Platform.runLater(() -> {
            Stage st = new Stage();
            st.initOwner(owner);
            st.initModality(Modality.WINDOW_MODAL);
            st.setTitle("Ошибка поиска лирики");
            TextArea area = new TextArea(message);
            area.setWrapText(true);
            area.setEditable(false);
            Button ok = new Button("Ок");
            ok.setOnAction(e -> st.close());
            BorderPane root = new BorderPane(area);
            root.setBottom(ok);
            BorderPane.setMargin(ok, new Insets(8));
            st.setScene(new Scene(root, 500, 200));
            st.show();
        });
    }
}
