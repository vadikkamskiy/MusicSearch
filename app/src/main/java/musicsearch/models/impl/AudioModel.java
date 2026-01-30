package musicsearch.models.impl;

import musicsearch.models.MediaType;
import lombok.Getter;
import musicsearch.models.MediaModel;

@Getter
public class AudioModel extends MediaModel {
    private String artist;
    private String title;
    private String time;
    private MediaType mediaType = MediaType.AUDIO;
    public AudioModel(String artist, String title, String time, String url, String previewUrl, boolean isDownloaded, MediaType mediaType) {
        super(artist + " - " + title, url, previewUrl, isDownloaded, mediaType);
        this.artist = artist;
        this.title = title;
        this.time = time;
    }

    @Override
    public String toString() {
        return artist + " - " + title;
    }
}
