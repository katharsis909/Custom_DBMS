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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Table {
    private static final char INDEX_COMPONENT_SEPARATOR = '\u0000';

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
    public RowPointer addRecord(List<DBMSDataType> values) throws DBMSException {
        return addRecord(values, null);
    }

    public RowPointer addRecord(List<DBMSDataType> values, Catalog catalog) throws DBMSException {
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

    public RowPointer insertRecord(Record record) throws DBMSException {
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
            createIndexStore(index.indexName).insert(indexKey(record, index.columnNames), rowPointer);
        }
        return rowPointer;
    }

    public TableIterator iterator() throws DBMSException {
        return new TableIterator(this, pageManager);
    }

    public Record readRecord(RowPointer pointer) throws DBMSException {
        byte[] rowBytes = pageManager.loadPage(pointer.getPageId()).getRowByOffset(pointer.getRowOffset());
        return RowSerializer.deserialize(rowBytes, this);
    }

    public void createIndex(String indexName, String columnName) throws DBMSException {
        createIndex(indexName, List.of(columnName));
    }

    public void createIndex(String indexName, List<String> columnNames) throws DBMSException {
        validateIndexColumns(columnNames);
        for (IndexDefinition index : indexes) {
            if (index.indexName.equals(indexName)) {
                throw new DBMSException("Index '" + indexName + "' already exists.");
            }
        }

        IndexDefinition index = new IndexDefinition(indexName, columnNames);
        BPlusTreeDiskStore<String, RowPointer> indexStore = createIndexStore(indexName);
        indexStore.save(new BPlusTree<>(4));

        TableIterator tableIterator = iterator();
        while (tableIterator.hasNext()) {
            RowPointer pointer = tableIterator.nextPointer();
            Record record = tableIterator.next();
            indexStore.insert(indexKey(record, index.columnNames), pointer);
        }

        indexes.add(index);
        writeIndexDefinitions();
    }

    public List<Record> indexedRecordsFor(WhereClause whereClause) throws DBMSException {
        if (whereClause == null) {
            return null;
        }

        IndexDefinition selectedIndex = null;
        IndexedLookup selectedLookup = null;

        for (IndexDefinition index : candidateIndexes()) {
            IndexedLookup lookup = buildLookup(index, whereClause);
            if (lookup == null || (selectedLookup != null && lookup.score <= selectedLookup.score)) {
                continue;
            }
            selectedIndex = index;
            selectedLookup = lookup;
        }

        if (selectedIndex != null && selectedLookup != null) {
            BPlusTreeDiskStore<String, RowPointer> store = selectedIndex.primaryKey
                    ? primaryKeyIndex
                    : createIndexStore(selectedIndex.indexName);
            List<RowPointer> pointers = selectedLookup.equalityKey != null
                    ? store.search(selectedLookup.equalityKey)
                    : store.searchRange(selectedLookup.lowerKey, selectedLookup.upperKey);
            List<Record> records = new ArrayList<>();
            for (RowPointer pointer : pointers) {
                records.add(readRecord(pointer));
            }
            return records;
        }

        return null;
    }

    public List<Record> orderedRecordsFor(List<String> columnNames, boolean ascending) throws DBMSException {
        BPlusTreeDiskStore<String, RowPointer> store = indexStoreForLeadingColumns(columnNames);
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
        for (String primaryKeyColumn : primaryKeyColumns) {
            DBMSDataType value = record.getValue(primaryKeyColumn);
            if (value == null || value.toString().isEmpty()) {
                throw new DBMSException("Primary key column '" + primaryKeyColumn + "' must be non-null and non-empty.");
            }
        }
        return indexKey(record, primaryKeyColumns);
    }

    private List<IndexDefinition> candidateIndexes() {
        List<IndexDefinition> candidates = new ArrayList<>();
        List<String> primaryKeyColumns = getPrimaryKeyColumns();
        if (!primaryKeyColumns.isEmpty()) {
            candidates.add(IndexDefinition.primary(primaryKeyColumns));
        }
        candidates.addAll(indexes);
        return candidates;
    }

    private IndexedLookup buildLookup(IndexDefinition index, WhereClause whereClause) throws DBMSException {
        if (index.columnNames.size() == 1) {
            ColumnBounds bounds = boundsForColumn(whereClause, index.columnNames.get(0));
            if (bounds.equalityKey != null) {
                return IndexedLookup.equality(bounds.equalityKey, 3);
            }
            if (bounds.lowerKey != null || bounds.upperKey != null) {
                String lowerKey = bounds.lowerKey == null ? "" : bounds.lowerKey;
                String upperKey = bounds.upperKey == null ? String.valueOf(Character.MAX_VALUE) : bounds.upperKey;
                return IndexedLookup.range(lowerKey, upperKey, 1);
            }
            return null;
        }

        List<String> equalityPrefix = new ArrayList<>();
        int equalityColumns = 0;

        for (String columnName : index.columnNames) {
            ColumnBounds bounds = boundsForColumn(whereClause, columnName);
            if (bounds.equalityKey != null) {
                equalityPrefix.add(encodeIndexComponent(bounds.equalityKey));
                equalityColumns++;
                continue;
            }

            if (bounds.lowerKey != null || bounds.upperKey != null) {
                String prefix = joinIndexPrefix(equalityPrefix);
                String lowerKey = bounds.lowerKey == null
                        ? prefix
                        : prefix + encodeIndexComponent(bounds.lowerKey);
                String upperKey = bounds.upperKey == null
                        ? prefix + Character.MAX_VALUE
                        : prefix + encodeIndexComponent(bounds.upperKey) + Character.MAX_VALUE;
                return IndexedLookup.range(lowerKey, upperKey, (equalityColumns * 2) + 1);
            }
            break;
        }

        if (equalityColumns == index.columnNames.size() && equalityColumns > 0) {
            return IndexedLookup.equality(joinIndexPrefix(equalityPrefix), (equalityColumns * 2) + 1);
        }
        if (equalityColumns > 0) {
            String prefix = joinIndexPrefix(equalityPrefix);
            return IndexedLookup.range(prefix, prefix + Character.MAX_VALUE, equalityColumns * 2);
        }
        return null;
    }

    private String indexKey(Record record, List<String> columnNames) throws DBMSException {
        if (columnNames.size() == 1) {
            return indexKey(record.getValue(columnNames.get(0)));
        }

        StringBuilder key = new StringBuilder();
        for (String columnName : columnNames) {
            key.append(encodeIndexComponent(indexKey(record.getValue(columnName))));
        }
        return key.toString();
    }

    private String indexKey(DBMSDataType value) {
        if (value.getType().equals("INT")) {
            return String.format("%010d", Integer.parseInt(value.toString()));
        }
        return value.toString();
    }

    private String encodeIndexComponent(String value) {
        return value + INDEX_COMPONENT_SEPARATOR;
    }

    private String joinIndexPrefix(List<String> encodedComponents) {
        StringBuilder key = new StringBuilder();
        for (String encodedComponent : encodedComponents) {
            key.append(encodedComponent);
        }
        return key.toString();
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
        return hasIndexStartingWithColumns(List.of(columnName));
    }

    public boolean hasIndexStartingWithColumns(List<String> columnNames) {
        return indexStoreForLeadingColumns(columnNames) != null;
    }

    private BPlusTreeDiskStore<String, RowPointer> indexStoreForLeadingColumns(List<String> columnNames) {
        for (IndexDefinition index : candidateIndexes()) {
            if (startsWithColumns(index.columnNames, columnNames)) {
                return index.primaryKey ? primaryKeyIndex : createIndexStore(index.indexName);
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
                String[] parts = trimmed.split("\t", 2);
                if (parts.length != 2) {
                    throw new DBMSException("Invalid index entry in table '" + table_name + "': " + line);
                }
                indexes.add(new IndexDefinition(parts[0], parseIndexColumns(parts[1], line)));
            }
        } catch (IOException e) {
            throw new DBMSException("Could not load indexes for table '" + table_name + "'.", e);
        }
    }

    private void writeIndexDefinitions() throws DBMSException {
        File file = new File(new File("data", table_name), "indexes.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (IndexDefinition index : indexes) {
                writer.write(index.indexName + "\t" + String.join(",", index.columnNames));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new DBMSException("Could not write indexes for table '" + table_name + "'.", e);
        }
    }

    private static final class IndexDefinition {
        private final String indexName;
        private final List<String> columnNames;
        private final boolean primaryKey;

        private IndexDefinition(String indexName, List<String> columnNames) {
            this(indexName, columnNames, false);
        }

        private IndexDefinition(String indexName, List<String> columnNames, boolean primaryKey) {
            this.indexName = indexName;
            this.columnNames = new ArrayList<>(columnNames);
            this.primaryKey = primaryKey;
        }

        private static IndexDefinition primary(List<String> columnNames) {
            return new IndexDefinition("__primary_key__", columnNames, true);
        }
    }

    private static final class IndexedLookup {
        private final String equalityKey;
        private final String lowerKey;
        private final String upperKey;
        private final int score;

        private IndexedLookup(String equalityKey, String lowerKey, String upperKey, int score) {
            this.equalityKey = equalityKey;
            this.lowerKey = lowerKey;
            this.upperKey = upperKey;
            this.score = score;
        }

        private static IndexedLookup equality(String key, int score) {
            return new IndexedLookup(key, null, null, score);
        }

        private static IndexedLookup range(String lowerKey, String upperKey, int score) {
            return new IndexedLookup(null, lowerKey, upperKey, score);
        }
    }

    private ColumnBounds boundsForColumn(WhereClause whereClause, String columnName) throws DBMSException {
        String equalityKey = null;
        String lowerKey = null;
        String upperKey = null;

        for (UnaryCondition condition : whereClause.getConditions().getConditions()) {
            if (!unqualifiedColumnName(condition.getColumnName().getName()).equals(columnName)) {
                continue;
            }

            String key = indexKey(condition.getValue().evaluate());
            String symbol = condition.getOperator().getSymbol();
            if (symbol.equals("=")) {
                equalityKey = key;
            } else if (symbol.equals(">") || symbol.equals(">=")) {
                lowerKey = lowerKey == null || key.compareTo(lowerKey) > 0 ? key : lowerKey;
            } else if (symbol.equals("<") || symbol.equals("<=")) {
                upperKey = upperKey == null || key.compareTo(upperKey) < 0 ? key : upperKey;
            }
        }

        return new ColumnBounds(equalityKey, lowerKey, upperKey);
    }

    private void validateIndexColumns(List<String> columnNames) throws DBMSException {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new DBMSException("Index must reference at least one column.");
        }

        Set<String> seenColumns = new HashSet<>();
        for (String columnName : columnNames) {
            if (!hasColumn(columnName)) {
                throw new DBMSException("Column '" + columnName + "' does not exist.");
            }
            if (!seenColumns.add(columnName)) {
                throw new DBMSException("Index columns must be unique.");
            }
        }
    }

    private boolean startsWithColumns(List<String> candidateColumns, List<String> requestedColumns) {
        if (requestedColumns.isEmpty() || requestedColumns.size() > candidateColumns.size()) {
            return false;
        }
        for (int i = 0; i < requestedColumns.size(); i++) {
            if (!candidateColumns.get(i).equals(requestedColumns.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<String> parseIndexColumns(String columns, String rawLine) throws DBMSException {
        List<String> parsedColumns = new ArrayList<>();
        for (String column : columns.split(",")) {
            String trimmedColumn = column.trim();
            if (!trimmedColumn.isEmpty()) {
                parsedColumns.add(trimmedColumn);
            }
        }
        if (parsedColumns.isEmpty()) {
            throw new DBMSException("Invalid index entry in table '" + table_name + "': " + rawLine);
        }
        return parsedColumns;
    }

    private String unqualifiedColumnName(String name) {
        int dotIndex = name.indexOf('.');
        return dotIndex < 0 ? name : name.substring(dotIndex + 1);
    }

    private static final class ColumnBounds {
        private final String equalityKey;
        private final String lowerKey;
        private final String upperKey;

        private ColumnBounds(String equalityKey, String lowerKey, String upperKey) {
            this.equalityKey = equalityKey;
            this.lowerKey = lowerKey;
            this.upperKey = upperKey;
        }
    }
}
