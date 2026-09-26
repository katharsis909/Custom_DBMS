package SEMANTIC.AST_NODES;

import STRUCTURE.table.Table;

public final class TableBinding {
    public final String tableName;
    public final String alias;
    public final Table table;

    public TableBinding(String tableName, String alias, Table table) {
        this.tableName = tableName;
        this.alias = alias;
        this.table = table;
    }
}
