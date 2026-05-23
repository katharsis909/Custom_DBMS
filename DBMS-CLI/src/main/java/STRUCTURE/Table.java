package STRUCTURE;

import disk_persistence.PageManager;
import disk_persistence.RowPointer;
import disk_persistence.RowSerializer;
import disk_persistence.TableIterator;
import indexing.bplustree.BPlusTree;
import indexing.bplustree.BPlusTreeDiskStore;
import indexing.bplustree.BPlusTreeSerializers;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.WhereClause;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Table {
    private final List<Column> columnList;
    private final String table_name;
    private final PageManager pageManager;
    private final BPlusTreeDiskStore<String, RowPointer> primaryKeyIndex;
    private final List<IndexDefinition> indexes = new ArrayList<>();

    public Table(String tableName, List<Column> schema) throws DBMSException {
        this.table_name = tableName;
        this.columnList = new ArrayList<>(schema);
        ensureTableDirectory();
        writeSchema(tableName, schema);
        this.pageManager = new PageManager(tableName);
        this.primaryKeyIndex = createPrimaryKeyIndex(tableName);
        loadIndexDefinitions();
        initializePrimaryKeyIndex();
    }

    public Table(String tableName) throws DBMSException {
        this.table_name = tableName;
        ensureTableDirectory();
        this.columnList = loadSchema(new File("data", tableName));
        this.pageManager = new PageManager(tableName);
        this.primaryKeyIndex = createPrimaryKeyIndex(tableName);
        loadIndexDefinitions();
        initializePrimaryKeyIndex();
    }

    public List<Column> getColumnList() {
        return new ArrayList<>(columnList);
    }

    public List<String> getPrimaryKeyColumns() {
        List<String> primaryKeyColumns = new ArrayList<>();
        for (Column column : columnList) {
            if (column.isPrimaryKey()) {
                primaryKeyColumns.add(column.getColumnName());
            }
        }
        return primaryKeyColumns;
    }

    public String getTable_name() {
        return table_name;
    }

    /**
     * Existing SQL insert path still calls this method. It now returns the
     * physical row pointer, but callers may ignore it until indexing is added.
     */
    public synchronized RowPointer addRecord(List<DBMSDataType> values) throws DBMSException {
        return addRecord(values, null);
    }

    public synchronized RowPointer addRecord(List<DBMSDataType> values, Catalog catalog) throws DBMSException {
        if (values.size() != columnList.size()) {
            throw new DBMSException("Column count mismatch. Expected " + columnList.size() + " but got " + values.size());
        }

        Record record = new Record();
        for (int i = 0; i < values.size(); i++) {
            Column column = columnList.get(i);
            DBMSDataType value = values.get(i);
            if (value == null) {
                if (column.isPrimaryKey()) {
                    throw new DBMSException("Primary key column '" + column.getColumnName() + "' must be non-null and non-empty.");
                }
                throw new DBMSException("Column '" + column.getColumnName() + "' must not be null.");
            }
            if (!value.typeEquals(column.getColumn_type())) {
                throw new DBMSException("Type mismatch at column " + column.getColumnName()
                        + ". Expected " + column.getColumn_type() + " but got " + value.getType());
            }
            record.setValue(column.getColumnName(), value);
        }

        validateForeignKeyReferences(record, catalog);
        return insertRecord(record);
    }

    public synchronized RowPointer insertRecord(Record record) throws DBMSException {
        String primaryKey = primaryKeyForRecord(record);
        if (primaryKey != null && !primaryKeyIndex.search(primaryKey).isEmpty()) {
            throw new DBMSException("Duplicate primary key value.");
        }

        byte[] rowBytes = RowSerializer.serialize(record, this);
        RowPointer rowPointer = pageManager.insertRow(rowBytes);
        if (primaryKey != null) {
            primaryKeyIndex.insert(primaryKey, rowPointer);
        }
        for (IndexDefinition index : indexes) {
            DBMSDataType value = record.getValue(index.columnName);
            createIndexStore(index.indexName).insert(indexKey(value), rowPointer);
        }
        return rowPointer;
    }

    public TableIterator iterator() throws DBMSException {
        return new TableIterator(this, pageManager);
    }

    public synchronized Record readRecord(RowPointer pointer) throws DBMSException {
        byte[] rowBytes = pageManager.loadPage(pointer.getPageId()).getRowByOffset(pointer.getRowOffset());
        return RowSerializer.deserialize(rowBytes, this);
    }

    public synchronized void createIndex(String indexName, String columnName) throws DBMSException {
        if (!hasColumn(columnName)) {
            throw new DBMSException("Column '" + columnName + "' does not exist.");
        }
        for (IndexDefinition index : indexes) {
            if (index.indexName.equals(indexName)) {
                throw new DBMSException("Index '" + indexName + "' already exists.");
            }
        }

        IndexDefinition index = new IndexDefinition(indexName, columnName);
        BPlusTreeDiskStore<String, RowPointer> indexStore = createIndexStore(indexName);
        indexStore.save(new BPlusTree<>(4));

        TableIterator tableIterator = iterator();
        while (tableIterator.hasNext()) {
            RowPointer pointer = tableIterator.nextPointer();
            Record record = tableIterator.next();
            indexStore.insert(indexKey(record.getValue(columnName)), pointer);
        }

        indexes.add(index);
        writeIndexDefinitions();
    }

    public synchronized List<Record> indexedRecordsFor(WhereClause whereClause) throws DBMSException {
        if (whereClause == null) {
            return null;
        }

        for (IndexDefinition index : candidateIndexes()) {
            IndexedLookup lookup = buildLookup(index.columnName, whereClause);
            if (lookup == null) {
                continue;
            }
            BPlusTreeDiskStore<String, RowPointer> store = index.primaryKey
                    ? primaryKeyIndex
                    : createIndexStore(index.indexName);
            List<RowPointer> pointers = lookup.equalityKey != null
                    ? store.search(lookup.equalityKey)
                    : store.searchRange(lookup.lowerKey, lookup.upperKey);
            List<Record> records = new ArrayList<>();
            for (RowPointer pointer : pointers) {
                records.add(readRecord(pointer));
            }
            return records;
        }

        return null;
    }

    public synchronized List<Record> orderedRecordsFor(String columnName, boolean ascending) throws DBMSException {
        BPlusTreeDiskStore<String, RowPointer> store = indexStoreForColumn(columnName);
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
        List<DBMSDataType> ans = new ArrayList<DBMSDataType>();
        for (int i = 0; i < columnList.size(); i++) {
            String columnName = columnList.get(i).getColumnName();
            ans.add(r.getValue(columnName));
        }
        return ans;
    }

    public void printColumns()
    {
        for (int i = 0; i < columnList.size(); i++) {
            Column column = columnList.get(i);
            System.out.print(column.getColumnName());
            if(i != columnList.size() - 1)
                System.out.print(", ");
        }
        System.out.println();
    }

    public static void writeSchema(String tableName, List<Column> schema) throws DBMSException {
        File dir = new File("data", tableName);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DBMSException("Could not create schema directory for table '" + tableName + "'.");
        }

        File file = new File(dir, "schema.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Column column : schema) {
                writer.write(column.getColumnName() + " " + column.getColumn_type());
                if (column.isPrimaryKey()) {
                    writer.write(" PRIMARY_KEY");
                }
                if (column.hasForeignKey()) {
                    writer.write(" REFERENCES " + column.getForeignTableName() + " " + column.getForeignColumnName());
                }
                writer.newLine();
            }
        } catch (IOException e) {
            throw new DBMSException("Could not write schema for table '" + tableName + "'.", e);
        }
    }

    private List<Column> loadSchema(File dir) throws DBMSException {
        File file = new File(dir, "schema.txt");
        if (!file.exists()) {
            throw new DBMSException("Schema file missing for table '" + table_name + "'.");
        }

        List<Column> columns = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length != 2 && parts.length != 3 && parts.length != 5 && parts.length != 6) {
                    throw new DBMSException("Invalid schema entry in table '" + table_name + "': " + line);
                }
                boolean primaryKey = false;
                String foreignTableName = null;
                String foreignColumnName = null;
                int index = 2;
                if (index < parts.length && parts[index].equals("PRIMARY_KEY")) {
                    primaryKey = true;
                    index++;
                }
                if (index < parts.length) {
                    if (index + 2 >= parts.length || !parts[index].equals("REFERENCES")) {
                        throw new DBMSException("Invalid schema entry in table '" + table_name + "': " + line);
                    }
                    foreignTableName = parts[index + 1];
                    foreignColumnName = parts[index + 2];
                }
                Column column = new Column(parts[0], parts[1], primaryKey);
                column.setForeignTableName(foreignTableName);
                column.setForeignColumnName(foreignColumnName);
                columns.add(column);
            }
        } catch (IOException e) {
            throw new DBMSException("Could not load schema for table '" + table_name + "'.", e);
        }

        return columns;
    }

    private void ensureTableDirectory() throws DBMSException {
        File dir = new File("data", table_name);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DBMSException("Could not create table directory for '" + table_name + "'.");
        }
    }

    private BPlusTreeDiskStore<String, RowPointer> createPrimaryKeyIndex(String tableName) {
        Path indexDirectory = Path.of("data", tableName, "primary_key_index");
        return new BPlusTreeDiskStore<>(
                indexDirectory,
                BPlusTreeSerializers.strings(),
                BPlusTreeSerializers.rowPointers()
        );
    }

    private BPlusTreeDiskStore<String, RowPointer> createIndexStore(String indexName) {
        Path indexDirectory = Path.of("data", table_name, "indexes", indexName);
        return new BPlusTreeDiskStore<>(
                indexDirectory,
                BPlusTreeSerializers.strings(),
                BPlusTreeSerializers.rowPointers()
        );
    }

    private void initializePrimaryKeyIndex() throws DBMSException {
        Path metadataPage = Path.of("data", table_name, "primary_key_index", "bptree_page_0.dat");
        if (!getPrimaryKeyColumns().isEmpty() && !Files.exists(metadataPage)) {
            primaryKeyIndex.save(new BPlusTree<>(4));
            TableIterator tableIterator = iterator();
            while (tableIterator.hasNext()) {
                RowPointer pointer = tableIterator.nextPointer();
                String primaryKey = primaryKeyForRecord(tableIterator.next());
                if (!primaryKeyIndex.search(primaryKey).isEmpty()) {
                    throw new DBMSException("Duplicate primary key value.");
                }
                primaryKeyIndex.insert(primaryKey, pointer);
            }
        }
    }

    private String primaryKeyForRecord(Record record) throws DBMSException {
        List<String> primaryKeyColumns = getPrimaryKeyColumns();
        if (primaryKeyColumns.isEmpty()) {
            return null;
        }
        if (primaryKeyColumns.size() == 1) {
            DBMSDataType value = record.getValue(primaryKeyColumns.get(0));
            if (value == null || value.toString().isEmpty()) {
                throw new DBMSException("Primary key column '" + primaryKeyColumns.get(0) + "' must be non-null and non-empty.");
            }
            return indexKey(value);
        }

        StringBuilder key = new StringBuilder();
        for (String primaryKeyColumn : primaryKeyColumns) {
            DBMSDataType value = record.getValue(primaryKeyColumn);
            if (value == null || value.toString().isEmpty()) {
                throw new DBMSException("Primary key column '" + primaryKeyColumn + "' must be non-null and non-empty.");
            }
            String encodedValue = value.getType() + ":" + value.toString();
            key.append(encodedValue.length()).append(':').append(encodedValue).append('|');
        }
        return key.toString();
    }

    private List<IndexDefinition> candidateIndexes() {
        List<IndexDefinition> candidates = new ArrayList<>();
        List<String> primaryKeyColumns = getPrimaryKeyColumns();
        if (primaryKeyColumns.size() == 1) {
            candidates.add(IndexDefinition.primary(primaryKeyColumns.get(0)));
        }
        candidates.addAll(indexes);
        return candidates;
    }

    private IndexedLookup buildLookup(String columnName, WhereClause whereClause) throws DBMSException {
        String equalityKey = null;
        String lowerKey = null;
        String upperKey = null;
        int inequalityCount = 0;

        for (UnaryCondition condition : whereClause.getConditions().getConditions()) {
            if (!condition.getColumnName().getName().equals(columnName)) {
                continue;
            }

            String key = indexKey(condition.getValue().evaluate());
            String symbol = condition.getOperator().getSymbol();
            if (symbol.equals("=")) {
                equalityKey = key;
            } else if (symbol.equals(">") || symbol.equals(">=")) {
                lowerKey = lowerKey == null || key.compareTo(lowerKey) > 0 ? key : lowerKey;
                inequalityCount++;
            } else if (symbol.equals("<") || symbol.equals("<=")) {
                upperKey = upperKey == null || key.compareTo(upperKey) < 0 ? key : upperKey;
                inequalityCount++;
            }
        }

        if (equalityKey != null) {
            return IndexedLookup.equality(equalityKey);
        }
        if (lowerKey != null && upperKey != null && inequalityCount >= 2) {
            return IndexedLookup.range(lowerKey, upperKey);
        }
        return null;
    }

    private String indexKey(DBMSDataType value) {
        if (value.getType().equals("INT")) {
            return String.format("%010d", Integer.parseInt(value.toString()));
        }
        return value.toString();
    }

    public boolean hasColumn(String columnName) {
        for (Column column : columnList) {
            if (column.getColumnName().equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsValue(String columnName, DBMSDataType expectedValue) throws DBMSException {
        TableIterator tableIterator = iterator();
        while (tableIterator.hasNext()) {
            Record record = tableIterator.next();
            if (record.getValue(columnName).equals(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    private void validateForeignKeyReferences(Record record, Catalog catalog) throws DBMSException {
        for (Column column : columnList) {
            if (!column.hasForeignKey()) {
                continue;
            }
            if (catalog == null) {
                throw new DBMSException("Foreign key validation requires catalog context.");
            }

            DBMSDataType value = record.getValue(column.getColumnName());
            Table referencedTable = catalog.getTable(column.getForeignTableName());
            if (!referencedTable.containsValue(column.getForeignColumnName(), value)) {
                throw new DBMSException("Foreign key violation on column '" + column.getColumnName()
                        + "': value '" + value + "' does not exist in "
                        + column.getForeignTableName() + "." + column.getForeignColumnName() + ".");
            }
        }
    }

    public boolean hasIndexOnColumn(String columnName) {
        return indexStoreForColumn(columnName) != null;
    }

    private BPlusTreeDiskStore<String, RowPointer> indexStoreForColumn(String columnName) {
        List<String> primaryKeyColumns = getPrimaryKeyColumns();
        if (primaryKeyColumns.size() == 1 && primaryKeyColumns.get(0).equals(columnName)) {
            return primaryKeyIndex;
        }
        for (IndexDefinition index : indexes) {
            if (index.columnName.equals(columnName)) {
                return createIndexStore(index.indexName);
            }
        }
        return null;
    }

    public int getRowCount() throws DBMSException {
        int rowCount = 0;
        TableIterator tableIterator = iterator();
        while (tableIterator.hasNext()) {
            tableIterator.next();
            rowCount++;
        }
        return rowCount;
    }

    private void loadIndexDefinitions() throws DBMSException {
        File file = new File(new File("data", table_name), "indexes.txt");
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length != 2) {
                    throw new DBMSException("Invalid index entry in table '" + table_name + "': " + line);
                }
                indexes.add(new IndexDefinition(parts[0], parts[1]));
            }
        } catch (IOException e) {
            throw new DBMSException("Could not load indexes for table '" + table_name + "'.", e);
        }
    }

    private void writeIndexDefinitions() throws DBMSException {
        File file = new File(new File("data", table_name), "indexes.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (IndexDefinition index : indexes) {
                writer.write(index.indexName + " " + index.columnName);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new DBMSException("Could not write indexes for table '" + table_name + "'.", e);
        }
    }

    private static final class IndexDefinition {
        private final String indexName;
        private final String columnName;
        private final boolean primaryKey;

        private IndexDefinition(String indexName, String columnName) {
            this(indexName, columnName, false);
        }

        private IndexDefinition(String indexName, String columnName, boolean primaryKey) {
            this.indexName = indexName;
            this.columnName = columnName;
            this.primaryKey = primaryKey;
        }

        private static IndexDefinition primary(String columnName) {
            return new IndexDefinition("__primary_key__", columnName, true);
        }
    }

    private static final class IndexedLookup {
        private final String equalityKey;
        private final String lowerKey;
        private final String upperKey;

        private IndexedLookup(String equalityKey, String lowerKey, String upperKey) {
            this.equalityKey = equalityKey;
            this.lowerKey = lowerKey;
            this.upperKey = upperKey;
        }

        private static IndexedLookup equality(String key) {
            return new IndexedLookup(key, null, null);
        }

        private static IndexedLookup range(String lowerKey, String upperKey) {
            return new IndexedLookup(null, lowerKey, upperKey);
        }
    }
}
