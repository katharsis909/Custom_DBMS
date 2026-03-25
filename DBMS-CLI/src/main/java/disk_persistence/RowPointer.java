package disk_persistence;

public class RowPointer {
    private final int pageId;
    private final int rowOffset;

    public RowPointer(int pageId, int rowOffset) {
        this.pageId = pageId;
        this.rowOffset = rowOffset;
    }

    public int getPageId() {
        return pageId;
    }

    public int getRowOffset() {
        return rowOffset;
    }
}
