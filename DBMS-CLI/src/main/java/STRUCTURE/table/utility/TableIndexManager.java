package STRUCTURE.table.utility;

import STRUCTURE.DBMSException;
import STRUCTURE.IndexDefinition;
import STRUCTURE.Record;
import STRUCTURE.table.Table;
import disk_persistence.RowPointer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableIndexManager {
    private final String tableName;
    private final BPlusTreeDiskStore<String, RowPointer> primaryKeyIndex;
    private final List<IndexDefinition> indexes = new ArrayList<>();

    public TableIndexManager(String tableName) {
        this.tableName = tableName;
        this.primaryKeyIndex = createPrimaryKeyIndex(tableName);
    }

    public BPlusTreeDiskStore<String, RowPointer> getPrimaryKeyIndex() {
        return primaryKeyIndex;
    }

    public List<IndexDefinition> getIndexes() {
        return new ArrayList<>(indexes);
    }

    public BPlusTreeDiskStore<String, RowPointer> createPrimaryKeyIndex(String tableName) {
        Path indexDirectory = Path.of("data", tableName, "primary_key_index");
        return new BPlusTreeDiskStore<>(
                indexDirectory,
                BPlusTreeSerializers.strings(),
                BPlusTreeSerializers.rowPointers()
        );
    }

    public BPlusTreeDiskStore<String, RowPointer> createIndexStore(String indexName) {
        Path indexDirectory = Path.of("data", tableName, "indexes", indexName);
        return new BPlusTreeDiskStore<>(
                indexDirectory,
                BPlusTreeSerializers.strings(),
                BPlusTreeSerializers.rowPointers()
        );
    }

    public void initializePrimaryKeyIndex(List<String> primaryKeyColumns, Table table) throws DBMSException {
        Path metadataPage = Path.of("data", tableName, "primary_key_index", "bptree_page_0.dat");
        if (!primaryKeyColumns.isEmpty() && !Files.exists(metadataPage)) {
            primaryKeyIndex.save(new BPlusTree<>(4));
            TableIterator tableIterator = table.iterator();
            while (tableIterator.hasNext()) {
                RowPointer pointer = tableIterator.nextPointer();
                Record record = tableIterator.next();
                String primaryKey = IndexKeyCodec.indexKey(record, primaryKeyColumns);
                if (!primaryKeyIndex.search(primaryKey).isEmpty()) {
                    throw new DBMSException("Duplicate primary key value.");
                }
                primaryKeyIndex.insert(primaryKey, pointer);
            }
        }
    }

    public void createIndex(String indexName, List<String> columnNames, TableSchema schema, Table table) throws DBMSException {
        List<Boolean> defaultDirections = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            defaultDirections.add(true);
        }
        createIndex(indexName, columnNames, defaultDirections, schema, table);
    }

    public void createIndex(String indexName, List<String> columnNames, List<Boolean> columnDirections, TableSchema schema, Table table) throws DBMSException {
        validateIndexColumns(columnNames, schema);
        for (IndexDefinition index : indexes) {
            if (index.indexName.equals(indexName)) {
                throw new DBMSException("Index '" + indexName + "' already exists.");
            }
        }

        IndexDefinition index = new IndexDefinition(indexName, columnNames, columnDirections);
        BPlusTreeDiskStore<String, RowPointer> indexStore = createIndexStore(indexName);
        indexStore.save(new BPlusTree<>(4));

        TableIterator tableIterator = table.iterator();
        while (tableIterator.hasNext()) {
            RowPointer pointer = tableIterator.nextPointer();
            Record record = tableIterator.next();
            indexStore.insert(IndexKeyCodec.indexKey(record, index.columnNames, index.columnDirections), pointer);
        }

        indexes.add(index);
        writeIndexDefinitions();
    }

    public void insertIndexEntries(Record record, RowPointer rowPointer, List<String> primaryKeyColumns) throws DBMSException {
        if (!primaryKeyColumns.isEmpty()) {
            String primaryKey = IndexKeyCodec.indexKey(record, primaryKeyColumns);
            primaryKeyIndex.insert(primaryKey, rowPointer);
        }
        for (IndexDefinition index : indexes) {
            createIndexStore(index.indexName).insert(IndexKeyCodec.indexKey(record, index.columnNames, index.columnDirections), rowPointer);
        }
    }

    public List<IndexDefinition> candidateIndexes(List<String> primaryKeyColumns) {
        List<IndexDefinition> candidates = new ArrayList<>();
        if (!primaryKeyColumns.isEmpty()) {
            candidates.add(IndexDefinition.primary(primaryKeyColumns));
        }
        candidates.addAll(indexes);
        return candidates;
    }

    public BPlusTreeDiskStore<String, RowPointer> indexStoreForLeadingColumns(List<String> columnNames, List<String> primaryKeyColumns) {
        for (IndexDefinition index : candidateIndexes(primaryKeyColumns)) {
            if (startsWithColumns(index.columnNames, columnNames)) {
                return index.primaryKey ? primaryKeyIndex : createIndexStore(index.indexName);
            }
        }
        return null;
    }

    public void loadIndexDefinitions() throws DBMSException {
        File file = new File(new File("data", tableName), "indexes.txt");
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
                    throw new DBMSException("Invalid index entry in table '" + tableName + "': " + line);
                }
                ParsedIndexSpec spec = parseIndexSpec(parts[1], line);
                indexes.add(new IndexDefinition(parts[0], spec.names, spec.directions));
            }
        } catch (IOException e) {
            throw new DBMSException("Could not load indexes for table '" + tableName + "'.", e);
        }
    }

    public void writeIndexDefinitions() throws DBMSException {
        File file = new File(new File("data", tableName), "indexes.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (IndexDefinition index : indexes) {
                List<String> specs = new ArrayList<>();
                for (int i = 0; i < index.columnNames.size(); i++) {
                    boolean asc = index.isAscending(i);
                    specs.add(index.columnNames.get(i) + (asc ? " ASC" : " DESC"));
                }
                writer.write(index.indexName + "\t" + String.join(",", specs));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new DBMSException("Could not write indexes for table '" + tableName + "'.", e);
        }
    }

    private void validateIndexColumns(List<String> columnNames, TableSchema schema) throws DBMSException {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new DBMSException("Index must reference at least one column.");
        }

        Set<String> seenColumns = new HashSet<>();
        for (String columnName : columnNames) {
            if (!schema.hasColumn(columnName)) {
                throw new DBMSException("Column '" + columnName + "' does not exist.");
            }
            if (!seenColumns.add(columnName)) {
                throw new DBMSException("Index columns must be unique.");
            }
        }
    }

    public static boolean startsWithColumns(List<String> candidateColumns, List<String> requestedColumns) {
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

    private static class ParsedIndexSpec {
        final List<String> names = new ArrayList<>();
        final List<Boolean> directions = new ArrayList<>();
    }

    private ParsedIndexSpec parseIndexSpec(String columns, String rawLine) throws DBMSException {
        ParsedIndexSpec spec = new ParsedIndexSpec();
        for (String column : columns.split(",")) {
            String trimmedColumn = column.trim();
            if (!trimmedColumn.isEmpty()) {
                String[] parts = trimmedColumn.split("\\s+");
                spec.names.add(parts[0]);
                boolean asc = true;
                if (parts.length > 1 && parts[1].equalsIgnoreCase("DESC")) {
                    asc = false;
                }
                spec.directions.add(asc);
            }
        }
        if (spec.names.isEmpty()) {
            throw new DBMSException("Invalid index entry in table '" + tableName + "': " + rawLine);
        }
        return spec;
    }
}
