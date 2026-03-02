package SEMANTIC.AST_NODES;

import Converter.Main;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;

public class CreateTableStatement extends Statement {
    private Identifier tableName;
    private ColumnDefinitionList columns;

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public ColumnDefinitionList getColumns() {
        return columns;
    }

    public void setColumns(ColumnDefinitionList columns) {
        this.columns = columns;
    }

    public void evaluate(Catalog db) throws DBMSException {
        Table table = Main.toTable(this);
        db.addTable(table);

    }
}
