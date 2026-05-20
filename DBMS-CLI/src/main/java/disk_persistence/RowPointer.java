package disk_persistence;

import java.util.Objects;

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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RowPointer)) {
            return false;
        }
        RowPointer that = (RowPointer) other;
        return pageId == that.pageId && rowOffset == that.rowOffset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageId, rowOffset);
    }
}
