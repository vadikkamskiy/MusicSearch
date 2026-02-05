package musicsearch.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.core.model.Model;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.io.File;

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
import io.github.cdimascio.dotenv.Dotenv;

import musicsearch.models.CurrentTrackListener;
import musicsearch.models.DataUpdateListener;
import musicsearch.models.MediaModel;
import musicsearch.models.MediaType;
import musicsearch.models.PlaybackListener;
import musicsearch.models.impl.AudioModel;
import musicsearch.models.impl.VideoModel;
import musicsearch.service.Events.ArtistSearchEvent;
import musicsearch.service.impl.AudioSearchProvider;
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
    public static String currentQuery = "null";
    private static final Dotenv dotenv = Dotenv.load();

    private final List<MediaSearchProvider> providers = List.of(
            new AudioSearchProvider()
    );
    public static final String lyricsUrl = dotenv.get("LYRICS_SOURCE");
    
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
        results.clear();

        executor.submit(() -> {
            List<MediaModel> all = new ArrayList<>();

            for (MediaSearchProvider provider : providers) {
                all.addAll(provider.search(query));
            }

            Platform.runLater(() -> results.setAll(all));
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
                    goHome(new File(System.getProperty("user.home"), "Music"));
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
    public void goHome(File curentDir) {
        currentQuery = "null";
        results.clear();
        File homeDir = new File(System.getProperty("user.home"), "Music");
        File[] files = homeDir.listFiles((dir, name) -> name.endsWith(".mp3") || name.endsWith(".flac"));
        if (files != null) {
            for (File file : files) {
                try {
                    AudioFile audioFile = AudioFileIO.read(file);
                    Tag tag = audioFile.getTag();
                    String artist = tag != null ? tag.getFirst(FieldKey.ARTIST) : "";
                    String title  = tag != null ? tag.getFirst(FieldKey.TITLE)  : "";
                    if (artist == null || artist.isBlank()) {
                        // попробуем вытащить из имени файла
                        String fileName = file.getName().replaceFirst("\\.[^.]+$", ""); // без расширения
                        if (fileName.contains(" - ")) {
                            artist = fileName.split(" - ", 2)[0].trim();
                            if (title == null || title.isBlank()) {
                                title = fileName.split(" - ", 2)[1].trim();
                            }
                        } else {
                            artist = "Unknown artist";
                            if (title == null || title.isBlank()) {
                                title = fileName;
                            }
                        }
                    }
                    if (title == null || title.isBlank()) {
                        title = "Unknown title";
                    }
                    String duration = String.valueOf(audioFile.getAudioHeader().getTrackLength() / 60) + ":" +
                    String.format("%02d", audioFile.getAudioHeader().getTrackLength() % 60);
                    AudioModel model = new AudioModel(artist, title, duration, file.toURI().toString(), "", true, MediaType.AUDIO);
                    homeModels.add(model);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            results.setAll(LocalFiles);
        }
    }

    public void scanAllFiles() {
        scanAudioFolder();
        scanVideoFolder();
    }

    public void scanAudioFolder() {
        LocalFiles.clear();
        File musicDir = new File(System.getProperty("user.home"), "Music");
        File[] files = musicDir.listFiles((dir, name) -> name.endsWith(".mp3") || name.endsWith(".flac"));
        if (files != null) {
            for (File file : files) {
                try {
                    AudioFile audioFile = AudioFileIO.read(file);
                    Tag tag = audioFile.getTag();
                    String artist = tag.getFirst(FieldKey.ARTIST);
                    String title = tag.getFirst(FieldKey.TITLE);
                    String duration = String.valueOf(audioFile.getAudioHeader().getTrackLength() / 60) + ":" +
                            String.format("%02d", audioFile.getAudioHeader().getTrackLength() % 60);
                    MediaModel model = new AudioModel(artist , title, duration, file.toURI().toString(), "", true, MediaType.AUDIO);
                    LocalFiles.add(model);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            results.setAll(LocalFiles);
        }
    }

    void findLyrics(String track){
        System.out.println("Lyrics :" + track);
        lyricsFinder.searchAndShowLyrics((Stage) mediaLayout.getScene().getWindow(), track);
    }

    // private void searchEventListener() {
    //     EventBus.subscribe(ArtistSearchEvent.class, event -> {
    //         search(event.artist);
    //     });
    //     EventBus.subscribe(LyricSearchEvent.class, event-> {
    //         findLyrics(event.track);
    //     });
    // }

    public void loadMoreResults() {
        executor.submit(() -> {
            List<MediaModel> more = new ArrayList<>();

            for (MediaSearchProvider provider : providers) {
                more.addAll(provider.loadMore());
            }

            if (!more.isEmpty()) {
                Platform.runLater(() -> results.addAll(more));
            }
        });
    }

    public void onTrackDeleted(MediaModel media) {
        goHome(new File(System.getProperty("user.home"), "Music"));
    }

    public void scanVideoFolder() {
        LocalFiles.clear();
        File videoDir = new File(System.getProperty("user.home"), "Videos");
        File[] files = videoDir.listFiles((dir, name) -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi"));
        if (files != null) {
            for (File file : files) {
                try {
                    VideoModel model = new VideoModel(file.getName(), file.toURI().toString(), "", null, true, MediaType.VIDEO);
                    LocalFiles.add(model);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            results.setAll(LocalFiles);
        }
    }

    public List<MediaModel> getResults() {
        return new ArrayList<>(results.get());
    }

    private void handleLyricSearch(LyricSearchEvent event) {
        findLyrics(event.track.toString());
    }
}