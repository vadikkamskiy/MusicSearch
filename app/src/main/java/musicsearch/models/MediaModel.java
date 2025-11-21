package musicsearch.models;

import lombok.Getter;

@Getter
public class MediaModel {
    private String title;
    private String time;
    private String url;
    private String imageUrl;
    private boolean isDownloaded;

    public MediaModel(String title, String time, String url, String imageUrl, boolean isDownloaded ){
        this.title = title;
        this.time = time;
        this.url = url;
        this.imageUrl = imageUrl;
        this.isDownloaded = isDownloaded;
    }

    public String toString(){
        return title + " | " + time;
    }
}
