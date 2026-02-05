package musicsearch.service;

import java.util.List;

import musicsearch.models.MediaType;
import musicsearch.models.MediaModel;

public interface MediaSearchProvider {
    MediaType getType();
    List<MediaModel> search(String query);
    List<MediaModel> loadMore();
}
