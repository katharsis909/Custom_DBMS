package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.GroupResult;
import SEMANTIC.AST_NODES.OrderByItem;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.util.Comparator;
import java.util.List;

public class RecordSortEngine {

    public void sortRecordsIfNeeded(List<Record> records, List<OrderByItem> orderByItems) {
        if (orderByItems.isEmpty()) {
            return;
        }
        records.sort(recordComparator(orderByItems));
    }

    public void sortGroupResultsIfNeeded(List<GroupResult> groupResults, List<OrderByItem> orderByItems) {
        if (orderByItems.isEmpty()) {
            return;
        }
        groupResults.sort((left, right) -> compareRecords(left.representative, right.representative, orderByItems));
    }

    public Comparator<Record> recordComparator(List<OrderByItem> orderByItems) {
        return (left, right) -> compareRecords(left, right, orderByItems);
    }

    public int compareRecords(Record left, Record right, List<OrderByItem> orderByItems) {
        for (OrderByItem item : orderByItems) {
            try {
                String leftValue = item.getColumn().evaluate(left).toString();
                String rightValue = item.getColumn().evaluate(right).toString();
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
