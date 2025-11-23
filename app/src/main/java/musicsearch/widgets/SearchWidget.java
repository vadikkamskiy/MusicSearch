package musicsearch.widgets;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import musicsearch.service.SearchEngine;


public class SearchWidget {
    private HBox layout;
    private TextField searchField;
    private Button searchButton;
    private Button homeButton;
    private SearchEngine searchEngine;
    

    public SearchWidget(SearchEngine searchEngine){
        this.searchEngine = searchEngine;
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
        layout.getChildren().addAll(homeButton,searchField, searchButton);

        searchButton.setOnAction(event -> {
            String query = searchField.getText();
            this.searchEngine.search(query);
        });
        
        homeButton.setOnAction(event -> {
            this.searchField.clear();
            this.searchEngine.goHome();
        });
        homeButton.setStyle(
            background() +
            "-fx-text-fill: #D6D6E3;"
        );
        homeButton.setOnAction(event -> {
            this.searchEngine.goHome();
        });
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

