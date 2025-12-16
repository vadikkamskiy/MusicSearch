package musicsearch.widgets;

import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import musicsearch.models.DataUpdateListener;
import musicsearch.models.MediaModel;
import musicsearch.models.PlaybackListener;
import musicsearch.models.CurrentTrackListener;
import musicsearch.service.EventBus;
import musicsearch.service.Events.*;
import musicsearch.service.MP3CoverExtractor;

public class MediaWidget extends VBox implements CurrentTrackListener {
    private static final Map<String, Image> coverCache = new ConcurrentHashMap<>();
    private static final ExecutorService IMAGE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("media-image-loader-" + t.getId());
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((th, ex) -> {
            System.err.println("Image loader error in " + th.getName());
            ex.printStackTrace();
        });
        return t;
    });

    private final MediaModel mediaModel;
    private final PlaybackListener playbackListener;
    private final ImageView imageView;
    private boolean imageLoaded = false;
    private boolean isCurrentTrack = false;
    private boolean isDownloaded;
    private ContextMenu contextMenu;
    private final DataUpdateListener dataUpdateListener;

    public List<MediaModel> parentPlaylist = new ArrayList<>();
    private int thisIndex = -1;

    public MediaWidget(MediaModel mediaModel, PlaybackListener playbackListener,
                       DataUpdateListener dataUpdateListener) {
        this(mediaModel, playbackListener, dataUpdateListener, null, -1);
    }

    public MediaWidget(MediaModel mediaModel,
                       PlaybackListener playbackListener,
                       DataUpdateListener dataUpdateListener,
                       List<MediaModel> parentPlaylist,
                       int index) {
        this.mediaModel = mediaModel;
        this.playbackListener = playbackListener;
        this.isDownloaded = mediaModel.isDownloaded();
        this.dataUpdateListener = dataUpdateListener;
        if (parentPlaylist != null) {
            this.parentPlaylist = parentPlaylist;
            this.thisIndex = index;
        } else {
            this.parentPlaylist = new ArrayList<>();
            this.thisIndex = -1;
        }
        this.imageView = new ImageView();
        setupUI();
        setupEvents();
        loadImageLazily();
        this.setStyle(NORMAL_STYLE);
    }

    private void setupUI() {
        this.setPadding(new Insets(10));
        this.setSpacing(5);
        this.setPrefSize(170, 200);

        imageView.setFitWidth(150);
        imageView.setFitHeight(150);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setStyle("-fx-background-color: #4A4A5F; -fx-background-radius: 6px;");

        setPlaceholderImage();

        String artistText = "";
        String songText = "";
        if (mediaModel.getTitle() != null && mediaModel.getTitle().contains("-")) {
            String[] parts = mediaModel.getTitle().split("-", 2);
            artistText = parts[0].trim();
            songText = parts[1].trim();
        } else {
            artistText = mediaModel.getTitle() != null ? mediaModel.getTitle() : "";
            songText = "";
        }

        Label Artist = new Label(truncateText(artistText, 30));
        Label Song = new Label(truncateText(songText, 30));
        Artist.setStyle("-fx-text-fill: #D6D6E3;");
        Song.setStyle("-fx-text-fill: #D6D6E3;");

        Label durationLabel = new Label(mediaModel.getTime());
        durationLabel.setStyle("-fx-text-fill: #9EA3B5; -fx-font-size: 10px;");

        this.getChildren().addAll(imageView, Artist, Song, durationLabel);
    }

    private void setPlaceholderImage() {
        Image placeholder = null;
        try {
            InputStream is = getClass().getResourceAsStream("/images/music_placeholder.png");
            if (is == null) {
                is = getClass().getResourceAsStream("images/music_placeholder.png");
            }
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream("images/music_placeholder.png");
            }
            if (is != null) {
                placeholder = new Image(is);
            }
        } catch (Exception e) {
            System.err.println("Error loading placeholder image (exception): " + e.getMessage());
        }
        if (placeholder == null) {
            placeholder = new WritableImage(1, 1);
        }
        imageView.setImage(placeholder);
    }

    // вставь вместо старого метода loadLocalCover()
    private void loadLocalCover() {
        IMAGE_EXECUTOR.submit(() -> {
            try {
                String raw = mediaModel.getUrl();
                if (raw == null || raw.isEmpty()) {
                    Platform.runLater(() -> {
                        if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) {
                            loadRemoteCover();
                        } else {
                            setPlaceholderImage();
                        }
                    });
                    return;
                }

                // Нормализуем URI/путь к файлу
                String fsPath;
                try {
                    // если это URI (file:/...), используем URI -> Path
                    java.net.URI uri = new java.net.URI(raw);
                    if ("file".equalsIgnoreCase(uri.getScheme())) {
                        fsPath = java.nio.file.Paths.get(uri).toString();
                    } else {
                        // не file-схема — возможно уже обычный путь
                        fsPath = raw;
                    }
                } catch (Exception ex) {
                    // fallback: обрезаем file: префиксы вручную и decode
                    fsPath = raw;
                    if (fsPath.startsWith("file:\\\\")) fsPath = fsPath.substring(6);
                    else if (fsPath.startsWith("file:\\")) fsPath = fsPath.substring(6);
                    else if (fsPath.startsWith("file:/")) fsPath = fsPath.substring(5);
                    else if (fsPath.startsWith("file:")) fsPath = fsPath.substring(5);
                    try {
                        fsPath = java.net.URLDecoder.decode(fsPath, "UTF-8");
                    } catch (Exception e2) { /* ignore */ }
                }

                System.err.println("DEBUG: loadLocalCover -> filePath raw='" + raw + "' fsPath='" + fsPath + "'");

                File file = new File(fsPath);
                if (!file.exists() || !file.canRead()) {
                    System.err.println("DEBUG: local file missing or unreadable: " + fsPath);
                    Platform.runLater(() -> {
                        if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) loadRemoteCover();
                        else setPlaceholderImage();
                    });
                    return;
                }

                // вытащим обложку (MP3CoverExtractor возвращает file://... URI)
                String coverUri = MP3CoverExtractor.extractCoverFromMP3(fsPath);
                System.err.println("DEBUG: MP3CoverExtractor returned: " + coverUri);

                if (coverUri != null && !coverUri.isEmpty()) {
                    // проверим файл-обложку
                    try {
                        java.net.URI curi = new java.net.URI(coverUri);
                        File coverFile = java.nio.file.Paths.get(curi).toFile();
                        if (coverFile.exists() && coverFile.canRead()) {
                            final Image img = new Image(coverFile.toURI().toString(), 150, 150, true, true);
                            Platform.runLater(() -> {
                                if (!img.isError()) {
                                    coverCache.put(coverFile.toURI().toString(), img);
                                    imageView.setImage(img);
                                    imageLoaded = true;
                                } else {
                                    System.err.println("DEBUG: image reported error after loading from coverFile");
                                    if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) loadRemoteCover();
                                    else setPlaceholderImage();
                                }
                            });
                            return;
                        } else {
                            System.err.println("DEBUG: coverFile not found/readable: " + coverUri);
                        }
                    } catch (Exception e) {
                        System.err.println("DEBUG: couldn't use coverUri as file: " + e.getMessage());
                    }
                }

                // fallback: если mediaModel содержит внешнюю imageUrl — пробуем её
                if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) {
                    Platform.runLater(this::loadRemoteCover);
                } else {
                    Platform.runLater(this::setPlaceholderImage);
                }

            } catch (Throwable t) {
                System.err.println("Error in loadLocalCover: " + t.getMessage());
                t.printStackTrace();
                Platform.runLater(() -> {
                    if (mediaModel.getImageUrl() != null && !mediaModel.getImageUrl().isEmpty()) loadRemoteCover();
                    else setPlaceholderImage();
                });
            }
        });
    }


    private Optional<Image> loadResourceImage(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
            }
            if (is == null) return Optional.empty();
            Image img = new Image(is);
            return Optional.of(img);
        } catch (Exception e) {
            System.err.println("Error loading resource image " + resourcePath + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void setupEvents() {
        this.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && playbackListener != null) {
                if (parentPlaylist != null && !parentPlaylist.isEmpty() && thisIndex >= 0) {
                    playbackListener.onPlayPlaylist(parentPlaylist, thisIndex);
                } else {
                    playbackListener.onTrackSelected(mediaModel);
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                ContextMenu menu = getContextMenu();
                if (menu != null) {
                    menu.show(this, event.getScreenX(), event.getScreenY());
                }
            }
        });

        this.setOnMouseEntered(event -> {
            if (!isCurrentTrack) this.setStyle(HOVER_STYLE);
        });

        this.setOnMouseExited(event -> updateStyle());
    }

    private void updateStyle() {
        if (isCurrentTrack) this.setStyle(CURRENT_TRACK_STYLE);
        else this.setStyle(NORMAL_STYLE);
    }

    @Override
    public void onCurrentTrackChanged(MediaModel currentTrack) {
        Platform.runLater(() -> {
            boolean wasCurrent = isCurrentTrack;
            if (currentTrack == null || currentTrack.getUrl() == null) {
                isCurrentTrack = false;
            } else {
                isCurrentTrack = mediaModel.getUrl() != null && mediaModel.getUrl().equals(currentTrack.getUrl());
            }
            if (wasCurrent != isCurrentTrack) updateStyle();
        });
    }

    private void loadImageLazily() {
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !imageLoaded) {
                IMAGE_EXECUTOR.submit(this::loadImageAsync);
            }
        });
    }

    private void loadImageAsync() {
        if (isDownloaded && mediaModel.getUrl() != null && !mediaModel.getUrl().isEmpty()) {
            loadLocalCover();
        } else {
            loadRemoteCover();
        }
    }

    private void loadRemoteCover() {
        String imageUrl = mediaModel.getImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            Platform.runLater(this::setPlaceholderImage);
            return;
        }

        Image cached = coverCache.get(imageUrl);
        if (cached != null) {
            Platform.runLater(() -> {
                imageView.setImage(cached);
                imageLoaded = true;
            });
            return;
        }

        Platform.runLater(() -> {
            try {
                Image img = new Image(imageUrl, 150, 150, true, true, true);
                img.errorProperty().addListener((obs, oldV, newV) -> {
                    if (newV) {
                        System.err.println("Remote image load error for: " + imageUrl + " - " + Optional.ofNullable(img.getException()).map(Throwable::getMessage).orElse("unknown"));
                        Platform.runLater(this::setPlaceholderImage);
                    }
                });
                img.progressProperty().addListener((pObs, oldP, newP) -> {
                    if (newP != null && newP.doubleValue() >= 1.0) {
                        if (!img.isError()) {
                            coverCache.put(imageUrl, img);
                            imageView.setImage(img);
                            imageLoaded = true;
                        }
                    }
                });
                if (!img.isError() && img.getProgress() >= 1.0) {
                    coverCache.put(imageUrl, img);
                    imageView.setImage(img);
                    imageLoaded = true;
                } else {
                    if (imageView.getImage() == null || imageView.getImage().getWidth() <= 1) {
                        setPlaceholderImage();
                    }
                }
            } catch (Exception ex) {
                System.err.println("Exception creating Image for url: " + imageUrl + " : " + ex.getMessage());
                ex.printStackTrace();
                setPlaceholderImage();
            }
        });
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
        if (contextMenu != null) return contextMenu;
        contextMenu = new ContextMenu();
        contextMenu.setStyle(CONTEXT_MENU_STYLE);

        MenuItem playItem = new MenuItem("Play");
        playItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
        playItem.setOnAction(e -> {
            if (playbackListener != null) {
                if (parentPlaylist != null && !parentPlaylist.isEmpty() && thisIndex >= 0) {
                    playbackListener.onPlayPlaylist(parentPlaylist, thisIndex);
                } else {
                    playbackListener.onTrackSelected(mediaModel);
                }
            }
        });

        contextMenu.getItems().add(playItem);

        if (isDownloaded) {
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            MenuItem findArtist = new MenuItem("Find Artist");
            findArtist.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");

            contextMenu.getItems().addAll(deleteItem, findArtist);

            deleteItem.setOnAction(e -> {
                String filePath = mediaModel.getUrl();
                if (filePath != null) {
                    if (filePath.startsWith("file:\\")) filePath = filePath.substring(6);
                    else if (filePath.startsWith("file:")) filePath = filePath.substring(5);
                    try {
                        filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
                    } catch (Exception ex) { /* ignore */ }
                    File file = new File(filePath);
                    if (file.delete()) {
                        System.out.println("Deleted file: " + file.getAbsolutePath());
                        isDownloaded = false;
                        mediaModel.setDownloaded(false);
                        if (dataUpdateListener != null) dataUpdateListener.onDataChanged();
                    }
                }
            });

            findArtist.setOnAction(e -> handleFindArtist());
            MenuItem findLyricsItem = new MenuItem("Find lyrics");
            findLyricsItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");

            contextMenu.getItems().add(findLyricsItem);
            findLyricsItem.setOnAction(e -> EventBus.publish(new LyricSearchEvent(mediaModel.getTitle())));
        } else {
            MenuItem downloadItem = new MenuItem("Download");
            downloadItem.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            MenuItem findArtist = new MenuItem("Find Artist");
            findArtist.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");
            MenuItem findLyrics = new MenuItem("Find lyrics");
            findLyrics.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px;");


            contextMenu.getItems().addAll(downloadItem, findArtist,findLyrics);

            findLyrics.setOnAction(e -> EventBus.publish(new LyricSearchEvent(mediaModel.getTitle())));
            downloadItem.setOnAction(e -> EventBus.publish(new TrackDownloadEvent(mediaModel)));
            findArtist.setOnAction(e -> handleFindArtist());
        }

        return contextMenu;
    }

    private void handleFindArtist() {
        String artist = "";
        if (mediaModel.getTitle() != null && mediaModel.getTitle().contains("-")) {
            artist = mediaModel.getTitle().split("-", 2)[0].trim();
        } else {
            artist = mediaModel.getTitle();
        }
        List<String> artists = checkArtist(artist);
        if (artists.size() == 1) {
            EventBus.publish(new ArtistSearchEvent(artists.get(0)));
        } else {
            showCustomArtistDialog(artists);
        }
    }

    List<String> checkArtist(String artist) {
        List<String> artists = new ArrayList<>();
        if (artist == null) return artists;
        String[] patterns = {
                "(.*?)\\s+(?:feat\\.?|ft\\.?)\\s+(.+)",
                "(.*?)\\s+&\\s+(.+)",
                "(.*?)\\s*,\\s*(.+)",
                "(.*?)\\s+(?:with|w/)\\s+(.+)",
                "(.*?)\\s+x\\s+(.+)",
                "(.*?)\\s+vs\\.?\\s+(.+)",
                "(.*?)\\s+featuring\\s+(.+)"
        };
        boolean matched = false;
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(artist);
            if (m.matches()) {
                String first = cleanArtistName(m.group(1));
                String second = cleanArtistName(m.group(2));
                if (!first.isEmpty()) artists.add(first);
                if (!second.isEmpty()) artists.addAll(checkArtist(second));
                matched = true;
                break;
            }
        }
        if (!matched && !artist.trim().isEmpty()) artists.add(cleanArtistName(artist));
        return artists;
    }

    private String cleanArtistName(String name) {
        if (name == null) return "";
        name = name.replaceAll("\\([^)]*\\)", "").trim();
        name = name.replaceAll("\\[[^]]*\\]", "").trim();
        name = name.replaceAll("^[,\\s]+|[,\\s]+$", "");
        return name;
    }

    private void showCustomArtistDialog(List<String> artists) {
        if (artists.isEmpty()) return;
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Select Artist");
        dialog.setHeaderText("Multiple artists found in this track");
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        try {
            Optional<Image> ic = loadResourceImage("/images/music_icon.png");
            ic.ifPresent(i -> stage.getIcons().add(i));
        } catch (Exception ignored) {}
        ButtonType searchButton = new ButtonType("Search", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButton, cancelButton);

        VBox dialogContent = new VBox();
        dialogContent.setSpacing(15);
        dialogContent.setPadding(new Insets(10));
        dialogContent.setStyle("-fx-background-color: #2A2F3A;");

        Label headerLabel = new Label("Choose which artist to search:");
        headerLabel.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px; -fx-font-weight: bold;");

        ListView<String> artistListView = new ListView<>();
        artistListView.getItems().addAll(artists);
        artistListView.getSelectionModel().selectFirst();
        artistListView.setPrefHeight(200);

        artistListView.setStyle(
                "-fx-background-color: #1E2330; " +
                        "-fx-border-color: #3A4050; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 5px; " +
                        "-fx-background-radius: 5px;"
        );

        artistListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 13px; -fx-padding: 8px; -fx-background-color: transparent;");
                    if (isSelected()) {
                        setStyle("-fx-text-fill: #FFFFFF; -fx-font-size: 13px; -fx-padding: 8px; -fx-background-color: #4A5063; -fx-background-radius: 3px;");
                    }
                }
            }
        });

        Label infoLabel = new Label(artists.size() + " artists found in: \"" + truncateText(mediaModel.getTitle(), 40) + "\"");
        infoLabel.setStyle("-fx-text-fill: #9EA3B5; -fx-font-size: 12px;");
        infoLabel.setWrapText(true);

        artistListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = artistListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.setResult(selected);
                    dialog.close();
                }
            }
        });

        dialogContent.getChildren().addAll(headerLabel, infoLabel, artistListView);
        dialog.getDialogPane().setContent(dialogContent);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #2A2F3A; -fx-border-color: #3A4050; -fx-border-width: 1px; -fx-border-radius: 8px;");

        Button searchBtn = (Button) dialogPane.lookupButton(searchButton);
        Button cancelBtn = (Button) dialogPane.lookupButton(cancelButton);

        if (searchBtn != null) searchBtn.setStyle("-fx-background-color: #6B5B95; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-padding: 8px 16px;");
        if (cancelBtn != null) cancelBtn.setStyle("-fx-background-color: #3A4050; -fx-text-fill: #D6D6E3; -fx-background-radius: 5px; -fx-padding: 8px 16px;");

        dialog.setResultConverter(dialogButton -> dialogButton == searchButton ? artistListView.getSelectionModel().getSelectedItem() : null);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selectedArtist -> EventBus.publish(new ArtistSearchEvent(selectedArtist)));
    }

    public MediaModel getModel() {
        return mediaModel;
    }

    private static final String NORMAL_STYLE =
            "-fx-background-color: #2A2F3A; -fx-border-color: #3A4050; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;";

    private static final String CURRENT_TRACK_STYLE =
            "-fx-background-color: #3E3A57; -fx-border-color: #af7affff; -fx-border-width: 2px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;";

    private static final String HOVER_STYLE =
            "-fx-background-color: #323848; -fx-border-color: #4A5063; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;";

    private static final String CONTEXT_MENU_STYLE =
            "-fx-background-color: #323848; -fx-border-color: #4A5063; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-cursor: hand;";
}