package musicsearch;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import musicsearch.widgets.MainWindow;


public class App extends Application {

    @Override
    public void start(Stage primaryStage) {

        // В начале public void start(Stage stage) { ... }
        System.out.println("start() on FX thread? " + javafx.application.Platform.isFxApplicationThread());


        Label musicsearchLabel = new Label("music-search");

        StackPane root = new StackPane();
        root.getChildren().add(musicsearchLabel);

        MainWindow mainWindow = new MainWindow();

        primaryStage.setTitle("Audio Search");

        primaryStage.setScene(mainWindow.getScene());
        primaryStage.setMinWidth(1020);
        primaryStage.setMinHeight(480);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event->{
            mainWindow.shutdown();
            Platform.exit();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
