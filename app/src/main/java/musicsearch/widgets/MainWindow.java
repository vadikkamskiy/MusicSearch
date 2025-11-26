package musicsearch.widgets;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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

        setupScrollListener(scrollPane);

        String scrollBarStyle =
            ".scroll-bar .thumb {" +
            "    -fx-background-color: #3A4050;" +
            "    -fx-background-radius: 4;" +
            "}" +
            ".scroll-bar .thumb:hover {" +
            "    -fx-background-color: #505870;" +
            "}" +
            ".scroll-bar .track {" +
            "    -fx-background-color: transparent;" +
            "}";
        scrollPane.getStylesheets().add(
            "data:text/css," + scrollBarStyle().replaceAll("\\s+", " ")
        );
        scrollPane.setStyle(scrollBarStyle);
        
        this.searchEngine = new SearchEngine(mediaLayout);
        SearchWidget searchWidget = new SearchWidget(searchEngine);
        setupGlobalEventListeners();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(scrollPaneStyle());

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
        
        
        audioPlayer = new AudioPlayer(scene.getWindow());
        
        searchEngine.setPlaybackListener(audioPlayer::playTrack);
        
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
        root.setBottom(audioPlayer);
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