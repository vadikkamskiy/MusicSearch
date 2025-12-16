package musicsearch.widgets;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import musicsearch.service.EventBus;
import musicsearch.service.SearchEngine;
import musicsearch.service.Events.ArtistSearchEvent;
import musicsearch.service.Events.TrackDownloadEvent;
import musicsearch.service.FileEngine;
import musicsearch.models.CurrentTrackListener;
import musicsearch.models.MediaModel;
import musicsearch.models.PlaybackListener;

public class MainWindow {
    private Scene scene;
    private BorderPane root;
    private AudioPlayer audioPlayer;
    private SearchEngine searchEngine;
    private static FileEngine fileEngine = new FileEngine();

    public MainWindow() {
        root = new BorderPane();

        GridPane mediaLayout = new GridPane();
        mediaLayout.setHgap(15);
        mediaLayout.setVgap(15);
        mediaLayout.setPadding(new Insets(30));

        ScrollPane scrollPane = new ScrollPane(mediaLayout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(scrollBarStyle());
        setupScrollListener(scrollPane);

        scrollPane.getStylesheets().add(
            "data:text/css," + scrollBarStyle().replaceAll("\\s+", " ")
        );
        scrollPane.setStyle(scrollPaneStyle());

        this.searchEngine = new SearchEngine(mediaLayout);
        this.audioPlayer = new AudioPlayer(null);

        PlaybackListener playbackListener = new PlaybackListener() {
            @Override
            public void onTrackSelected(MediaModel model) {
                List<MediaModel> list = searchEngine.getResults();
                if (list == null || list.isEmpty()) return;
                int idx = findIndexByUrl(list, model);
                if (idx < 0) idx = 0;
                audioPlayer.setPlaylistAndPlay(list, idx);
            }

            @Override
            public void onPlayPlaylist(List<MediaModel> playlist, int startIndex) {
                if (playlist == null || playlist.isEmpty()) return;
                int idx = (startIndex < 0 || startIndex >= playlist.size()) ? 0 : startIndex;
                audioPlayer.setPlaylistAndPlay(playlist, idx);
            }

            private int findIndexByUrl(List<MediaModel> list, MediaModel model) {
                if (model == null || list == null) return -1;
                for (int i = 0; i < list.size(); i++) {
                    MediaModel m = list.get(i);
                    if (m != null && m.getUrl() != null && m.getUrl().equals(model.getUrl())) {
                        return i;
                    }
                }
                return -1;
            }
        };

        this.searchEngine.setPlaybackListener(playbackListener);

        SearchWidget searchWidget = new SearchWidget(searchEngine);
        audioPlayer.setSearchWidget(searchWidget);

        setupGlobalEventListeners();
        scrollPane.setFitToWidth(true);

        scrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            Node viewport = scrollPane.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle(backgroundStyle());
            }
        });

        mediaLayout.setStyle(backgroundStyle());
        mediaLayout.setAlignment(Pos.CENTER);

        root.setTop(searchWidget.getWidget());
        root.setCenter(scrollPane);

        scene = new Scene(root, 1005, 600);
        root.setBottom(audioPlayer);

        CurrentTrackListener widgetTracker = new CurrentTrackListener() {
            @Override
            public void onCurrentTrackChanged(MediaModel currentTrack) {
                Platform.runLater(() -> {
                    for (Node node : mediaLayout.getChildren()) {
                        if (node instanceof MediaWidget) {
                            MediaWidget widget = (MediaWidget) node;
                            widget.onCurrentTrackChanged(currentTrack);
                        }
                    }
                });
            }
        };

        audioPlayer.addCurrentTrackListener(widgetTracker);

        searchEngine.goHome();
    }

    public void shutdown() {
        if (this.searchEngine != null) {
            searchEngine.shutdown();
        }
    }

    public Scene getScene() {
        return scene;
    }

    private void setupScrollListener(ScrollPane scrollPane) {
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == scrollPane.getVmax()) {
                searchEngine.loadMoreResults();
            }
        });
    }

    private void setupGlobalEventListeners() {
        EventBus.subscribe(ArtistSearchEvent.class, event -> {
            searchEngine.search(event.artist);
        });

        EventBus.subscribe(TrackDownloadEvent.class, event -> {
            fileEngine.downloadMedia(event.track);
        });
    }

    private final String scrollBarStyle (){
        return ".scroll-bar .thumb {" +
            "    -fx-background-color: #3A4050;" +
            "    -fx-background-radius: 4;" +
            "}" +
            ".scroll-bar .thumb:hover {" +
            "    -fx-background-color: #505870;" +
            "}" +
            ".scroll-bar .track {" +
            "    -fx-background-color: transparent;" +
            "}";
    }

    private final String scrollPaneStyle() {
        return "-fx-background: #1E2330;" +
            "-fx-background-color: #1E2330;";
    }
    private final String backgroundStyle() {
        return "-fx-background-color: #1E2330;";
    }
}
