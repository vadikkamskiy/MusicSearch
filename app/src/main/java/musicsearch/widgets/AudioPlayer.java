package musicsearch.widgets;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Window;
import javafx.util.Duration;
import musicsearch.models.CurrentTrackListener;
import musicsearch.models.MediaModel;
import musicsearch.service.FileEngine;

public class AudioPlayer extends HBox {
    private final List<CurrentTrackListener> currentTrackListeners = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private MediaModel currentModel;
    private boolean isPlaying = false;
    private boolean isSeeking = false;
    private Label currentTrack;
    private Slider progressSlider;
    private Label timeLabel;
    private Button playButton, pauseButton, stopButton, downloadButton;
    private Slider volumeSlider;
    
    private FileEngine fileEngine;

    public AudioPlayer(Window parentWindow) {
        initializeFileEngine(parentWindow);
        initializeUI();
        setupEventHandlers();
        setupKeyboardShortcuts();
    }

    private void initializeFileEngine(Window parentWindow) {
        if (parentWindow != null) {
            this.fileEngine = new FileEngine(parentWindow);
        } else {
            this.fileEngine = new FileEngine();
            this.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null && newScene.getWindow() != null) {
                    fileEngine.setParentWindow(newScene.getWindow());
                }
            });
        }
    }

    private void initializeUI() {
        currentTrack = new Label("No track playing");
        currentTrack.setPrefWidth(300);
        currentTrack.setStyle(textStyle());
        
        progressSlider = new Slider(0, 100, 0);
        progressSlider.setPrefWidth(300);
        
        timeLabel = new Label("00:00 / 00:00");
        timeLabel.setPrefWidth(100);
        timeLabel.setStyle(textStyle());
        
        playButton = new Button("▶");
        pauseButton = new Button("⏸");
        stopButton = new Button("⏹");
        downloadButton = new Button("⬇");
        
        volumeSlider = new Slider(0, 100, 80);
        volumeSlider.setPrefWidth(100);

        
        playButton.setStyle(buttonStyle());
        pauseButton.setStyle(buttonStyle());
        stopButton.setStyle(buttonStyle());
        downloadButton.setStyle(buttonStyle());
        
        this.getChildren().addAll(
            currentTrack, progressSlider, timeLabel, 
            playButton, pauseButton, stopButton, downloadButton, volumeSlider
        );
        this.setSpacing(10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPadding(new Insets(10));
        this.setStyle(playerStyle());
        progressSlider.setStyle(
            "-fx-control-inner-background: #3A4050;" +
            "-fx-accent: #7AB8FF;" + 
            "-fx-background-radius: 4;" +
            "-fx-padding: 0 5 0 5;"
        );
        updateUI();
    }

    private void setupEventHandlers() {
        playButton.setOnAction(e -> play());
        pauseButton.setOnAction(e -> pause());
        stopButton.setOnAction(e -> stop());
        downloadButton.setOnAction(e -> downloadCurrentTrack());

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue() / 100.0);
            }
        });

        progressSlider.setOnMousePressed(e -> isSeeking = true);
        progressSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null && isSeeking) {
                double seekTime = progressSlider.getValue() / 100.0 * mediaPlayer.getTotalDuration().toMillis();
                mediaPlayer.seek(Duration.millis(seekTime));
                isSeeking = false;
            }
        });
    }

    private void setupKeyboardShortcuts() {
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.SPACE) {
                        if (isPlaying) {
                            pause();
                        } else {
                            play();
                        }
                        event.consume();
                    } else if (event.getCode() == KeyCode.ESCAPE) {
                        stop();
                        event.consume();
                    }
                });
            }
        });
    }

    public void playTrack(MediaModel model) {
        if (model == null) return;
        
        System.out.println("Playing: " + model.getTitle());
        
        if (currentModel != null && currentModel.equals(model)) {
            if (isPlaying) {
                pause();
            } else {
                play();
            }
            return;
        }
        
        stop();
        
        currentModel = model;
        notifyCurrentTrackChanged(model);
        
        try {
            mediaPlayer = new MediaPlayer(new Media(model.getUrl()));
            setupMediaPlayerListeners();
            
            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            
            play();
            
        } catch (Exception e) {
            System.err.println("Error creating media player: " + e.getMessage());
            currentTrack.setText("Error: " + e.getMessage());
            updateUI();
        }
    }

    private void setupMediaPlayerListeners() {
        mediaPlayer.setOnReady(() -> {
            Platform.runLater(() -> {
                currentTrack.setText(currentModel.getTitle());
                progressSlider.setValue(0);
                if (mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                    timeLabel.setText("00:00 / " + formatTime(mediaPlayer.getTotalDuration().toSeconds()));
                } else {
                    timeLabel.setText("00:00 / --:--");
                }
                updateUI();
            });
        });
        
        mediaPlayer.setOnPlaying(() -> {
            Platform.runLater(() -> {
                isPlaying = true;
                updateUI();
            });
        });
        
        mediaPlayer.setOnPaused(() -> {
            Platform.runLater(() -> {
                isPlaying = false;
                updateUI();
            });
        });
        
        mediaPlayer.setOnEndOfMedia(() -> {
            Platform.runLater(() -> {
                isPlaying = false;
                currentTrack.setText("Finished: " + currentModel.getTitle());
                updateUI();
            });
        });
        
        mediaPlayer.setOnError(() -> {
            Platform.runLater(() -> {
                System.err.println("Media error: " + mediaPlayer.getError());
                isPlaying = false;
                currentTrack.setText("Error playing: " + currentModel.getTitle());
                updateUI();
            });
        });
        
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!isSeeking && mediaPlayer != null && mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
                Platform.runLater(() -> updateProgress(newTime));
            }
        });
    }

    private void updateProgress(Duration currentTime) {
        if (mediaPlayer == null || !mediaPlayer.getTotalDuration().greaterThan(Duration.ZERO)) {
            return;
        }
        
        double progress = currentTime.toMillis() / mediaPlayer.getTotalDuration().toMillis();
        progressSlider.setValue(progress * 100);
        
        String currentTimeStr = formatTime(currentTime.toSeconds());
        String totalTimeStr = formatTime(mediaPlayer.getTotalDuration().toSeconds());
        timeLabel.setText(currentTimeStr + " / " + totalTimeStr);
    }

    private String formatTime(double seconds) {
        if (Double.isInfinite(seconds) || Double.isNaN(seconds)) {
            return "--:--";
        }
        int min = (int) seconds / 60;
        int sec = (int) seconds % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void updateUI() {
        boolean hasMedia = mediaPlayer != null;
        boolean hasCurrentModel = currentModel != null;
        
        playButton.setDisable(!hasMedia || isPlaying);
        pauseButton.setDisable(!hasMedia || !isPlaying);
        stopButton.setDisable(!hasMedia);
        downloadButton.setDisable(!hasCurrentModel);
        
        double disabledOpacity = 0.5;
        double enabledOpacity = 1.0;
        
        playButton.setOpacity((!hasMedia || isPlaying) ? disabledOpacity : enabledOpacity);
        pauseButton.setOpacity((!hasMedia || !isPlaying) ? disabledOpacity : enabledOpacity);
        stopButton.setOpacity(!hasMedia ? disabledOpacity : enabledOpacity);
        downloadButton.setOpacity(!hasCurrentModel ? disabledOpacity : enabledOpacity);
    }

    public void play() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            currentTrack.setText("No track playing");
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        mediaPlayer = null;
        isPlaying = false;
        
        progressSlider.setValue(0);
        timeLabel.setText("00:00 / 00:00");
        
        if (currentModel != null) {
            notifyCurrentTrackChanged(null);
        }
        
        updateUI();
    }

    private void downloadCurrentTrack() {
        if (currentModel != null && fileEngine != null) {
            System.out.println("Downloading: " + currentModel.getTitle());
            fileEngine.downloadMedia(currentModel);
        }
    }

    public void cleanup() {
        stop();
        currentModel = null;
        currentTrack.setText("No track playing");
        updateUI();
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public MediaModel getCurrentTrack() {
        return currentModel;
    }
    
    public void setVolume(double volume) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume);
        }
        volumeSlider.setValue(volume * 100);
    }
    
    public double getVolume() {
        return mediaPlayer != null ? mediaPlayer.getVolume() : volumeSlider.getValue() / 100.0;
    }

    public void addCurrentTrackListener(CurrentTrackListener listener) {
        currentTrackListeners.add(listener);
    }

    public void removeCurrentTrackListener(CurrentTrackListener listener) {
        currentTrackListeners.remove(listener);
    }

    private void notifyCurrentTrackChanged(MediaModel track) {
        for (CurrentTrackListener listener : currentTrackListeners) {
            listener.onCurrentTrackChanged(track);
        }
    }

    private final String buttonStyle () {
        return "-fx-background-color: #2A2F3A;" +
        "-fx-text-fill: #D6D6E3;" +
        "-fx-background-radius: 6;";
    }

    private final String textStyle(){
        return "-fx-text-fill: #D6D6E3;";
    }

    private final String playerStyle(){
        return "-fx-background-color: #232835;" +
            "-fx-border-color: #2F3544;" +
            "-fx-border-width: 1px 0 0 0;" + 
            "-fx-padding: 10;" +
            "-fx-background-radius: 0;";
    }
}