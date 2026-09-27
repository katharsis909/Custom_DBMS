package STRUCTURE.table.utility;

import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.table.Table;
import disk_persistence.PageManager;
import disk_persistence.RowPointer;
import disk_persistence.RowSerializer;
import disk_persistence.TableIterator;

public class TableHeap {
    private final String tableName;
    private final PageManager pageManager;
    private int approximateRowCount = -1;

    public TableHeap(String tableName) throws DBMSException {
        this.tableName = tableName;
        this.pageManager = new PageManager(tableName);
    }

    public RowPointer insertRow(byte[] rowBytes) throws DBMSException {
        if (approximateRowCount >= 0) {
            approximateRowCount++;
        }
        return pageManager.insertRow(rowBytes);
    }

    public Record readRecord(RowPointer pointer, Table table) throws DBMSException {
        byte[] rowBytes = pageManager.loadPage(pointer.getPageId()).getRowByOffset(pointer.getRowOffset());
        return RowSerializer.deserialize(rowBytes, table);
    }

    public TableIterator iterator(Table table) throws DBMSException {
        return new TableIterator(table, pageManager);
    }

    public int getRowCount(Table table) throws DBMSException {
        int rowCount = 0;
        TableIterator tableIterator = iterator(table);
        while (tableIterator.hasNext()) {
            tableIterator.next();
            rowCount++;
        }
        approximateRowCount = rowCount;
        return rowCount;
    }

    public int getApproximateRowCount(Table table) throws DBMSException {
        if (approximateRowCount < 0) {
            getRowCount(table);
        }
        return approximateRowCount;
    }
}
