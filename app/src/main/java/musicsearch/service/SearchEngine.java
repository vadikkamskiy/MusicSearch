package musicsearch.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.core.model.Model;

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
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
    
    public SearchEngine(GridPane mediaLayout) {
        this.mediaLayout = mediaLayout;
        results.addListener((Observable obs) -> updateMediaLayout());
        searchEventListener();
    }

    public SearchEngine(GridPane mediaLayout, PlaybackListener playbackListener) {
        this.mediaLayout = mediaLayout;
        this.playbackListener = playbackListener;
        results.addListener((Observable obs) -> updateMediaLayout());
        searchEventListener();
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
            for (File file : files) {
                try {
                    AudioFile audioFile = AudioFileIO.read(file);
                    Tag tag = audioFile.getTag();
                    String artist = tag.getFirst(FieldKey.ARTIST);
                    String title = tag.getFirst(FieldKey.TITLE);
                    String duration = String.valueOf(audioFile.getAudioHeader().getTrackLength() / 60) + ":" +
                            String.format("%02d", audioFile.getAudioHeader().getTrackLength() % 60);
                    MediaModel model = new MediaModel(artist + " - " + title, duration, file.toURI().toString(), "", true);
                    LocalFiles.add(model);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            results.setAll(LocalFiles);
        }
    }

    public void loadMoreResults() {
        if(searchPage<allPage && !currentQuery.equals("null")){
            executor.submit(() -> {
                try {
                    String searchUrl = "https://" +
                        dotenv.get("URL_SOURCE") +
                        "/search/start/" + 48 * searchPage + "?q=" + currentQuery;
                    System.out.println(searchUrl);
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

    void findLyrics(String track){
        System.out.println("Lyrics :" + track);
        lyricsFinder.searchAndShowLyrics((Stage) mediaLayout.getScene().getWindow(), track);
    }

    private void searchEventListener() {
        EventBus.subscribe(ArtistSearchEvent.class, event -> {
            search(event.artist);
        });
        EventBus.subscribe(LyricSearchEvent.class, event-> {
            findLyrics(event.track);
        });
    }



    public List<MediaModel> getResults() {
        return new ArrayList<>(results.get());
    }
}