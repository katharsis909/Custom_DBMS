package SEMANTIC.AST_NODES;

public final class SingleTableAccessPath {
    public final boolean useWhereIndex;
    public final boolean useOrderIndex;
    public final int orderPrefixLength;

    private SingleTableAccessPath(boolean useWhereIndex, boolean useOrderIndex, int orderPrefixLength) {
        this.useWhereIndex = useWhereIndex;
        this.useOrderIndex = useOrderIndex;
        this.orderPrefixLength = orderPrefixLength;
    }

    public static SingleTableAccessPath tableScan() {
        return new SingleTableAccessPath(false, false, 0);
    }

    public static SingleTableAccessPath whereIndex() {
        return new SingleTableAccessPath(true, false, 0);
    }

    public static SingleTableAccessPath orderIndex(int orderPrefixLength) {
        return new SingleTableAccessPath(false, true, orderPrefixLength);
    }
}
