package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.SelectedColumnList;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.table.Table;
import dbmscli.result.QueryResultBlock;

import java.util.ArrayList;
import java.util.List;

public class ResultProjectionEngine {

    public QueryResultBlock buildSingleTableResult(Table table, List<Record> filteredRecords, SelectedColumnList selectedColumnList) throws DBMSException {
        List<List<String>> rows = new ArrayList<>();
        for (Record record : filteredRecords) {
            addSelectedRow(table, rows, record, selectedColumnList);
        }
        List<String> headers = buildHeaders(selectedColumnList, table);
        return QueryResultBlock.table(headers, rows);
    }

    public void addSelectedRow(Table table, List<List<String>> rows, Record record, SelectedColumnList selectedColumnList) throws DBMSException {
        List<DBMSDataType> selectedValues = selectedColumnList.evaluate(record, table);
        List<String> row = new ArrayList<>();
        for (DBMSDataType value : selectedValues) {
            row.add(value.toString());
        }
        rows.add(row);
    }

    public List<String> buildHeaders(SelectedColumnList selectedColumnList, Table table) {
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
