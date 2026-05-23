package disk_persistence;

import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.Table;

import java.util.NoSuchElementException;

public class TableIterator {
    private final Table table;
    private final PageManager pageManager;
    private final int maxPageId;

    private int currentPageId;
    private Page currentPage;
    private int currentRowIndex;

    public TableIterator(Table table, PageManager pageManager) throws DBMSException {
        this.table = table;
        this.pageManager = pageManager;
        this.maxPageId = pageManager.getCurrentPageId();
        this.currentPageId = 0;
        this.currentRowIndex = 0;
        this.currentPage = pageManager.loadPage(0);
    }

    public boolean hasNext() throws DBMSException {
        while (true) {
            if (currentRowIndex < currentPage.getRowCount()) {
                return true;
            }

            if (currentPageId < maxPageId) {
                currentPageId++;
                currentPage = pageManager.loadPage(currentPageId);
                currentRowIndex = 0;
                continue;
            }

            return false;
        }
    }

    public Record next() throws DBMSException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        byte[] rowBytes = currentPage.getRow(currentRowIndex);
        currentRowIndex++;
        return RowSerializer.deserialize(rowBytes, table);
    }

    public RowPointer nextPointer() throws DBMSException {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return new RowPointer(currentPageId, currentPage.getRowOffset(currentRowIndex));
    }
}
