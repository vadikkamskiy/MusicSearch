package musicsearch.service.impl;

import java.util.ArrayList;
import java.util.List;

import musicsearch.models.MediaModel;
import musicsearch.models.MediaType;
import musicsearch.service.MediaSearchProvider;

public class VideoSearchProvider implements MediaSearchProvider {

    @Override
    public MediaType getType() {
        return MediaType.VIDEO;
    }

    @Override
    public List<MediaModel> search(String query) {
        
        return new ArrayList<>();
    }

    @Override
    public List<MediaModel> loadMore() {
        return new ArrayList<>();
    }

}
