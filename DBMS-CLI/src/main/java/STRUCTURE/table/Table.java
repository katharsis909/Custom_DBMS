package STRUCTURE.table;

import QUERY_PLANNER.IndexedAccess;
import QUERY_PLANNER.OrderIndexMatch;
import QUERY_PLANNER.TableIndexPlanner;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.WhereClause;
import STRUCTURE.Catalog;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.table.utility.TableConstraintValidator;
import STRUCTURE.table.utility.TableHeap;
import STRUCTURE.table.utility.TableIndexManager;
import STRUCTURE.table.utility.TableSchema;
import disk_persistence.RowPointer;
import disk_persistence.TableIterator;
import indexing.bplustree.BPlusTreeDiskStore;

import java.util.ArrayList;
import java.util.List;

public class Table {
    private final TableSchema schema;
    private final TableHeap heap;
    private final TableIndexManager indexManager;
    private final TableIndexPlanner planner;

    public Table(String tableName, List<Column> schemaList) throws DBMSException {
        this.schema = new TableSchema(tableName, schemaList);
        this.heap = new TableHeap(tableName);
        this.indexManager = new TableIndexManager(tableName);
        this.indexManager.loadIndexDefinitions();
        this.indexManager.initializePrimaryKeyIndex(schema.getPrimaryKeyColumns(), this);
        this.planner = new TableIndexPlanner(schema, indexManager);
    }

    public Table(String tableName) throws DBMSException {
        this.schema = new TableSchema(tableName);
        this.heap = new TableHeap(tableName);
        this.indexManager = new TableIndexManager(tableName);
        this.indexManager.loadIndexDefinitions();
        this.indexManager.initializePrimaryKeyIndex(schema.getPrimaryKeyColumns(), this);
        this.planner = new TableIndexPlanner(schema, indexManager);
    }

    public String getTable_name() {
        return schema.getTableName();
    }

    public List<Column> getColumnList() {
        return schema.getColumnList();
    }

    public List<String> getPrimaryKeyColumns() {
        return schema.getPrimaryKeyColumns();
    }

    public RowPointer addRecord(List<DBMSDataType> values) throws DBMSException {
        return addRecord(values, null);
    }

    public RowPointer addRecord(List<DBMSDataType> values, Catalog catalog) throws DBMSException {
        Record record = TableConstraintValidator.validateAndBuildRecord(values, schema.getColumnList(), catalog, this);
        return insertRecord(record);
    }

    public RowPointer insertRecord(Record record) throws DBMSException {
        TableConstraintValidator.validatePrimaryKey(record, schema.getPrimaryKeyColumns(), indexManager);
        byte[] rowBytes = disk_persistence.RowSerializer.serialize(record, this);
        RowPointer rowPointer = heap.insertRow(rowBytes);
        indexManager.insertIndexEntries(record, rowPointer, schema.getPrimaryKeyColumns());
        return rowPointer;
    }

    public TableIterator iterator() throws DBMSException {
        return heap.iterator(this);
    }

    public Record readRecord(RowPointer pointer) throws DBMSException {
        return heap.readRecord(pointer, this);
    }

    public void createIndex(String indexName, String columnName) throws DBMSException {
        createIndex(indexName, List.of(columnName));
    }

    public void createIndex(String indexName, List<String> columnNames) throws DBMSException {
        indexManager.createIndex(indexName, columnNames, schema, this);
    }

    public void createIndex(String indexName, List<String> columnNames, List<Boolean> columnDirections) throws DBMSException {
        indexManager.createIndex(indexName, columnNames, columnDirections, schema, this);
    }

    public List<Record> indexedRecordsFor(WhereClause whereClause) throws DBMSException {
        IndexedAccess access = planner.bestIndexedAccess(whereClause);
        if (access == null) {
            return null;
        }

        BPlusTreeDiskStore<String, RowPointer> store = access.index.primaryKey
                ? indexManager.getPrimaryKeyIndex()
                : indexManager.createIndexStore(access.index.indexName);
        List<RowPointer> pointers = access.lookup.equalityKey != null
                ? store.search(access.lookup.equalityKey)
                : store.searchRange(access.lookup.lowerKey, access.lookup.upperKey);
        List<Record> records = new ArrayList<>();
        for (RowPointer pointer : pointers) {
            records.add(readRecord(pointer));
        }
        return records;
    }

    public boolean hasUsableIndexForWhere(WhereClause whereClause) throws DBMSException {
        return planner.hasUsableIndexForWhere(whereClause);
    }

    public boolean hasSingleIndexCoveringWhereColumns(WhereClause whereClause) throws DBMSException {
        return planner.hasSingleIndexCoveringWhereColumns(whereClause);
    }

    public OrderIndexMatch findBestOrderIndexMatch(List<OrderByItem> orderByItems) {
        return planner.findBestOrderIndexMatch(orderByItems);
    }

    public List<Record> orderedRecordsFor(List<String> columnNames, boolean ascending) throws DBMSException {
        BPlusTreeDiskStore<String, RowPointer> store = indexManager.indexStoreForLeadingColumns(columnNames, schema.getPrimaryKeyColumns());
        if (store == null) {
            return null;
        }
        List<Record> records = new ArrayList<>();
        for (RowPointer pointer : store.valuesInOrder(ascending)) {
            records.add(readRecord(pointer));
        }
        return records;
    }

    public List<DBMSDataType> getValueFromRecord(Record r) throws DBMSException {
        return schema.getValueFromRecord(r);
    }

    public void printColumns() {
        schema.printColumns();
    }

    public static void writeSchema(String tableName, List<Column> schema) throws DBMSException {
        TableSchema.writeSchema(tableName, schema);
    }

    public boolean hasColumn(String columnName) {
        return schema.hasColumn(columnName);
    }

    public boolean containsValue(String columnName, DBMSDataType expectedValue) throws DBMSException {
        return TableConstraintValidator.containsValue(this, columnName, expectedValue);
    }

    public boolean hasIndexOnColumn(String columnName) {
        return planner.hasIndexOnColumn(columnName);
    }

    public boolean hasIndexStartingWithColumns(List<String> columnNames) {
        return planner.hasIndexStartingWithColumns(columnNames);
    }

    public int longestIndexPrefixForColumns(List<String> columnNames) {
        return planner.longestIndexPrefixForColumns(columnNames);
    }

    public int getRowCount() throws DBMSException {
        return heap.getRowCount(this);
    }
}
