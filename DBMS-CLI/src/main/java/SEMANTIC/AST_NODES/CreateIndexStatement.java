package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import STRUCTURE.table.Table;
import dbmscli.result.QueryResultBlock;

import java.util.ArrayList;
import java.util.List;

public class CreateIndexStatement extends Statement {
    private Identifier indexName;
    private Identifier tableName;
    private List<Identifier> columnNames = new ArrayList<>();
    private List<Boolean> columnDirections = new ArrayList<>(); // true = ASC, false = DESC

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

    public List<Identifier> getColumnNames() {
        return new ArrayList<>(columnNames);
    }

    public void setColumnNames(List<Identifier> columnNames) {
        this.columnNames = new ArrayList<>(columnNames);
    }

    public List<Boolean> getColumnDirections() {
        return new ArrayList<>(columnDirections);
    }

    public void setColumnDirections(List<Boolean> columnDirections) {
        this.columnDirections = new ArrayList<>(columnDirections);
    }

    @Override
    public QueryResultBlock execute(Catalog catalog) throws DBMSException {
        try {
            Table table = catalog.getTable(tableName.getName());
            List<String> indexColumns = new ArrayList<>();
            for (Identifier columnName : columnNames) {
                indexColumns.add(columnName.getName());
            }
            if (columnDirections == null || columnDirections.isEmpty()) {
                table.createIndex(indexName.getName(), indexColumns);
            } else {
                table.createIndex(indexName.getName(), indexColumns, columnDirections);
            }
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
