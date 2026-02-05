package musicsearch.models;

public enum MediaFilter {
    ALL("all files"),
    AUDIO("music"),
    VIDEO("videos"),
    IMAGE("pictures"),
    DOCUMENT("documents"),
    ;

    private final String label;

    MediaFilter(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
