package musicsearch.widgets;

import java.io.File;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import musicsearch.models.MediaFilter;
import musicsearch.service.SearchEngine;


public class SearchWidget {
    private HBox layout;
    private TextField searchField;
    private Button searchButton;
    private Button homeButton;
    private SearchEngine searchEngine;
    private final ComboBox<MediaFilter> MediaTypeComboBox = new ComboBox<>();
    
    public SearchWidget(SearchEngine searchEngine){
        this.searchEngine = searchEngine;
        
        MediaTypeComboBox.setItems(
            FXCollections.observableArrayList(MediaFilter.values())
        );
        MediaTypeComboBox.getSelectionModel().select(MediaFilter.ALL);
        MediaTypeComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(MediaFilter item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getLabel());
            }
        });
        MediaTypeComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(MediaFilter item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getLabel());
            }
        });

        MediaTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == MediaFilter.AUDIO) {
                searchEngine.scanAudioFolder();
            } else if (newVal == MediaFilter.VIDEO) {
                searchEngine.scanVideoFolder();
            }
        });
        
        homeButton = new Button("Home");
        layout = new HBox();
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #1E2330;"); 
        layout.setSpacing(10);
        searchField = new TextField();
        searchField.setStyle(SEARCH_FIELD_STYLE);
        searchField.setOnAction(event -> {
            String query = searchField.getText();
            this.searchEngine.search(query);
        });
        searchButton = new Button("Search");
        searchButton.setStyle(
            background() +
            "-fx-text-fill: #D6D6E3;"
        );
        layout.getChildren().addAll(MediaTypeComboBox, homeButton, searchField, searchButton);

        searchButton.setOnAction(event -> {
            String query = searchField.getText();
            this.searchEngine.search(query);
        });
        
        homeButton.setOnAction(event -> {
            this.searchField.clear();
            if( MediaTypeComboBox.getValue() == MediaFilter.AUDIO){
                this.searchEngine.scanAudioFolder();
            } else if (MediaTypeComboBox.getValue() == MediaFilter.VIDEO){
                this.searchEngine.scanVideoFolder();
            } else {
                this.searchEngine.goHome(new File(System.getProperty("user.home"), "Music"));
            }
        });
        homeButton.setStyle(
            background() +
            "-fx-text-fill: #D6D6E3;"
        );

    }  
    public HBox getWidget() {
        return layout;
    }

    private static final String SEARCH_FIELD_STYLE = 
            "-fx-background-color: #2A2F3A; " +
            "-fx-text-fill: #D6D6E3; " +
            "-fx-prompt-text-fill: #9EA3B5;";
            
    private static final String background() {
        return "-fx-background-color: #323848; ";
    }
}

