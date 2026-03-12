package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;
import STRUCTURE.Record;
import dbmscli.result.QueryResultBlock;

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

    public QueryResultBlock execute(Catalog db) throws DBMSException {
        Table table = db.getTable(getTableName().getName());
        //FROM is executed before
        List<Record> allRecords = table.getRecordList();

        List<Record> filtered = new ArrayList<>();
        //validated records to be returned
        for (Record r : allRecords)
        {
            if (getWhereClause() == null || getWhereClause().evaluate(r))
            //First use Where; above also checked for "Where not present"
            {
                filtered.add(r);
            }
        }

        List<List<String>> rows = new ArrayList<>();
        for (Record r : filtered) {
            //Second, from the validated records, now select specific columns
            //No need to pass information of which columns to select, all info in the below nodes
            //Crazy design pattern!
            List<DBMSDataType> selectedValues = selectedColumnList.evaluate(r,table);
            List<String> row = new ArrayList<>();
            for (DBMSDataType value : selectedValues) {
                row.add(value.toString());
            }
            rows.add(row);
        }
        List<String> headers = buildHeaders(selectedColumnList, table);
        return QueryResultBlock.table(headers, rows);
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

}
