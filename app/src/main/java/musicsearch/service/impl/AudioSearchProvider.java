package musicsearch.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.cdimascio.dotenv.Dotenv;
import musicsearch.models.MediaModel;
import musicsearch.models.MediaType;
import musicsearch.models.impl.AudioModel;
import musicsearch.service.MediaSearchProvider;

public class AudioSearchProvider implements MediaSearchProvider {

    String currentQuery = "";
    int currentPage = 1;
    private List<MediaModel> LocalFiles = new ArrayList<>();
    List<MediaModel> results = new ArrayList<>();
    private int page = 0;
    private int totalPages = 1;
    private static final int PAGE_SIZE = 48;
    private static final Dotenv dotenv = Dotenv.load();

    @Override
    public MediaType getType() {
        return MediaType.AUDIO;
    }

    @Override
    public List<MediaModel> search(String query) {
        this.currentQuery = query;
        this.page = 0;
        this.totalPages = 1;
        return fetchPage();
    }

    @Override
    public List<MediaModel> loadMore() {
        if (page + 1 >= totalPages) {
            return List.of();
        }
        return fetchPage();
    }

    private List<MediaModel> fetchPage() {
        List<MediaModel> result = new ArrayList<>();
        try {
            String url = "https://" +
                dotenv.get("URL_SOURCE") +
                (page == 0
                    ? "/search?q=" + currentQuery
                    : "/search/start/" + (PAGE_SIZE * page) + "?q=" + currentQuery);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .maxBodySize(0)
                    .get();
            if (page == 0) {
                Elements pages = doc.select(".pagination__item");
                totalPages = Math.max(1, pages.size());
            }
            Elements tracks = doc.select("li.tracks__item.track.mustoggler");

            for (Element track : tracks) {
                JsonObject obj = JsonParser
                        .parseString(track.attr("data-musmeta"))
                        .getAsJsonObject();
                result.add(new AudioModel(
                    obj.get("artist").getAsString(),
                    obj.get("title").getAsString(),
                    track.selectFirst("div.track__fulltime") != null
                            ? track.selectFirst("div.track__fulltime").text()
                            : "Unknown",
                    obj.get("url").getAsString(),
                    obj.get("img").getAsString(),
                    false,
                    MediaType.AUDIO
                ));
            }
            page++;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
