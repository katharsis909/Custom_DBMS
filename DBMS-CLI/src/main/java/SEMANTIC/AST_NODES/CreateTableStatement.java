package SEMANTIC.AST_NODES;

import Converter.Main;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.Column;
import STRUCTURE.DBMSException;
import dbmscli.result.QueryResultBlock;

import java.util.ArrayList;
import java.util.List;

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

    // Executes CREATE TABLE by converting AST column definitions into schema columns and registering the table in the catalog.
    public QueryResultBlock execute(Catalog db) throws DBMSException {
        try {
            List<Column> convertedColumns = Main.toColumnListFromDefinition(getColumns().getColumnList());
            applyTablePrimaryKeyColumns(convertedColumns);
            db.addTable(getTableName().getName(), convertedColumns);
        } catch (DBMSException exception) {
            throw attachPosition(exception, getSourcePosition());
        }
        return null;
    }

    private void applyTablePrimaryKeyColumns(List<Column> convertedColumns) throws DBMSException {
        List<String> primaryKeyColumns = getColumns().getPrimaryKeyColumns();
        if (primaryKeyColumns.isEmpty()) {
            return;
        }

        List<String> knownColumns = new ArrayList<>();
        for (Column column : convertedColumns) {
            knownColumns.add(column.getColumnName());
        }

        List<String> seenPrimaryKeyColumns = new ArrayList<>();
        for (String primaryKeyColumn : primaryKeyColumns) {
            if (!knownColumns.contains(primaryKeyColumn)) {
                throw new DBMSException("Primary key column '" + primaryKeyColumn + "' does not exist.");
            }
            if (seenPrimaryKeyColumns.contains(primaryKeyColumn)) {
                throw new DBMSException("Primary key column '" + primaryKeyColumn + "' is repeated.");
            }
            seenPrimaryKeyColumns.add(primaryKeyColumn);
            for (Column column : convertedColumns) {
                if (column.getColumnName().equals(primaryKeyColumn)) {
                    column.setPrimaryKey(true);
                }
            }
        }
    }

    private DBMSException attachPosition(DBMSException exception, int position) {
        if (exception.getPosition() != null) {
            return exception;
        }
        return new DBMSException(exception.getMessage(), position);
    }
}
