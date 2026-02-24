package STRUCTURE;

import java.util.HashMap;
import java.util.Map;

public class Catalog {
    private Map<String, Table> tables;

    public Catalog() {
        this.tables = new HashMap<>();
    }

    public void addTable(Table table) throws DBMSException {
        String table_name = table.getTable_name().toUpperCase();
        if (tables.containsKey(table_name)) {
            throw new DBMSException("Table '" + table_name + "' already exists.");
        }
        tables.put(table_name, table);
    }

    public Table getTable(String name) throws DBMSException
    {
        name = name.toUpperCase();
        if (!tables.containsKey(name)) {
            throw new DBMSException("Table '" + name + "' does not exist.");
        }
        return tables.get(name);
    }
    /*
    public boolean tableExists(String name) {
        return tables.containsKey(name);
    }

    public void listTables() {
        System.out.println("Tables:");
        if (tables.isEmpty()) {
            System.out.println(" - No tables found.");
        } else {
            for (String tableName : tables.keySet()) {
                System.out.println(" - " + tableName);
            }
        }
    }
    */
    public void dropTable(String name) throws DBMSException
    {
        name = name.toUpperCase();
        if (!tables.containsKey(name)) {
            throw new DBMSException("Table '" + name + "' does not exist.");
        }
        tables.remove(name);
    }
}
