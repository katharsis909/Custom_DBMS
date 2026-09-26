package QUERY_PLANNER;

import STRUCTURE.IndexDefinition;

public final class IndexedAccess {
    public final IndexDefinition index;
    public final IndexedLookup lookup;

    public IndexedAccess(IndexDefinition index, IndexedLookup lookup) {
        this.index = index;
        this.lookup = lookup;
    }
}
