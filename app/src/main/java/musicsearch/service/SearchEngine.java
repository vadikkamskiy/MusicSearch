package musicsearch.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.io.File;

import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import musicsearch.models.CurrentTrackListener;
import musicsearch.models.DataUpdateListener;
import musicsearch.models.MediaModel;
import musicsearch.models.PlaybackListener;
import musicsearch.service.Events.ArtistSearchEvent;
import musicsearch.service.Events.LyricSearchEvent;
import musicsearch.widgets.MediaWidget;

public class SearchEngine {
    private final ListProperty<MediaModel> results = new SimpleListProperty<>(
            FXCollections.observableArrayList()
    );
    private List<MediaModel> LocalFiles = new ArrayList<>();
    private CurrentTrackListener currentTrackListener;
    private FindLyrics lyricsFinder = new FindLyrics();
    private GridPane mediaLayout;
    private PlaybackListener playbackListener;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Dotenv dotenv = Dotenv.load();
    public static int searchPage = 1;
    public static int allPage;
    public static String currentQuery = "null";
    public static final String lyricsUrl = dotenv.get("LYRICS_SOURCE");
    
    public SearchEngine(GridPane mediaLayout) {
        this.mediaLayout = mediaLayout;
        results.addListener((Observable obs) -> updateMediaLayout());
        initializeEventListeners();
    }

    public SearchEngine(GridPane mediaLayout, PlaybackListener playbackListener) {
        this.mediaLayout = mediaLayout;
        this.playbackListener = playbackListener;
        results.addListener((Observable obs) -> updateMediaLayout());
        initializeEventListeners();
    }

    private void initializeEventListeners() {
        EventBus.subscribe(ArtistSearchEvent.class, event -> {
            search(event.artist);
        });
        EventBus.subscribe(LyricSearchEvent.class, this::handleLyricSearch);
    }

    public void setPlaybackListener(PlaybackListener playbackListener) {
        this.playbackListener = playbackListener;
    }

