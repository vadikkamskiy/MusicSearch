package musicsearch.models;

import java.util.Optional;

import lombok.Getter;

@Getter
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

    public Optional<String> getSearchArtist() {
        return Optional.empty();
    }

    public String toString(){
        return title;
    }

    public void setDownloaded(boolean downloaded) {
        isDownloaded = downloaded;
    }
}
