package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;
import dbmscli.result.QueryResultBlock;

public class CreateIndexStatement extends Statement {
    private Identifier indexName;
    private Identifier tableName;
    private Identifier columnName;

    public Identifier getIndexName() {
        return indexName;
    }

    public void setIndexName(Identifier indexName) {
        this.indexName = indexName;
    }

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public Identifier getColumnName() {
        return columnName;
    }

    public void setColumnName(Identifier columnName) {
        this.columnName = columnName;
    }

    @Override
    public QueryResultBlock execute(Catalog catalog) throws DBMSException {
        try {
            Table table = catalog.getTable(tableName.getName());
            table.createIndex(indexName.getName(), columnName.getName());
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
