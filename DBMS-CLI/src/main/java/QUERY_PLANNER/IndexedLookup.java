package QUERY_PLANNER;

public final class IndexedLookup {
    public final String equalityKey;
    public final String lowerKey;
    public final String upperKey;
    public final int score;

    private IndexedLookup(String equalityKey, String lowerKey, String upperKey, int score) {
        this.equalityKey = equalityKey;
        this.lowerKey = lowerKey;
        this.upperKey = upperKey;
        this.score = score;
    }

    public static IndexedLookup equality(String key, int score) {
        return new IndexedLookup(key, null, null, score);
    }

    public static IndexedLookup range(String lowerKey, String upperKey, int score) {
        return new IndexedLookup(null, lowerKey, upperKey, score);
    }
}
