package musicsearch.widgets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.effect.DropShadow;

public class LyricsWindow {

    public static void show(Stage owner, String title, String text) {
        Stage stage = new Stage();
        stage.setTitle("Текст песни: " + title);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);

        // Заголовок
        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-text-fill: #af7aff;" +
                "-fx-font-size: 20px;" +
                "-fx-font-weight: bold;" +
                "-fx-font-family: 'Segoe UI', 'Open Sans', sans-serif;"
        );
        titleLabel.setPadding(new Insets(0, 0, 20, 0));

        // Создаем TextFlow для отображения текста с центрированием
        TextFlow textFlow = new TextFlow();
        textFlow.setTextAlignment(TextAlignment.CENTER);
        textFlow.setLineSpacing(8);
        
        // Разбиваем текст на строки
        String[] lines = text.split("\n");
        for (String line : lines) {
            Text textNode = new Text(line + "\n");
            textNode.setStyle(
                    "-fx-fill: #E0E0E0;" +
                    "-fx-font-size: 16px;" +
                    "-fx-font-family: 'Segoe UI', 'Open Sans', sans-serif;"
            );
            textFlow.getChildren().add(textNode);
        }

        // ScrollPane для прокрутки текста
        ScrollPane scrollPane = new ScrollPane(textFlow);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle(
                "-fx-background: transparent;" +
                "-fx-background-color: transparent;" +
                "-fx-border-color: #3A4050;" +
                "-fx-border-radius: 8px;" +
                "-fx-background-radius: 8px;"
        );

        // Основной контейнер
        VBox container = new VBox(10, titleLabel, scrollPane);
        container.setAlignment(Pos.TOP_CENTER);
        container.setPadding(new Insets(20));
        container.setStyle(
                "-fx-background-color: #2A2F3A;" +
                "-fx-background-radius: 8px;"
        );

        // Root контейнер
        BorderPane root = new BorderPane(container);
        root.setPadding(new Insets(15));
        root.setStyle(
                "-fx-background-color: #323848;" +
                "-fx-border-color: #4A5063;" +
                "-fx-border-radius: 8px;" +
                "-fx-background-radius: 8px;"
        );

        // Эффект тени
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.6));
        shadow.setRadius(15);
        root.setEffect(shadow);

        Scene scene = new Scene(root, 650, 550);
        stage.setScene(scene);
        stage.show();
    }
}