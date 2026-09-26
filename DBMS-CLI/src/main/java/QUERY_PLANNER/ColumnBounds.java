package QUERY_PLANNER;

public final class ColumnBounds {
    public final String equalityKey;
    public final String lowerKey;
    public final String upperKey;

    public ColumnBounds(String equalityKey, String lowerKey, String upperKey) {
        this.equalityKey = equalityKey;
        this.lowerKey = lowerKey;
        this.upperKey = upperKey;
    }
}
