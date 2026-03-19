package SEMANTIC.AST_NODES;

import Converter.Main;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import dbmscli.result.QueryResultBlock;

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

    public QueryResultBlock execute(Catalog db) throws DBMSException {
        try {
            db.addTable(getTableName().getName(), Main.toColumnListFromDefinition(getColumns().getColumnList()));
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
