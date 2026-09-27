package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.JoinClause;
import SEMANTIC.AST_NODES.SelectedColumnList;
import SEMANTIC.AST_NODES.TableBinding;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import disk_persistence.TableIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import SEMANTIC.AST_NODES.WhereClause;

public class JoinExecutor {

    public Iterator<Record> joinedRecordIterator(
            List<TableBinding> bindings,
            List<JoinClause> joins,
            WhereClause whereClause
    ) {
        return new JoinedRecordIterator(bindings, joins, whereClause);
    }

    public void buildJoinedRecords(
            List<TableBinding> bindings,
            int bindingIndex,
            Record current,
            List<Record> results
    ) throws DBMSException {
        if (bindingIndex == bindings.size()) {
            results.add(current);
            return;
        }

        TableBinding binding = bindings.get(bindingIndex);
        TableIterator iterator = binding.table.iterator();
        while (iterator.hasNext()) {
            Record next = copyRecord(current);
            addTableRecord(next, binding, iterator.next());
            buildJoinedRecords(bindings, bindingIndex + 1, next, results);
        }
    }

    public boolean joinsMatch(Record record, List<JoinClause> joins) throws DBMSException {
        for (JoinClause join : joins) {
            if (!join.evaluate(record)) {
                return false;
            }
        }
        return true;
    }

    public List<String> buildJoinHeaders(List<TableBinding> bindings, SelectedColumnList selectedColumnList) {
        List<String> headers = new ArrayList<>();
        if (!selectedColumnList.isSelectAll()) {
            for (ColumnMention column : selectedColumnList.getColumns()) {
                headers.add(column.getColumnName().getName());
            }
            return headers;
        }
        for (TableBinding binding : bindings) {
            for (Column column : binding.table.getColumnList()) {
                headers.add(binding.tableName + "." + column.getColumnName());
            }
        }
        return headers;
    }

    public void addJoinedSelectedRow(
            List<TableBinding> bindings,
            List<List<String>> rows,
            Record record,
            SelectedColumnList selectedColumnList,
            ResultProjectionEngine projectionEngine
    ) throws DBMSException {
        if (selectedColumnList.isSelectAll()) {
            List<String> row = new ArrayList<>();
            for (TableBinding binding : bindings) {
                for (Column column : binding.table.getColumnList()) {
                    row.add(record.getValue(binding.tableName + "." + column.getColumnName()).toString());
                }
            }
            rows.add(row);
            return;
        }
        projectionEngine.addSelectedRow(null, rows, record, selectedColumnList);
    }

    public Record copyRecord(Record record) {
        Record copy = new Record();
        for (Map.Entry<String, DBMSDataType> entry : record.getAllValues().entrySet()) {
            copy.setValue(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    public void addTableRecord(Record target, TableBinding binding, Record source) throws DBMSException {
        for (Column column : binding.table.getColumnList()) {
            DBMSDataType value = source.getValue(column.getColumnName());
            target.setValue(binding.tableName + "." + column.getColumnName(), value);
            target.setValue(binding.alias + "." + column.getColumnName(), value);
            target.setValue(column.getColumnName(), value);
        }
    }
}
