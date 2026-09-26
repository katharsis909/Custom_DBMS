package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.AggregateFunction;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.GroupResult;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.SelectedColumnList;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RecordSortEngine {

    public void sortRecordsIfNeeded(List<Record> records, List<OrderByItem> orderByItems) {
        if (orderByItems.isEmpty()) {
            return;
        }
        records.sort(recordComparator(orderByItems));
    }

    public void sortGroupResultsIfNeeded(List<GroupResult> groupResults, List<OrderByItem> orderByItems) {
        sortGroupResultsIfNeeded(groupResults, orderByItems, null);
    }

    public void sortGroupResultsIfNeeded(
            List<GroupResult> groupResults,
            List<OrderByItem> orderByItems,
            SelectedColumnList selectedColumnList
    ) {
        if (orderByItems.isEmpty()) {
            return;
        }
        groupResults.sort((left, right) -> compareGroupResults(left, right, orderByItems, selectedColumnList));
    }

    public Comparator<Record> recordComparator(List<OrderByItem> orderByItems) {
        return (left, right) -> compareRecords(left, right, orderByItems);
    }

    public int compareRecords(Record left, Record right, List<OrderByItem> orderByItems) {
        for (OrderByItem item : orderByItems) {
            try {
                String targetName = item.getColumn().getColumnName().getName();
                String leftValue = getRecordValue(left, targetName);
                String rightValue = getRecordValue(right, targetName);
                int comparison = compareOrderValues(leftValue, rightValue);
                if (comparison != 0) {
                    return item.isAscending() ? comparison : -comparison;
                }
            } catch (DBMSException exception) {
                return 0;
            }
        }
        return 0;
    }

    private String getRecordValue(Record record, String targetName) throws DBMSException {
        try {
            return record.getValue(targetName).toString();
        } catch (DBMSException ignored) {
        }

        String unqual = unqualifiedColumnName(targetName);
        try {
            return record.getValue(unqual).toString();
        } catch (DBMSException ignored) {
        }

        for (Map.Entry<String, DBMSDataType> entry : record.getAllValues().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(targetName)
                    || unqualifiedColumnName(entry.getKey()).equalsIgnoreCase(unqual)) {
                return entry.getValue().toString();
            }
        }
        throw new DBMSException("Column '" + targetName + "' not found in record.");
    }

    public int compareGroupResults(
            GroupResult left,
            GroupResult right,
            List<OrderByItem> orderByItems,
            SelectedColumnList selectedColumnList
    ) {
        for (OrderByItem item : orderByItems) {
            String leftValue = evaluateGroupResultItem(left, item, selectedColumnList);
            String rightValue = evaluateGroupResultItem(right, item, selectedColumnList);
            int comparison = compareOrderValues(leftValue, rightValue);
            if (comparison != 0) {
                return item.isAscending() ? comparison : -comparison;
            }
        }
        return 0;
    }

    private String evaluateGroupResultItem(
            GroupResult groupResult,
            OrderByItem item,
            SelectedColumnList selectedColumnList
    ) {
        String targetName = item.getColumn().getColumnName().getName();
        String unqualTarget = unqualifiedColumnName(targetName);

        if (isInteger(targetName)) {
            int pos = Integer.parseInt(targetName) - 1;
            if (pos >= 0 && pos < groupResult.row.size()) {
                return groupResult.row.get(pos);
            }
        }

        if (selectedColumnList != null && selectedColumnList.getColumns() != null) {
            List<ColumnMention> columns = selectedColumnList.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i).getColumnName().getName();
                if (colName.equalsIgnoreCase(targetName) || unqualifiedColumnName(colName).equalsIgnoreCase(unqualTarget)) {
                    return groupResult.row.get(i);
                }
            }
        }

        if (selectedColumnList != null && selectedColumnList.getAggregateFunctions() != null) {
            List<AggregateFunction> aggregates = selectedColumnList.getAggregateFunctions();
            int baseOffset = (selectedColumnList.getColumns() == null) ? 0 : selectedColumnList.getColumns().size();
            for (int j = 0; j < aggregates.size(); j++) {
                AggregateFunction agg = aggregates.get(j);
                int rowIdx = baseOffset + j;
                if (rowIdx >= groupResult.row.size()) {
                    break;
                }
                if (agg.getAlias() != null && agg.getAlias().equalsIgnoreCase(targetName)) {
                    return groupResult.row.get(rowIdx);
                }
                String defaultHeader = "agg" + (j + 1);
                if (defaultHeader.equalsIgnoreCase(targetName)) {
                    return groupResult.row.get(rowIdx);
                }
                String aggExpr = aggExprString(agg);
                if (aggExpr.equalsIgnoreCase(targetName) || unqualifiedColumnName(aggExpr).equalsIgnoreCase(unqualTarget)) {
                    return groupResult.row.get(rowIdx);
                }
            }
        }

        try {
            return item.getColumn().evaluate(groupResult.representative).toString();
        } catch (DBMSException e) {
            return "";
        }
    }

    private String aggExprString(AggregateFunction agg) {
        if (agg.isCountAll()) {
            return "COUNT(*)";
        }
        String col = agg.getColumn() == null ? "*" : agg.getColumn().getColumnName().getName();
        return agg.getFunctionName() + "(" + col + ")";
    }

    private static String unqualifiedColumnName(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int compareOrderValues(String left, String right) {
        if (isNumeric(left) && isNumeric(right)) {
            return Double.compare(Double.parseDouble(left), Double.parseDouble(right));
        }
        return left.compareTo(right);
    }

    public static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
