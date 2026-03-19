package STRUCTURE;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Catalog {
    private final Map<String, Table> tables;

    public Catalog() {
        this.tables = new HashMap<>();
        ensureDataDirectory();
        loadTables();
    }

    public void addTable(String tableName, java.util.List<Column> schema) throws DBMSException {
        String key = tableName.toUpperCase();
        if (tables.containsKey(key)) {
            throw new DBMSException("Table '" + key + "' already exists.");
        }

        Table table = new Table(tableName, schema);
        tables.put(key, table);
    }

    public Table getTable(String name) throws DBMSException
    {
        String key = name.toUpperCase();
        if (!tables.containsKey(key)) {
            throw new DBMSException("Table '" + key + "' does not exist.");
        }
        return tables.get(key);
    }

    public void dropTable(String name) throws DBMSException
    {
        String key = name.toUpperCase();
        Table table = tables.remove(key);
        if (table == null) {
            throw new DBMSException("Table '" + key + "' does not exist.");
        }

        deleteDirectory(new File("data", table.getTable_name()));
    }

    private void loadTables() {
        File dataDir = new File("data");
        File[] dirs = dataDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }

        for (File dir : dirs) {
            String tableName = dir.getName();
            try {
                tables.put(tableName.toUpperCase(), new Table(tableName));
            } catch (DBMSException ignored) {
                // Skip malformed table directories so one bad table does not prevent startup.
            }
        }
    }

    private void ensureDataDirectory() {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    private void deleteDirectory(File file) throws DBMSException {
        if (!file.exists()) {
            return;
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectory(child);
                } else if (!child.delete()) {
                    throw new DBMSException("Could not delete file '" + child.getPath() + "'.");
                }
            }
        }

        if (!file.delete()) {
            throw new DBMSException("Could not delete directory '" + file.getPath() + "'.");
        }
    }
}
