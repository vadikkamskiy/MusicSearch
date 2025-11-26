package musicsearch.widgets;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.chart.PieChart.Data;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;
import musicsearch.models.MediaModel;
import musicsearch.models.PlaybackListener;
import musicsearch.models.CurrentTrackListener;
import musicsearch.models.DataUpdateListener;
import musicsearch.service.EventBus;
import musicsearch.service.Events.*;
import musicsearch.service.MP3CoverExtractor;

public class MediaWidget extends VBox implements CurrentTrackListener {
    private static final Map<String, Image> coverCache = new ConcurrentHashMap<>();
    private MediaModel mediaModel;
    private PlaybackListener playbackListener;
    private ImageView imageView;
    private boolean imageLoaded = false;
    private boolean isCurrentTrack = false;
    private boolean isDownloaded;
    private ContextMenu contextMenu;
    private DataUpdateListener dataUpdateListener;
    private static final String NORMAL_STYLE = 
    "-fx-background-color: #2A2F3A; " +
    "-fx-border-color: #3A4050; " +
    "-fx-border-width: 1px; " +
    "-fx-border-radius: 8px; " +
    "-fx-background-radius: 8px; " +
    "-fx-cursor: hand;";

    private static final String CURRENT_TRACK_STYLE = 
    "-fx-background-color: #3E3A57; " +
    "-fx-border-color: #af7affff; " +
    "-fx-border-width: 2px; " +
    "-fx-border-radius: 8px; " +
    "-fx-background-radius: 8px; " +
    "-fx-cursor: hand;";

    private static final String HOVER_STYLE = 
    "-fx-background-color: #323848; " +
    "-fx-border-color: #4A5063; " +
    "-fx-border-width: 1px; " +
    "-fx-border-radius: 8px; " +
    "-fx-background-radius: 8px; " +
    "-fx-cursor: hand;";

    private static final String CONTEXT_MENU_STYLE = 
    "-fx-background-color: #2A2F3A; " +
    "-fx-text-fill: #D6D6E3; " +
    "-fx-font-size: 14px; " +
    "-fx-border-color: #3A4050; " +
    "-fx-border-width: 1px; " +
    "-fx-border-radius: 6px; " +
    "-fx-background-radius: 6px;";

    public MediaWidget(MediaModel mediaModel, PlaybackListener playbackListener,
                          DataUpdateListener dataUpdateListener){
        this.mediaModel = mediaModel;
        this.playbackListener = playbackListener;
        this.isDownloaded = mediaModel.isDownloaded();
        this.dataUpdateListener = dataUpdateListener;
        setupUI();
        setupEvents();
        loadImageLazily();
        this.setStyle(NORMAL_STYLE);
    }

    private void setupUI() {
        this.setPadding(new Insets(10));
        this.setSpacing(5);
        this.setPrefSize(170, 200);
        
        imageView = new ImageView();
        imageView.setFitWidth(150);
        imageView.setFitHeight(150);
        imageView.setStyle("-fx-background-color: #4A4A5F; -fx-background-radius: 6px;");

        setPlaceholderImage();
        
        Label titleLabel = new Label(truncateText(mediaModel.getTitle(), 15));
        Label Artist = new Label(mediaModel.getTitle().split("-")[0].trim());
        Label Song = new Label(mediaModel.getTitle().split("-")[1].trim()); 
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(100);
        
        Label durationLabel = new Label(mediaModel.getTime());

        titleLabel.setStyle("-fx-text-fill: #D6D6E3;");
        Artist.setStyle("-fx-text-fill: #D6D6E3;");
        Song.setStyle("-fx-text-fill: #D6D6E3;");
        durationLabel.setStyle("-fx-text-fill: #9EA3B5; -fx-font-size: 10px;");
        
        this.getChildren().addAll(imageView, Artist, Song, durationLabel);
    }

    private void setPlaceholderImage() {
        try {
            Image placeholder = new Image(getClass().getResourceAsStream("/images/music_placeholder.png"));
            if (placeholder != null) {
                imageView.setImage(placeholder);
            }
        } catch (Exception e) {
            System.err.println("Error loading placeholder image: " + e.getMessage());
        }
    }

