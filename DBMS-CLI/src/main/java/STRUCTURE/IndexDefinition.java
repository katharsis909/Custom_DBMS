package STRUCTURE;

import java.util.ArrayList;
import java.util.List;

// Describes one persisted table index. Primary-key indexes use the same shape
// as secondary indexes so planner code can compare both uniformly.
// If in doubt on this class or how indexes are defined, refer to Documentation/Index-Planning-Notes.md
public final class IndexDefinition {
    public final String indexName;
    public final List<String> columnNames;
    public final boolean primaryKey;

    public IndexDefinition(String indexName, List<String> columnNames) {
        this(indexName, columnNames, false);
    }

    public IndexDefinition(String indexName, List<String> columnNames, boolean primaryKey) {
        this.indexName = indexName;
        this.columnNames = new ArrayList<>(columnNames);
        this.primaryKey = primaryKey;
    }

    public static IndexDefinition primary(List<String> columnNames) {
        return new IndexDefinition("__primary_key__", columnNames, true);
    }
}
