package musicsearch.models;

import lombok.Getter;

@Getter
// TODO : Extend this class for specific media types like AudioModel, VideoModel, etc.
public abstract class MediaModel {
    private String title;
    private String url;
    private String previewUrl;
    private boolean isDownloaded;
    protected MediaType mediaType;

    public MediaModel(String title, String url, String previewUrl, boolean isDownloaded, MediaType mediaType) {
        this.title = title;
        this.url = url;
        this.previewUrl = previewUrl;
        this.isDownloaded = isDownloaded;
        this.mediaType = mediaType;
    }

    public String toString(){
        return title;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }
}
