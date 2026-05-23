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
            applyTableForeignKeys(convertedColumns);
            validateForeignKeys(db, convertedColumns);
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

    private void applyTableForeignKeys(List<Column> convertedColumns) throws DBMSException {
        for (ColumnDefinitionList.ForeignKeyDefinition foreignKey : getColumns().getForeignKeys()) {
            boolean found = false;
            for (Column column : convertedColumns) {
                if (column.getColumnName().equals(foreignKey.getColumnName())) {
                    column.setForeignTableName(foreignKey.getReferencedTableName());
                    column.setForeignColumnName(foreignKey.getReferencedColumnName());
                    found = true;
                }
            }
            if (!found) {
                throw new DBMSException("Foreign key column '" + foreignKey.getColumnName() + "' does not exist.");
            }
        }
    }

    private void validateForeignKeys(Catalog catalog, List<Column> convertedColumns) throws DBMSException {
        for (Column column : convertedColumns) {
            if (!column.hasForeignKey()) {
                continue;
            }
            STRUCTURE.Table referencedTable = catalog.getTable(column.getForeignTableName());
            boolean referencedColumnExists = false;
            for (Column referencedColumn : referencedTable.getColumnList()) {
                if (referencedColumn.getColumnName().equals(column.getForeignColumnName())) {
                    referencedColumnExists = true;
                }
            }
            if (!referencedColumnExists) {
                throw new DBMSException("Referenced column '" + column.getForeignColumnName()
                        + "' does not exist in table '" + column.getForeignTableName() + "'.");
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
