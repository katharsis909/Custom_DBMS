package QUERY_PLANNER;

import STRUCTURE.IndexDefinition;

public final class OrderIndexMatch {
    public final IndexDefinition index;
    public final int prefixLength;
    public final boolean scanAscending;
    public final boolean completeCovered;

    public OrderIndexMatch(IndexDefinition index, int prefixLength, boolean scanAscending, boolean completeCovered) {
        this.index = index;
        this.prefixLength = prefixLength;
        this.scanAscending = scanAscending;
        this.completeCovered = completeCovered;
    }

    public static OrderIndexMatch none() {
        return new OrderIndexMatch(null, 0, true, false);
    }
}
