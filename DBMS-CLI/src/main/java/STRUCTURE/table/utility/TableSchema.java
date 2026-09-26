package STRUCTURE.table.utility;

import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TableSchema {
    private final String tableName;
    private final List<Column> columnList;

    public TableSchema(String tableName, List<Column> schema) throws DBMSException {
        this.tableName = tableName;
        this.columnList = new ArrayList<>(schema);
        ensureTableDirectory();
        writeSchema(tableName, schema);
    }

    public TableSchema(String tableName) throws DBMSException {
        this.tableName = tableName;
        ensureTableDirectory();
        this.columnList = loadSchema(new File("data", tableName));
    }

    public String getTableName() {
        return tableName;
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

    public boolean hasColumn(String columnName) {
        for (Column column : columnList) {
            if (column.getColumnName().equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    public List<DBMSDataType> getValueFromRecord(Record r) throws DBMSException {
        List<DBMSDataType> ans = new ArrayList<>();
        for (int i = 0; i < columnList.size(); i++) {
            String columnName = columnList.get(i).getColumnName();
            ans.add(r.getValue(columnName));
        }
        return ans;
    }

    public void printColumns() {
        for (int i = 0; i < columnList.size(); i++) {
            Column column = columnList.get(i);
            System.out.print(column.getColumnName());
            if (i != columnList.size() - 1) {
                System.out.print(", ");
            }
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
            throw new DBMSException("Schema file missing for table '" + tableName + "'.");
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
                    throw new DBMSException("Invalid schema entry in table '" + tableName + "': " + line);
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
                        throw new DBMSException("Invalid schema entry in table '" + tableName + "': " + line);
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
            throw new DBMSException("Could not load schema for table '" + tableName + "'.", e);
        }

        return columns;
    }

    private void ensureTableDirectory() throws DBMSException {
        File dir = new File("data", tableName);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DBMSException("Could not create table directory for '" + tableName + "'.");
        }
    }
}
