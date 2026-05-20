package STRUCTURE;

import disk_persistence.PageManager;
import disk_persistence.RowPointer;
import disk_persistence.RowSerializer;
import disk_persistence.TableIterator;
import indexing.bplustree.BPlusTree;
import indexing.bplustree.BPlusTreeDiskStore;
import indexing.bplustree.BPlusTreeSerializers;

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

    public Table(String tableName, List<Column> schema) throws DBMSException {
        this.table_name = tableName;
        this.columnList = new ArrayList<>(schema);
        ensureTableDirectory();
        writeSchema(tableName, schema);
        this.pageManager = new PageManager(tableName);
        this.primaryKeyIndex = createPrimaryKeyIndex(tableName);
        initializePrimaryKeyIndex();
    }

    public Table(String tableName) throws DBMSException {
        this.table_name = tableName;
        ensureTableDirectory();
        this.columnList = loadSchema(new File("data", tableName));
        this.pageManager = new PageManager(tableName);
        this.primaryKeyIndex = createPrimaryKeyIndex(tableName);
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
        return rowPointer;
    }

    public TableIterator iterator() throws DBMSException {
        return new TableIterator(this, pageManager);
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
                if (parts.length != 2 && parts.length != 3) {
                    throw new DBMSException("Invalid schema entry in table '" + table_name + "': " + line);
                }
                boolean primaryKey = parts.length == 3 && parts[2].equals("PRIMARY_KEY");
                columns.add(new Column(parts[0], parts[1], primaryKey));
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

    private void initializePrimaryKeyIndex() throws DBMSException {
        Path metadataPage = Path.of("data", table_name, "primary_key_index", "bptree_page_0.dat");
        if (!getPrimaryKeyColumns().isEmpty() && !Files.exists(metadataPage)) {
            primaryKeyIndex.save(new BPlusTree<>(4));
            TableIterator tableIterator = iterator();
            while (tableIterator.hasNext()) {
                String primaryKey = primaryKeyForRecord(tableIterator.next());
                if (!primaryKeyIndex.search(primaryKey).isEmpty()) {
                    throw new DBMSException("Duplicate primary key value.");
                }
                primaryKeyIndex.insert(primaryKey, new RowPointer(0, 0));
            }
        }
    }

    private String primaryKeyForRecord(Record record) throws DBMSException {
        List<String> primaryKeyColumns = getPrimaryKeyColumns();
        if (primaryKeyColumns.isEmpty()) {
            return null;
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
}
