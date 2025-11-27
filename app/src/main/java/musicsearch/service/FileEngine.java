package musicsearch.service;

import java.io.File;
import java.io.FileOutputStream;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import musicsearch.models.MediaModel;

public class FileEngine {
    private Window parentWindow;
    
    public FileEngine(Window parentWindow) {
        this.parentWindow = parentWindow;
    }
    
    public FileEngine() {

    }
    
    public void setParentWindow(Window parentWindow) {
        this.parentWindow = parentWindow;
    }

    public void downloadMedia(MediaModel mediaModel) {
        Window window = getParentWindow();
        
        System.out.println("Скачивание: " + mediaModel.getTitle());
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить аудиофайл");
        
        File musicDir = new File(System.getProperty("user.home"), "Music");
        if (!musicDir.exists()) {
            musicDir = new File(System.getProperty("user.home"));
        }
        fileChooser.setInitialDirectory(musicDir);
        
        String cleanFileName = cleanFileName(mediaModel.getTitle()) + ".mp3";
        fileChooser.setInitialFileName(cleanFileName);
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("MP3 files", "*.mp3")
        );
        
        File file = fileChooser.showSaveDialog(window);
        if (file != null) {
            downloadFile(mediaModel.getUrl(), file);
        }
    }

    private Window getParentWindow() {
        if (parentWindow != null) {
            return parentWindow;
        }
        
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                parentWindow = window;
                return window;
            }
        }
        
        if (!Window.getWindows().isEmpty()) {
            parentWindow = Window.getWindows().get(0);
            return parentWindow;
        }
        
        return null;
    }

    private void downloadFile(String url, File outputFile) {
        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Connection.Response response = Jsoup.connect(url)
                            .ignoreContentType(true)
                            .timeout(30000)
                            .maxBodySize(0)
                            .execute();

                    byte[] body = response.bodyAsBytes();
                    long totalSize = body.length;
                    updateMessage("Скачивание: " + outputFile.getName());
                    updateProgress(0, totalSize);

                    try (FileOutputStream out = new FileOutputStream(outputFile)) {
                        int chunkSize = 8192;
                        for (int i = 0; i < body.length; i += chunkSize) {
                            int end = Math.min(i + chunkSize, body.length);
                            out.write(body, i, end - i);
                            updateProgress(i + (end - i), totalSize);
                            Thread.sleep(10);
                        }
                    }

                    updateMessage("Скачивание завершено!");
                    return null;
                } catch (Exception e) {
                    updateMessage("Ошибка скачивания: " + e.getMessage());
                    throw e;
                }
            }
        };

        showDownloadProgress(downloadTask, outputFile);
        new Thread(downloadTask).start();
    }

    private void showDownloadProgress(Task<Void> downloadTask, File outputFile) {
        Window window = getParentWindow();

        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(downloadTask.progressProperty());
        
        Label statusLabel = new Label();
        statusLabel.textProperty().bind(downloadTask.messageProperty());

        VBox progressBox = new VBox(5, progressBar, statusLabel);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(10));

        Stage progressStage = new Stage();
        progressStage.setTitle("Скачивание");
        progressStage.setScene(new Scene(progressBox, 300, 100));

        if (window != null) {
            progressStage.initOwner(window);
        }

        progressStage.show();

        downloadTask.setOnSucceeded(e -> {
            progressStage.close();
            showAlert("Скачивание завершено", "Файл сохранен: " + outputFile.getName());
        });

        downloadTask.setOnFailed(e -> {
            progressStage.close();
            outputFile.delete();
            showAlert("Ошибка", "Не удалось скачать файл: " + 
                    downloadTask.getException().getMessage());
        });
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            
            Window window = getParentWindow();
            if (window != null) {
                alert.initOwner(window);
            }
            
            alert.showAndWait();
        });
    }
    
    private String cleanFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public void update(){
        
    }
}