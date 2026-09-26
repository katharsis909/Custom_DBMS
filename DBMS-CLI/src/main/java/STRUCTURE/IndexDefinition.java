package STRUCTURE;

import java.util.ArrayList;
import java.util.List;

// Describes one persisted table index. Primary-key indexes use the same shape
// as secondary indexes so planner code can compare both uniformly.
// Supports per-column ordering directions (true = ASC, false = DESC).
public final class IndexDefinition {
    public final String indexName;
    public final List<String> columnNames;
    public final List<Boolean> columnDirections; // true = ASC, false = DESC
    public final boolean primaryKey;

    public IndexDefinition(String indexName, List<String> columnNames) {
        this(indexName, columnNames, defaultDirections(columnNames.size()), false);
    }

    public IndexDefinition(String indexName, List<String> columnNames, List<Boolean> columnDirections) {
        this(indexName, columnNames, columnDirections, false);
    }

    public IndexDefinition(String indexName, List<String> columnNames, List<Boolean> columnDirections, boolean primaryKey) {
        this.indexName = indexName;
        this.columnNames = new ArrayList<>(columnNames);
        this.columnDirections = new ArrayList<>(columnDirections);
        this.primaryKey = primaryKey;
    }

    public boolean isAscending(int columnIndex) {
        if (columnIndex >= 0 && columnIndex < columnDirections.size()) {
            return columnDirections.get(columnIndex);
        }
        return true;
    }

    public static IndexDefinition primary(List<String> columnNames) {
        return new IndexDefinition("__primary_key__", columnNames, defaultDirections(columnNames.size()), true);
    }

    private static List<Boolean> defaultDirections(int count) {
        List<Boolean> directions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            directions.add(true);
        }
        return directions;
    }
}
