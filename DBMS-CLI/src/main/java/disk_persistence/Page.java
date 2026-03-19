package disk_persistence;

import java.util.Arrays;

/**
 * Fixed-size slotted page.
 * Header grows from the front, row data grows from the back.
 */
public class Page {
    public static final int PAGE_SIZE = 4096;

    private static final int PAGE_ID_OFFSET = 0;
    private static final int ROW_COUNT_OFFSET = 4;
    private static final int FREE_PTR_OFFSET = 6;
    private static final int DATA_PTR_OFFSET = 8;
    private static final int HEADER_SIZE = 14;

    private final byte[] data;

    public Page(int pageId) {
        this.data = new byte[PAGE_SIZE];
        setInt(PAGE_ID_OFFSET, pageId);
        setShort(ROW_COUNT_OFFSET, 0);
        setShort(FREE_PTR_OFFSET, HEADER_SIZE);
        setShort(DATA_PTR_OFFSET, PAGE_SIZE);
    }

    public int getPageId() {
        return getInt(PAGE_ID_OFFSET);
    }

    public int getRowCount() {
        return getShort(ROW_COUNT_OFFSET);
    }

    public boolean hasSpace(int rowSize) {
        int freePtr = getFreePtr();
        int dataPtr = getDataPtr();
        return freePtr + 2 <= dataPtr - rowSize;
    }

    public void insertRow(byte[] row) {
        int rowSize = row.length;
        if (!hasSpace(rowSize)) {
            throw new IllegalStateException("Not enough space in page " + getPageId());
        }

        int newDataPtr = getDataPtr() - rowSize;
        System.arraycopy(row, 0, data, newDataPtr, rowSize);

        int slotOffset = HEADER_SIZE + (getRowCount() * 2);
        setShort(slotOffset, newDataPtr);
        setShort(DATA_PTR_OFFSET, newDataPtr);
        setShort(FREE_PTR_OFFSET, getFreePtr() + 2);
        setShort(ROW_COUNT_OFFSET, getRowCount() + 1);
    }

    public byte[] getRow(int slotIndex) {
        int rowCount = getRowCount();
        if (slotIndex < 0 || slotIndex >= rowCount) {
            throw new IllegalArgumentException("Invalid slot index " + slotIndex);
        }

        int rowStart = getShort(HEADER_SIZE + (slotIndex * 2));
        int rowEnd = slotIndex == 0 ? PAGE_SIZE : getShort(HEADER_SIZE + ((slotIndex - 1) * 2));
        return Arrays.copyOfRange(data, rowStart, rowEnd);
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public void loadData(byte[] bytes) {
        if (bytes.length != PAGE_SIZE) {
            throw new IllegalArgumentException("Page data must be exactly " + PAGE_SIZE + " bytes");
        }
        System.arraycopy(bytes, 0, data, 0, PAGE_SIZE);
    }

    private int getFreePtr() {
        return getShort(FREE_PTR_OFFSET);
    }

    private int getDataPtr() {
        return getShort(DATA_PTR_OFFSET);
    }

    private void setInt(int pos, int value) {
        data[pos] = (byte) (value >>> 24);
        data[pos + 1] = (byte) (value >>> 16);
        data[pos + 2] = (byte) (value >>> 8);
        data[pos + 3] = (byte) value;
    }

    private int getInt(int pos) {
        return ((data[pos] & 0xFF) << 24)
                | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8)
                | (data[pos + 3] & 0xFF);
    }

    private void setShort(int pos, int value) {
        data[pos] = (byte) (value >>> 8);
        data[pos + 1] = (byte) value;
    }

    private short getShort(int pos) {
        return (short) (((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF));
    }
}
