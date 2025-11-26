package musicsearch.service.Events;

import musicsearch.models.MediaModel;

public class TrackDownloadEvent {
    public final MediaModel track;
    public TrackDownloadEvent(MediaModel track) { this.track = track; }
}
