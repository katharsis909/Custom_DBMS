package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;

public class DropTableStatement extends Statement
{
    private Identifier tableName;

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public void evaluate(Catalog catalog) throws DBMSException {
        String name = tableName.getName();
        //no ambiguities!
        catalog.dropTable(name);
    }
}

