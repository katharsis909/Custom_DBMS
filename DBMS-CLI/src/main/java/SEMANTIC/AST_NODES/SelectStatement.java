package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.Table;
import dbmscli.result.QueryResultBlock;
import disk_persistence.TableIterator;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement extends Statement {
    private SelectedColumnList selectedColumnList;
    private Identifier tableName;
    private WhereClause whereClause; // can be null

    public SelectedColumnList getSelectedColumnList() {
        return selectedColumnList;
    }

    public void setSelectedColumnList(SelectedColumnList selectedColumnList) {
        this.selectedColumnList = selectedColumnList;
    }

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public WhereClause getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(WhereClause whereClause) {
        this.whereClause = whereClause;
    }

    // Executes SELECT by scanning the table, applying the optional WHERE clause, and returning the matching rows as a result table.
    public QueryResultBlock execute(Catalog db) throws DBMSException {
        try {
            Table table = db.getTable(getTableName().getName());
            TableIterator iterator = table.iterator();

            List<List<String>> rows = new ArrayList<>();
            while (iterator.hasNext()) {
                Record record = iterator.next();
                if (getWhereClause() == null || getWhereClause().evaluate(record)) {
                    List<DBMSDataType> selectedValues = selectedColumnList.evaluate(record, table);
                    List<String> row = new ArrayList<>();
                    for (DBMSDataType value : selectedValues) {
                        row.add(value.toString());
                    }
                    rows.add(row);
                }
            }

            List<String> headers = buildHeaders(selectedColumnList, table);
            return QueryResultBlock.table(headers, rows);
        } catch (DBMSException exception) {
            throw attachPosition(exception, getSourcePosition());
        }
    }

    private List<String> buildHeaders(SelectedColumnList selectedColumnList, Table table) {
        List<String> headers = new ArrayList<>();
        if (selectedColumnList.getColumns() == null) {
            for (int i = 0; i < table.getColumnList().size(); i++) {
                headers.add(table.getColumnList().get(i).getColumnName());
            }
            return headers;
        }

        for (int i = 0; i < selectedColumnList.getColumns().size(); i++) {
            headers.add(selectedColumnList.getColumns().get(i).getColumnName().getName());
        }
        return headers;
    }

    private DBMSException attachPosition(DBMSException exception, int position) {
        if (exception.getPosition() != null) {
            return exception;
        }
        return new DBMSException(exception.getMessage(), position);
    }
}
