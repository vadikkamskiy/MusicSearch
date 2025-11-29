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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private GridPane mediaLayout;
    private PlaybackListener playbackListener;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Dotenv dotenv = Dotenv.load();
    public static int searchPage = 1;
    public static int allPage;
    public static String currentQuery = "null";
    
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
                    "/search?q=" + query.replace(" ", "+");
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
    }

    public List<MediaModel> getResults() {
        return new ArrayList<>(results.get());
    }

    public Text findLyrics(String track) {
        int maxRetries = 2;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                System.out.println("=== LYRICS SEARCH ATTEMPT " + attempt + " ===");
                System.out.println("Searching for: " + track);
                
                Text directResult = findLyricsDirect(track);
                if (directResult != null) {
                    return directResult;
                }
                
                Text searchResult = findLyricsViaImprovedSearch(track);
                if (searchResult != null) {
                    return searchResult;
                }
                
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        System.out.println("All attempts failed to find lyrics");
        return null;
    }

    private Text findLyricsDirect(String track) {
        try {
            String[] directUrls = generateDirectUrls(track);
            
            for (String directUrl : directUrls) {
                System.out.println("Trying direct URL: " + directUrl);
                
                try {
                    Document lyricsDoc = Jsoup.connect(directUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(10000)
                            .ignoreHttpErrors(true)
                            .get();
                    
                    if (isValidLyricsPage(lyricsDoc)) {
                        Text lyrics = extractLyrics(lyricsDoc);
                        if (lyrics != null && !lyrics.getText().trim().isEmpty()) {
                            System.out.println("✓ Success via direct URL: " + directUrl);
                            return lyrics;
                        }
                    }
                    
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    System.err.println("Direct URL failed: " + e.getMessage());
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
                .replace("--", "-")
                .replace("(", "")
                .replace(")", "");
        
        urls.add("https://genius.com/" + base + "-lyrics");
        
        String simplified = base.replace("-the-", "-").replace("-a-", "-");
        urls.add("https://genius.com/" + simplified + "-lyrics");
        
        if (track.contains(" - ")) {
            String[] parts = track.split(" - ", 2);
            String artist = parts[0].toLowerCase().replace(" ", "-").replace("'", "");
            String song = parts[1].toLowerCase().replace(" ", "-").replace("'", "");
            urls.add("https://genius.com/" + artist + "-" + song + "-lyrics");
        }
        
        return urls.toArray(new String[0]);
    }

    private boolean isValidLyricsPage(Document doc) {
        if (doc == null) return false;
        
        String title = doc.title().toLowerCase();
        
        if (title.contains("not found") || title.contains("404") || 
            title.contains("bedoes") || title.contains("polish")) {
            return false;
        }
        
        return doc.selectFirst("div[data-lyrics-container=true]") != null ||
            doc.selectFirst("div.lyrics") != null;
    }

    private Text findLyricsViaImprovedSearch(String track) {
        try {
            String encodedTrack = URLEncoder.encode(track, StandardCharsets.UTF_8);
            String searchUrl = "https://genius.com/search?q=" + encodedTrack;
            
            System.out.println("Search URL: " + searchUrl);
            
            Document searchDoc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .ignoreHttpErrors(true)
                    .get();

            if (searchDoc == null) {
                System.out.println("Search page request failed");
                return null;
            }

            Elements songCards = searchDoc.select("a.mini_card");
            System.out.println("Found " + songCards.size() + " song cards");
            
            if (songCards.isEmpty()) {
                System.out.println("No song cards found");
                return null;
            }

            List<SongCandidate> candidates = new ArrayList<>();
            
            for (Element card : songCards) {
                SongCandidate candidate = extractSongInfoFromCard(card, track);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            
            candidates.sort((a, b) -> Integer.compare(b.relevanceScore, a.relevanceScore));
            
            System.out.println("Found " + candidates.size() + " candidate songs");
            
            int maxToTry = Math.min(3, candidates.size());
            for (int i = 0; i < maxToTry; i++) {
                SongCandidate candidate = candidates.get(i);
                System.out.println("Trying candidate " + (i+1) + ": " + candidate.artist + " - " + candidate.title + 
                                " (score: " + candidate.relevanceScore + ")");
                
                try {
                    Thread.sleep(1000);
                    
                    Document lyricsDoc = Jsoup.connect(candidate.url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(15000)
                            .ignoreHttpErrors(true)
                            .get();
                    
                    Text lyrics = extractLyrics(lyricsDoc);
                    if (lyrics != null && !lyrics.getText().trim().isEmpty()) {
                        System.out.println("✓ Success with candidate: " + candidate.url);
                        return lyrics;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process candidate: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Search approach failed: " + e.getMessage());
        }
        return null;
    }

    private SongCandidate extractSongInfoFromCard(Element card, String originalTrack) {
        try {
            String url = card.attr("abs:href");
            
            Element titleElement = card.selectFirst(".mini_card-title");
            Element artistElement = card.selectFirst(".mini_card-subtitle");
            
            if (titleElement == null || artistElement == null) {
                return null;
            }
            
            String cardTitle = titleElement.text().trim();
            String cardArtist = artistElement.text().trim();
            
            int relevanceScore = calculateRelevance(originalTrack, cardArtist, cardTitle);
            
            return new SongCandidate(cardArtist, cardTitle, url, relevanceScore);
            
        } catch (Exception e) {
            System.err.println("Error extracting song info: " + e.getMessage());
            return null;
        }
    }

    private int calculateRelevance(String searchQuery, String foundArtist, String foundTitle) {
        int score = 0;
        
        String searchLower = searchQuery.toLowerCase();
        String artistLower = foundArtist.toLowerCase();
        String titleLower = foundTitle.toLowerCase();
        
        String searchArtist = "";
        String searchTitle = searchQuery;
        
        if (searchQuery.contains(" - ")) {
            String[] parts = searchQuery.split(" - ", 2);
            searchArtist = parts[0].toLowerCase().trim();
            searchTitle = parts[1].toLowerCase().trim();
        }
        
        if (!searchArtist.isEmpty()) {
            if (artistLower.equals(searchArtist)) score += 50;
            else if (artistLower.contains(searchArtist)) score += 30;
            else if (searchArtist.contains(artistLower)) score += 20;
        }
        
        if (titleLower.equals(searchTitle)) score += 50;
        else if (titleLower.contains(searchTitle)) score += 30;
        else if (searchTitle.contains(titleLower)) score += 20;
        
        if (!searchArtist.isEmpty()) {
            String[] artistWords = searchArtist.split("\\s+");
            for (String word : artistWords) {
                if (word.length() > 2 && artistLower.contains(word)) {
                    score += 5;
                }
            }
        }
        
        String[] titleWords = searchTitle.split("\\s+");
        for (String word : titleWords) {
            if (word.length() > 2 && titleLower.contains(word)) {
                score += 5;
            }
        }
        
        if (artistLower.contains("bedoes") || titleLower.contains("bedoes")) score -= 100;
        if (artistLower.contains("polish") || titleLower.contains("polish")) score -= 100;
        if (artistLower.contains("rap") || titleLower.contains("rap")) score -= 50;
        
        return Math.max(0, score);
    }

    private Text extractLyrics(Document lyricsDoc) {
        try {
            Element lyricsContainer = lyricsDoc.selectFirst("div[data-lyrics-container=true]");
            
            if (lyricsContainer != null) {
                lyricsContainer.select("script, style, [class*='ad'], [class*='header']").remove();
                
                String lyricsText = lyricsContainer.text().trim();
                
                if (!lyricsText.isEmpty()) {
                    return new Text(lyricsText);
                }
            }
            
            Element fallbackContainer = lyricsDoc.selectFirst("div.lyrics");
            if (fallbackContainer != null) {
                String lyricsText = fallbackContainer.text().trim();
                if (!lyricsText.isEmpty()) {
                    return new Text(lyricsText);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting lyrics: " + e.getMessage());
        }
        return null;
    }

    private static class SongCandidate {
        String artist;
        String title;
        String url;
        int relevanceScore;
        
        SongCandidate(String artist, String title, String url, int relevanceScore) {
            this.artist = artist;
            this.title = title;
            this.url = url;
            this.relevanceScore = relevanceScore;
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
        
        Scene scene = new Scene(layout, 650, 800); // Увеличили размер окна
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