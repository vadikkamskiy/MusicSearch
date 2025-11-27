package musicsearch.widgets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
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

    // playlist support
    // parentPlaylist может быть null — тогда считаем, что виджет одиночный
    public List<MediaModel> parentPlaylist = new ArrayList<>();
    private int thisIndex = -1;


    public MediaWidget(MediaModel mediaModel, PlaybackListener playbackListener,
                          DataUpdateListener dataUpdateListener){
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
            if (currentTrack == null || currentTrack.getUrl() == null) {
                isCurrentTrack = false;
            } else {
                isCurrentTrack = mediaModel.getUrl() != null && mediaModel.getUrl().equals(currentTrack.getUrl());
            }

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

                if (filePath != null) {
                    if (filePath.startsWith("file:\\")) {
                        filePath = filePath.substring(6);
                    } else if (filePath.startsWith("file:")) {
                        filePath = filePath.substring(5);
                    }

                    try {
                        filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
                    } catch (Exception e) {
                        // ignore
                    }
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
            setPlaceholderImage();
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

        imageTask.setOnFailed(event -> {
            setPlaceholderImage();
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
                if (parentPlaylist != null && !parentPlaylist.isEmpty() && thisIndex >= 0) {
                    playbackListener.onPlayPlaylist(parentPlaylist, thisIndex);
                } else {
                    playbackListener.onTrackSelected(mediaModel);
                }
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
                    } else if (filePath.startsWith("file:")) {
                        filePath = filePath.substring(5);
                    }

                    try {
                        filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
                    } catch (Exception ex) {
                        // ignore
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
            });
        }

        return contextMenu;
    }

    List<String> checkArtist(String artist){
        List<String> artists = new ArrayList<>();

        if (artist == null) return artists;

        // Паттерны для разных случаев
        String[] patterns = {
            "(.*?)\\s+(?:feat\\.?|ft\\.?)\\s+(.+)",           // Artist feat. Artist2
            "(.*?)\\s+&\\s+(.+)",                            // Artist & Artist2
            "(.*?)\\s*,\\s*(.+)",                            // Artist, Artist2
            "(.*?)\\s+(?:with|w/)\\s+(.+)",                  // Artist with Artist2
            "(.*?)\\s+x\\s+(.+)",                            // Artist x Artist2
            "(.*?)\\s+vs\\.?\\s+(.+)",                       // Artist vs Artist2
            "(.*?)\\s+featuring\\s+(.+)"                     // Artist featuring Artist2
        };

        boolean matched = false;
        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
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

        // Если не нашли разделителей, возвращаем весь заголовок как одного артиста
        if (!matched && !artist.trim().isEmpty()) {
            artists.add(cleanArtistName(artist));
        }

        return artists;
    }

    private String cleanArtistName(String name) {
        if (name == null) return "";

        // Удаляем скобки и их содержимое
        name = name.replaceAll("\\([^)]*\\)", "").trim();

        // Удаляем квадратные скобки и их содержимое  
        name = name.replaceAll("\\[[^]]*\\]", "").trim();

        // Удаляем лишние пробелы и запятые в начале/конце
        name = name.replaceAll("^[,\\s]+|[,\\s]+$", "");

        return name;
    }

    private void showCustomArtistDialog(List<String> artists) {
        if (artists.isEmpty()) {
            return;
        }

        // Создаем диалог
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Select Artist");
        dialog.setHeaderText("Multiple artists found in this track");

        // Устанавливаем иконку (опционально)
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/music_icon.png")));
        } catch (Exception e) {
            // Иконка не обязательна
        }

        // Создаем кнопки
        ButtonType searchButton = new ButtonType("Search", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(searchButton, cancelButton);

        // === СОЗДАЕМ ОСНОВНОЙ КОНТЕЙНЕР ===
        VBox dialogContent = new VBox();
        dialogContent.setSpacing(15);
        dialogContent.setPadding(new Insets(10));
        dialogContent.setStyle("-fx-background-color: #2A2F3A;");

        // Заголовок
        Label headerLabel = new Label("Choose which artist to search:");
        headerLabel.setStyle("-fx-text-fill: #D6D6E3; -fx-font-size: 14px; -fx-font-weight: bold;");

        // === LISTVIEW ДЛЯ АРТИСТОВ ===
        ListView<String> artistListView = new ListView<>();
        artistListView.getItems().addAll(artists);
        artistListView.getSelectionModel().selectFirst(); // Выбираем первого по умолчанию
        artistListView.setPrefHeight(200); // Фиксированная высота

        // Кастомные стили для ListView
        artistListView.setStyle(
            "-fx-background-color: #1E2330; " +
            "-fx-border-color: #3A4050; " +
            "-fx-border-width: 1px; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px;"
        );

        // Кастомные ячейки для лучшего отображения
        artistListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    setStyle(
                        "-fx-text-fill: #D6D6E3; " +
                        "-fx-font-size: 13px; " +
                        "-fx-padding: 8px; " +
                        "-fx-background-color: transparent;"
                    );

                    // Подсветка при выборе
                    if (isSelected()) {
                        setStyle(
                            "-fx-text-fill: #FFFFFF; " +
                            "-fx-font-size: 13px; " +
                            "-fx-padding: 8px; " +
                            "-fx-background-color: #4A5063; " +
                            "-fx-background-radius: 3px;"
                        );
                    }
                }
            }
        });

        // === ДОПОЛНИТЕЛЬНАЯ ИНФОРМАЦИЯ ===
        Label infoLabel = new Label(artists.size() + " artists found in: \"" +
            truncateText(mediaModel.getTitle(), 40) + "\"");
        infoLabel.setStyle("-fx-text-fill: #9EA3B5; -fx-font-size: 12px;");
        infoLabel.setWrapText(true);

        // === ДВОЙНОЙ КЛИК ДЛЯ БЫСТРОГО ВЫБОРА ===
        artistListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = artistListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.setResult(selected);
                    dialog.close();
                }
            }
        });

        // === СОБИРАЕМ ВСЕ ВМЕСТЕ ===
        dialogContent.getChildren().addAll(headerLabel, infoLabel, artistListView);
        dialog.getDialogPane().setContent(dialogContent);

        // === СТИЛИЗАЦИЯ ДИАЛОГА ===
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle(
            "-fx-background-color: #2A2F3A; " +
            "-fx-border-color: #3A4050; " +
            "-fx-border-width: 1px; " +
            "-fx-border-radius: 8px;"
        );

        // Стилизация кнопок
        Button searchBtn = (Button) dialogPane.lookupButton(searchButton);
        Button cancelBtn = (Button) dialogPane.lookupButton(cancelButton);

        if (searchBtn != null) {
            searchBtn.setStyle(
                "-fx-background-color: #6B5B95; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 5px; " +
                "-fx-padding: 8px 16px;"
            );
        }

        if (cancelBtn != null) {
            cancelBtn.setStyle(
                "-fx-background-color: #3A4050; " +
                "-fx-text-fill: #D6D6E3; " +
                "-fx-background-radius: 5px; " +
                "-fx-padding: 8px 16px;"
            );
        }

        // === ОБРАБОТКА РЕЗУЛЬТАТА ===
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == searchButton) {
                return artistListView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        // Показываем диалог и обрабатываем результат
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selectedArtist -> {
            System.out.println("Selected artist for search: " + selectedArtist);
            EventBus.publish(new ArtistSearchEvent(selectedArtist));
        });
    }

    public MediaModel getModel() {
        return mediaModel;
    }
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
    "-fx-background-color: #323848; " +
    "-fx-border-color: #4A5063; " +
    "-fx-border-width: 1px; " +
    "-fx-border-radius: 8px; " +
    "-fx-background-radius: 8px; " +
    "-fx-cursor: hand;";
}
