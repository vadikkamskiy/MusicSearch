package musicsearch.models;

import java.util.List;

public interface PlaybackListener {
    void onTrackSelected(MediaModel mediaModel);
    void onPlayPlaylist(List<MediaModel> playlist, int startIndex);
}