    public void search(String query) {
        currentQuery = query;
        results.clear();
        executor.submit(() -> {
            try {
                String searchUrl = "https://" +
                    dotenv.get("URL_SOURCE") +
                    "/search?q=" + query;
                Document doc = Jsoup.connect(searchUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .timeout(5000)
                        .maxBodySize(0) 
                        .get();

                Elements tracks = doc.select("li.tracks__item.track.mustoggler");
                Elements pages = doc.select(".pagination__item");
                allPage = pages.size();
                List<MediaModel> newModels = new ArrayList<>();

                for (Element track : tracks) {
                    String musmeta = track.attr("data-musmeta");
                    if (musmeta == null || musmeta.isEmpty()) continue;

                    JsonObject obj = JsonParser.parseString(musmeta).getAsJsonObject();
                    String artist = obj.get("artist").getAsString();
                    String title = obj.get("title").getAsString();
                    String imageUrl = obj.get("img").getAsString();
                    String downloadUrl = obj.get("url").getAsString();
                    String time = track.selectFirst("div.track__fulltime") != null
                            ? track.selectFirst("div.track__fulltime").text()
                            : "Unknown";
                    boolean isDownloaded = false;
                    if(LocalFiles.stream().anyMatch(m -> m.getTitle().equals(artist + " - " + title))) {
                        isDownloaded = true;
                    }
                    MediaModel model = new MediaModel(artist + " - " + title, time, downloadUrl, imageUrl, isDownloaded);
                    newModels.add(model);
                }

                Platform.runLater(() -> {
                    results.setAll(newModels);
                });
                tracks.clear();
                doc.clearAttributes();

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateMediaLayout() {
        mediaLayout.getChildren().clear();
        int columnsCount = 5;
        for (int i = 0; i < results.size(); i++) {
            MediaModel model = results.get(i);
            MediaWidget widget = new MediaWidget(model, playbackListener, new DataUpdateListener() {
                @Override
                public void onDataChanged() {
                    goHome();
                }
            });
            
            if (currentTrackListener != null) {
            }
            
            int column = i % columnsCount;
            int row = i / columnsCount;
            mediaLayout.add(widget, column, row);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void setCurrentTrackListener(CurrentTrackListener listener) {
        this.currentTrackListener = listener;
    }
    public void goHome() {
        currentQuery = "null";
        results.clear();
        File homeDir = new File(System.getProperty("user.home"), "Music");
        File[] files = homeDir.listFiles((dir, name) -> name.endsWith(".mp3") || name.endsWith(".flac"));
        if (files != null) {
            List<MediaModel> homeModels = new ArrayList<>();
            for (File file : files) {
                try {
                    AudioFile audioFile = AudioFileIO.read(file);
                    Tag tag = audioFile.getTag();
                    String artist = tag.getFirst(FieldKey.ARTIST);
                    String title = tag.getFirst(FieldKey.TITLE);
                    String duration = String.valueOf(audioFile.getAudioHeader().getTrackLength() / 60) + ":" +
                            String.format("%02d", audioFile.getAudioHeader().getTrackLength() % 60);
                    MediaModel model = new MediaModel(artist + " - " + title, duration, file.toURI().toString(), "", true);
                    homeModels.add(model);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            results.setAll(homeModels);
        }
    }

    public void loadMoreResults() {
        if(searchPage<allPage && !currentQuery.equals("null")){
            executor.submit(() -> {
                try {
                    String searchUrl = "https://" +
                        dotenv.get("URL_SOURCE") +
                        "/search/start/" + 48 * searchPage + "?q=" + currentQuery;
                    Document doc = Jsoup.connect(searchUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .timeout(5000)
                            .maxBodySize(0) 
                            .get();
                    System.out.println(searchUrl);
                    Elements tracks = doc.select("li.tracks__item.track.mustoggler");
                    List<MediaModel> newModels = new ArrayList<>();

                    for (Element track : tracks) {
                        String musmeta = track.attr("data-musmeta");
                        if (musmeta == null || musmeta.isEmpty()) continue;

                        JsonObject obj = JsonParser.parseString(musmeta).getAsJsonObject();
                        String artist = obj.get("artist").getAsString();
                        String title = obj.get("title").getAsString();
                        String imageUrl = obj.get("img").getAsString();
                        String downloadUrl = obj.get("url").getAsString();
                        String time = track.selectFirst("div.track__fulltime") != null
                                ? track.selectFirst("div.track__fulltime").text()
                                : "Unknown";
                        boolean isDownloaded = false;
                        if(LocalFiles.stream().anyMatch(m -> m.getTitle().equals(artist + " - " + title))) {
                            isDownloaded = true;
                        }
                        MediaModel model = new MediaModel(artist + " - " + title, time, downloadUrl, imageUrl, isDownloaded);
                        newModels.add(model);
                    }

                    Platform.runLater(() -> {
                        results.addAll(newModels);
                    });
                    tracks.clear();
                    doc.clearAttributes();
                    searchPage++;

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void onTrackDeleted(MediaModel media) {
        goHome();
    }

    private void handleLyricSearch(LyricSearchEvent event) {
        String track = event.track;
        System.out.println("Searching lyrics for: " + track);
        
        executor.submit(() -> {
            Text lyricsText = findLyrics(track);
            Platform.runLater(() -> {
                if (lyricsText != null) {
                    showLyricsWindow(lyricsText, track);
                } else {
                    showErrorAlert("Lyrics not found for: " + track);
                }
            });
        });
        EventBus.subscribe(LyricSearchEvent.class, event-> {
            findLyrics(event.track);
        });
    }

    public List<MediaModel> getResults() {
        return new ArrayList<>(results.get());
    }

    public Text findLyrics(String track) {
        if (track == null || track.trim().isEmpty()) return null;

        String cleanedTrack = cleanTrackTitle(track);
        System.out.println("Original: " + track);
        System.out.println("Cleaned: " + cleanedTrack);

        Text directResult = findLyricsDirect(cleanedTrack);
        if (directResult != null) {
            System.out.println("✓ Found via direct URL");
            return directResult;
        }

        System.out.println("Direct approach failed, trying search...");
        
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("=== SEARCH ATTEMPT " + attempt + " ===");
                Text result = findLyricsViaImprovedSearch(cleanedTrack);
                if (result != null) return result;
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
            }

            if (attempt < maxRetries) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }

        System.out.println("All search attempts failed");
        return null;
    }

    private String cleanTrackTitle(String track) {
        if (track == null) return "";
        
        String cleaned = track.replaceAll("\\([^)]*\\)", "");
        
        cleaned = cleaned.replaceAll("\\[[^]]*\\]", "");
        
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
        cleaned = cleaned.replaceAll(" - - ", " - ").replaceAll("--", "-");
        
        return cleaned;
    }

    private Text findLyricsDirect(String track) {
        try {
            String[] directUrls = generateDirectUrls(track);
            
            for (String url : directUrls) {
                System.out.println("Trying direct URL: " + url);
                
                try {
                    Document lyricsDoc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(10000)
                            .ignoreHttpErrors(true)
                            .get();

                    if (lyricsDoc.selectFirst("div[data-lyrics-container=true]") != null || 
                        lyricsDoc.selectFirst("div.lyrics") != null) {
                        
                        Text lyrics = extractLyrics(lyricsDoc);
                        if (lyrics != null && !lyrics.getText().trim().isEmpty()) {
                            return lyrics;
                        }
                    }
                    
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    System.err.println("Direct URL failed: " + url);
                }
            }
        } catch (Exception e) {
            System.err.println("Direct approach failed: " + e.getMessage());
        }
        return null;
    }

    private String[] generateDirectUrls(String track) {
        List<String> urls = new ArrayList<>();
        
        String base = track.toLowerCase()
                .replace(" - ", "-")
                .replace(" ", "-")
                .replace("'", "")
                .replace(",", "")
                .replace("&", "and")
                .replace("--", "-");
        
        urls.add("https://genius.com/" + base + "-lyrics");
        
        if (track.contains(" - ")) {
            String[] parts = track.split(" - ", 2);
            String artist = parts[0].toLowerCase()
                    .replace(" ", "-")
                    .replace("'", "")
                    .replace(",", "");
            String song = parts[1].toLowerCase()
                    .replace(" ", "-")
                    .replace("'", "")
                    .replace(",", "");
            
            urls.add("https://genius.com/" + artist + "-" + song + "-lyrics");
        }
        
        return urls.toArray(new String[0]);
    }

    private Text findLyricsViaImprovedSearch(String track) {
        try {
            String normalized = track == null ? "" : track.trim().replaceAll("\\s+", " ");
            
            String searchUrl = lyricsUrl + "/search?q=" + normalized;
            
            System.out.println("Search URL: " + searchUrl);

            Document searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .referrer("https://www.google.com/")
                    .timeout(30000)
                    .ignoreHttpErrors(true)
                    .get();

            if (searchDoc == null) {
                System.out.println("Search page request failed");
                return null;
            }

            if (isBlockedPage(searchDoc)) {
                System.out.println("Genius is blocking the request");
                return null;
            }

            saveHtmlForDebug(searchDoc, "search_page");

            String lyricsPageUrl = findLyricsLinkEnhanced(searchDoc);
            
            if (lyricsPageUrl == null) {
                System.out.println("No lyrics link found after all strategies");
                return null;
            }

            System.out.println("Found lyrics page: " + lyricsPageUrl);

            Document lyricsDoc = Jsoup.connect(lyricsPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .timeout(30000)
                    .ignoreHttpErrors(true)
                    .get();

            saveHtmlForDebug(lyricsDoc, "lyrics_page");

            return extractLyrics(lyricsDoc);

        } catch (Exception e) {
            System.err.println("Error in findLyricsViaImprovedSearch: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String findLyricsLinkEnhanced(Document searchDoc) {
        System.out.println("=== ENHANCED LINK SEARCH ===");
        
        System.out.println("Page title: " + searchDoc.title());
        String bodyText = searchDoc.body().text();
        System.out.println("Body text sample: " + bodyText.substring(0, Math.min(200, bodyText.length())));

        String result = findMiniCardLink(searchDoc);
        if (result != null) return result;
        
        result = findTopResultLink(searchDoc);
        if (result != null) return result;
        
        result = findAnyLyricsLink(searchDoc);
        if (result != null) return result;
        
        result = findSearchResultLink(searchDoc);
        if (result != null) return result;
        
        System.out.println("No lyrics link found with any strategy");
        return null;
    }

    private String findMiniCardLink(Document doc) {
        System.out.println("Strategy 1: Looking for mini cards...");
        
        Elements miniCards = doc.select("a.mini_card");
        System.out.println("Found " + miniCards.size() + " mini cards");
        
        for (int i = 0; i < miniCards.size(); i++) {
            Element card = miniCards.get(i);
            String href = card.attr("href");
            String text = card.text();
            
            System.out.println("Mini card " + (i+1) + ": " + text + " -> " + href);
            
            if (href.contains("genius.com") && (href.contains("-lyrics") || href.matches(".*/[a-z0-9-]+-[a-z0-9-]+$"))) {
                System.out.println("Using mini card: " + href);
                return card.absUrl("href");
            }
        }
        
        return null;
    }

    private String findTopResultLink(Document doc) {
        System.out.println("Strategy 2: Looking for Top Result...");
        
        Element topLabel = doc.selectFirst("div:contains(Top Result)");
        if (topLabel == null) {
            topLabel = doc.selectFirst(":containsOwn(Top Result)");
        }
        
        if (topLabel != null) {
            System.out.println("Found Top Result label");
            
            Element container = topLabel;
            for (int i = 0; i < 5; i++) {
                if (container == null) break;
                
                Element link = container.selectFirst("a[href*='genius.com']");
                if (link != null) {
                    String href = link.attr("href");
                    if (href.contains("-lyrics") || href.matches(".*/[a-z0-9-]+-[a-z0-9-]+$")) {
                        System.out.println("Found Top Result link: " + href);
                        return link.absUrl("href");
                    }
                }
                container = container.parent();
            }
        }
        
        System.out.println("No Top Result found");
        return null;
    }

    private String findAnyLyricsLink(Document doc) {
        System.out.println("Strategy 3: Looking for any lyrics links...");
        
        Elements geniusLinks = doc.select("a[href*='genius.com']");
        System.out.println("Found " + geniusLinks.size() + " Genius links total");
        
        for (int i = 0; i < Math.min(10, geniusLinks.size()); i++) {
            Element link = geniusLinks.get(i);
            String href = link.attr("href");
            String text = link.text();
            
            if (!href.contains("/albums/") && 
                !href.contains("/artists/") && 
                !href.contains("/users/") &&
                (href.contains("-lyrics") || href.matches(".*/[a-z0-9-]+-[a-z0-9-]+$"))) {
                
                System.out.println("Found potential lyrics link: " + text + " -> " + href);
                return link.absUrl("href");
            }
        }
        
        return null;
    }

    private String findSearchResultLink(Document doc) {
        System.out.println("Strategy 4: Looking for search results...");
        
        Elements resultItems = doc.select("search-result-item, [class*='result'], [class*='search']");
        System.out.println("Found " + resultItems.size() + " result items");
        
        for (Element item : resultItems) {
            Element link = item.selectFirst("a[href*='genius.com']");
            if (link != null) {
                String href = link.attr("href");
                if (href.contains("-lyrics") || href.matches(".*/[a-z0-9-]+-[a-z0-9-]+$")) {
                    System.out.println("Found result item link: " + href);
                    return link.absUrl("href");
                }
            }
        }
        
        return null;
    }

    private boolean isBlockedPage(Document doc) {
        String title = doc.title().toLowerCase();
        String bodyText = doc.body().text().toLowerCase();
        
        return title.contains("bot") || title.contains("blocked") || 
            title.contains("access denied") || bodyText.contains("please enable javascript") ||
            bodyText.contains("cloudflare") || bodyText.contains("captcha");
    }

    private void saveHtmlForDebug(Document doc, String filename) {
        try {
            String html = doc.html();
            java.nio.file.Files.write(
                java.nio.file.Paths.get(filename + ".html"), 
                html.getBytes()
            );
            System.out.println("Saved " + filename + ".html for debugging");
        } catch (Exception e) {
            System.err.println("Could not save debug HTML: " + e.getMessage());
        }
    }

    private Text extractLyrics(Document lyricsDoc) {
        try {
            Elements lyricsContainers = lyricsDoc.select("div[data-lyrics-container=true]");
            
            if (lyricsContainers.isEmpty()) {
                return null;
            }

            StringBuilder allLyrics = new StringBuilder();
            
            for (Element container : lyricsContainers) {
                container.select("script, style, [class*='ad'], [class*='header'], [class*='Sidebar'], [class*='Footer']").remove();
                
                processLyricsContainer(container, allLyrics);
                
                allLyrics.append("\n\n");
            }

            String result = allLyrics.toString()
                .replaceAll("\n{3,}", "\n\n")
                .trim();
            
            if (!result.isEmpty()) {
                return new Text(result);
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting lyrics: " + e.getMessage());
        }
        return null;
    }

    private void processLyricsContainer(Element container, StringBuilder result) {
        for (Node node : container.childNodes()) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).text().trim();
                if (!text.isEmpty()) {
                    result.append(text).append("\n");
                }
            } else if (node instanceof Element) {
                Element element = (Element) node;
                String tagName = element.tagName();
                
                if (tagName.equals("br")) {
                    result.append("\n");
                } else if (tagName.equals("a")) {
                    String linkText = element.text().trim();
                    if (!linkText.isEmpty()) {
                        result.append(linkText).append("\n");
                    }
                } else {
                    processLyricsContainer(element, result);
                }
            }
        }
    }

    private void showLyricsWindow(Text lyricsText, String title) {
        Stage lyricsStage = new Stage();
        lyricsStage.setTitle("Lyrics: " + title);
        lyricsStage.initModality(Modality.APPLICATION_MODAL);
        
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        
        String formattedLyrics = formatLyricsText(lyricsText.getText());
        textArea.setText(formattedLyrics);
        
        textArea.setStyle("-fx-font-family: 'Arial'; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-color: #f8f8f8; " +
                        "-fx-text-fill: #333333;");
        
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-font-size: 14px; -fx-padding: 8 16;");
        closeButton.setOnAction(e -> lyricsStage.close());
        
        VBox layout = new VBox(15, textArea, closeButton);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");
        
        VBox.setVgrow(textArea, Priority.ALWAYS);
        
        Scene scene = new Scene(layout, 650, 480);
        lyricsStage.setScene(scene);
        
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                lyricsStage.close();
            }
        });
        
        lyricsStage.show();
        
        textArea.requestFocus();
    }

    private String formatLyricsText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "No lyrics found.";
        }
        
        String[] lines = rawText.split("\n");
        StringBuilder formatted = new StringBuilder();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.isEmpty()) {
                formatted.append("\n");
            } else if (trimmedLine.matches("\\[.*\\]")) {
                formatted.append("\n").append(trimmedLine).append("\n");
            } else {
                formatted.append(trimmedLine).append("\n");
            }
        }
        
        return formatted.toString().trim();
    }
    
    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}