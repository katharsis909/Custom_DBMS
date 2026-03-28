package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import dbmscli.result.QueryResultBlock;

public class DropTableStatement extends Statement
{
    private Identifier tableName;

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    // Executes DROP TABLE by removing the named table from the catalog and underlying storage.
    public QueryResultBlock execute(Catalog catalog) throws DBMSException {
        try {
            String name = tableName.getName();
            //no ambiguities!
            catalog.dropTable(name);
        } catch (DBMSException exception) {
            throw attachPosition(exception, getSourcePosition());
        }
        return null;
    }

    private DBMSException attachPosition(DBMSException exception, int position) {
        if (exception.getPosition() != null) {
            return exception;
        }
        return new DBMSException(exception.getMessage(), position);
    }
}