    private void setupEvents() {
        this.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && playbackListener != null) {
                playbackListener.onTrackSelected(mediaModel);
            } else if (event.getButton() == MouseButton.SECONDARY) {
                ContextMenu menu = getContextMenu();
                if (menu != null) {
                    menu.show(this, event.getScreenX(), event.getScreenY());
                }
            }
        });
        
        this.setOnMouseEntered(event -> {
            if (!isCurrentTrack) {
                this.setStyle(HOVER_STYLE);
            }
        });
        
        this.setOnMouseExited(event -> {
            updateStyle();
        });
    }
    
    private void updateStyle() {
        if (isCurrentTrack) {
            this.setStyle(CURRENT_TRACK_STYLE);
        } else {
            this.setStyle(NORMAL_STYLE);
        }
    }
    
    @Override
    public void onCurrentTrackChanged(MediaModel currentTrack) {
        Platform.runLater(() -> {
            boolean wasCurrent = isCurrentTrack;
            isCurrentTrack = mediaModel.equals(currentTrack);
            
            if (wasCurrent != isCurrentTrack) {
                updateStyle();
            }
        });
    }

    private void loadImageLazily() {
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !imageLoaded) {
                loadImageAsync();
            }
        });
    }

    private void loadImageAsync() {
        if (mediaModel.isDownloaded() && mediaModel.getUrl() != null) {
            loadLocalCover();
        } else {
            loadRemoteCover();
        }
    }

    private void loadLocalCover() {
    Task<String> coverTask = new Task<String>() {
        @Override
        protected String call() throws Exception {
            String filePath = mediaModel.getUrl();
            
            System.out.println("Original filePath: " + filePath);
            
            if (filePath != null) {
                if (filePath.startsWith("file:\\")) {
                    filePath = filePath.substring(6);
                }
                else if (filePath.startsWith("file:")) {
                    filePath = filePath.substring(5);
                }
                
                try {
                    filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
                } catch (Exception e) {

                }
                
                System.out.println("Normalized filePath: " + filePath);
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("File does not exist: " + filePath);
                return null;
            }
            
            if (!file.canRead()) {
                System.err.println("Cannot read file: " + filePath);
                return null;
            }
            
            return MP3CoverExtractor.extractCoverFromMP3(filePath);
        }
    };

    coverTask.setOnSucceeded(event -> {
        String coverUrl = coverTask.getValue();
        if (coverUrl != null) {
            loadImageFromUrl(coverUrl);
        } else {
            if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) {
                loadRemoteCover();
            } else {
                setPlaceholderImage();
            }
        }
    });

    coverTask.setOnFailed(event -> {
        System.err.println("Failed to extract cover: " + coverTask.getException().getMessage());
        if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) {
            loadRemoteCover();
        } else {
            setPlaceholderImage();
        }
    });

    new Thread(coverTask).start();
}

    private void loadRemoteCover() {
        String imageUrl = mediaModel.getImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        loadImageFromUrl(imageUrl);
    }

    private void loadImageFromUrl(String imageUrl) {
        if (coverCache.containsKey(imageUrl)) {
            Image cachedImage = coverCache.get(imageUrl);
            Platform.runLater(() -> {
                imageView.setImage(cachedImage);
                imageLoaded = true;
            });
            return;
        }
        
        Task<Image> imageTask = new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                try {
                    return new Image(imageUrl, 150, 150, true, true, true);
                } catch (Exception e) {
                    return null;
                }
            }
        };

        imageTask.setOnSucceeded(event -> {
            Image image = imageTask.getValue();
            if (image != null && !image.isError()) {
                coverCache.put(imageUrl, image);
                Platform.runLater(() -> {
                    imageView.setImage(image);
                    imageLoaded = true;
                });
            }
        });

        new Thread(imageTask).start();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    public static void clearCoverCache() {
        coverCache.clear();
        MP3CoverExtractor.cleanupOldCoverFiles();
    }

    private ContextMenu getContextMenu() {
        contextMenu = new ContextMenu();
        contextMenu.setStyle(CONTEXT_MENU_STYLE);
        
        MenuItem playItem = new MenuItem("Play");
        playItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
        
        playItem.setOnAction(e -> {
            if (playbackListener != null) {
                playbackListener.onTrackSelected(mediaModel);
            }
        });

        if (isDownloaded) {
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            
            MenuItem findArtist = new MenuItem("Find Artist");
            findArtist.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            
            contextMenu.getItems().addAll(playItem, deleteItem, findArtist);
            
            deleteItem.setOnAction(e -> {
                String filePath = mediaModel.getUrl();
                if (filePath != null) {
                    if (filePath.startsWith("file:\\")) {
                        filePath = filePath.substring(6);
                    }
                    else if (filePath.startsWith("file:")) {
                        filePath = filePath.substring(5);
                    }
                    
                    try {
                        filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
                    } catch (Exception ex) {

                    }
                    
                    File file = new File(filePath);
                    if (file.delete()) {
                        System.out.println("Deleted file: " + file.getAbsolutePath());
                        isDownloaded = false;
                        mediaModel.setDownloaded(false);
                        
                        if (dataUpdateListener != null) {
                            dataUpdateListener.onDataChanged();
                        }
                    }
                }
            });
            
            findArtist.setOnAction(e -> {
                String artist = mediaModel.getTitle().split("-")[0].trim();
                EventBus.publish(new ArtistSearchEvent(artist));
            });
        } else {
            MenuItem downloadItem = new MenuItem("Download");
            downloadItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            
            MenuItem findArtist = new MenuItem("Find Artist");
            findArtist.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            
            contextMenu.getItems().addAll(playItem, downloadItem, findArtist);
            
            downloadItem.setOnAction(e -> {
                EventBus.publish(new TrackDownloadEvent(mediaModel));
            });
            
            findArtist.setOnAction(e -> {
                String artist = mediaModel.getTitle().split("-")[0].trim();
                System.out.println("DEBUG: Publishing ArtistSearchEvent for: " + artist);
                EventBus.publish(new ArtistSearchEvent(artist));
            });
        }
        
        return contextMenu;
    }
}