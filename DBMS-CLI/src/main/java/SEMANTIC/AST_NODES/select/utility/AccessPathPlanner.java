package SEMANTIC.AST_NODES.select.utility;

import QUERY_PLANNER.OrderIndexMatch;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.SingleTableAccessPath;
import SEMANTIC.AST_NODES.WhereClause;
import STRUCTURE.DBMSException;
import STRUCTURE.table.Table;

import java.util.ArrayList;
import java.util.List;

public class AccessPathPlanner {

    public SingleTableAccessPath chooseSingleTableAccessPath(
            Table table,
            WhereClause whereClause,
            List<OrderByItem> orderByItems,
            boolean isGroupedQuery
    ) throws DBMSException {
        boolean hasWhereIndexPath = table.hasUsableIndexForWhere(whereClause);
        boolean whereIndexCoversAllWhereColumns = table.hasSingleIndexCoveringWhereColumns(whereClause);

        OrderIndexMatch orderMatch = findOrderIndexMatch(table, orderByItems, isGroupedQuery);
        int orderIndexPrefixLength = orderMatch.prefixLength;
        boolean hasOrderIndexPath = orderIndexPrefixLength > 0;

        if (!hasOrderIndexPath) {
            return hasWhereIndexPath ? SingleTableAccessPath.whereIndex() : SingleTableAccessPath.tableScan();
        }

        if (hasMultipleOrderConditions(orderByItems)) {
            return orderIndexPrefixLength > 1
                    ? SingleTableAccessPath.orderIndex(orderIndexPrefixLength)
                    : (hasWhereIndexPath ? SingleTableAccessPath.whereIndex() : SingleTableAccessPath.tableScan());
        }

        if (hasMultipleWhereConditions(whereClause) && whereIndexCoversAllWhereColumns) {
            return SingleTableAccessPath.whereIndex();
        }

        return SingleTableAccessPath.orderIndex(orderIndexPrefixLength);
    }

    public OrderIndexMatch findOrderIndexMatch(Table table, List<OrderByItem> orderByItems, boolean isGroupedQuery) {
        if (orderByItems.isEmpty() || isGroupedQuery) {
            return OrderIndexMatch.none();
        }
        OrderIndexMatch match = table.findBestOrderIndexMatch(orderByItems);
        if (match != null && match.prefixLength > 0) {
            return match;
        }

        List<String> orderCols = orderByColumnNames(orderByItems);
        int prefixLen = table.longestIndexPrefixForColumns(orderCols);
        if (prefixLen > 0) {
            boolean scanAsc = orderByItems.get(0).isAscending();
            boolean complete = prefixLen == orderByItems.size();
            return new OrderIndexMatch(null, prefixLen, scanAsc, complete);
        }

        return match != null ? match : OrderIndexMatch.none();
    }

    public int usableOrderIndexPrefixLength(Table table, List<OrderByItem> orderByItems, boolean isGroupedQuery) {
        return findOrderIndexMatch(table, orderByItems, isGroupedQuery).prefixLength;
    }

    public List<String> orderByColumnNames(List<OrderByItem> orderByItems) {
        List<String> columnNames = new ArrayList<>();
        for (OrderByItem item : orderByItems) {
            columnNames.add(unqualifiedColumnName(item.getColumn().getColumnName().getName()));
        }
        return columnNames;
    }

    public boolean hasMultipleOrderConditions(List<OrderByItem> orderByItems) {
        return orderByItems.size() > 1;
    }

    public boolean hasMultipleWhereConditions(WhereClause whereClause) {
        return whereClause != null
                && whereClause.getConditions() != null
                && whereClause.getConditions().getConditions().size() > 1;
    }

    private String unqualifiedColumnName(String name) {
        int dotIndex = name.indexOf('.');
        return dotIndex < 0 ? name : name.substring(dotIndex + 1);
    }
}
