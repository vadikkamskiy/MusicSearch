package musicsearch.models.impl;

import musicsearch.models.MediaModel;
import musicsearch.models.MediaType;

public class VideoModel extends MediaModel {

    public VideoModel(String title, String time, String url, String previewUrl, boolean isDownloaded, MediaType mediaType) {
        super(title, url, previewUrl, isDownloaded, mediaType);
    }

}
