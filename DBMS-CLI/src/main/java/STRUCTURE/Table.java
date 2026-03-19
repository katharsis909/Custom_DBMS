package STRUCTURE;

import disk_persistence.PageManager;
import disk_persistence.RowSerializer;
import disk_persistence.TableIterator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Table {
    private final List<Column> columnList;
    private final String table_name;
    private final PageManager pageManager;

    public Table(String tableName, List<Column> schema) throws DBMSException {
        this.table_name = tableName;
        this.columnList = new ArrayList<>(schema);
        ensureTableDirectory();
        writeSchema(tableName, schema);
        this.pageManager = new PageManager(tableName);
    }

    public Table(String tableName) throws DBMSException {
        this.table_name = tableName;
        ensureTableDirectory();
        this.columnList = loadSchema(new File("data", tableName));
        this.pageManager = new PageManager(tableName);
    }

    public List<Column> getColumnList() {
        return new ArrayList<>(columnList);
    }

    public String getTable_name() {
        return table_name;
    }

    public void addRecord(List<DBMSDataType> values) throws DBMSException {
        if (values.size() != columnList.size()) {
            throw new DBMSException("Column count mismatch. Expected " + columnList.size() + " but got " + values.size());
        }

        Record record = new Record();
        for (int i = 0; i < values.size(); i++) {
            Column column = columnList.get(i);
            DBMSDataType value = values.get(i);
            if (!value.typeEquals(column.getColumn_type())) {
                throw new DBMSException("Type mismatch at column " + column.getColumnName()
                        + ". Expected " + column.getColumn_type() + " but got " + value.getType());
            }
            record.setValue(column.getColumnName(), value);
        }

        insertRecord(record);
    }

    public void insertRecord(Record record) throws DBMSException {
        byte[] rowBytes = RowSerializer.serialize(record, this);
        pageManager.insertRow(rowBytes);
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
                if (parts.length != 2) {
                    throw new DBMSException("Invalid schema entry in table '" + table_name + "': " + line);
                }
                columns.add(new Column(parts[0], parts[1]));
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
}
