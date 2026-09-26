package SEMANTIC.AST_NODES.select.utility;

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
        int orderIndexPrefixLength = usableOrderIndexPrefixLength(table, orderByItems, isGroupedQuery);
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

    public int usableOrderIndexPrefixLength(Table table, List<OrderByItem> orderByItems, boolean isGroupedQuery) {
        if (orderByItems.isEmpty() || isGroupedQuery || !hasUniformOrderDirection(orderByItems)) {
            return 0;
        }
        return table.longestIndexPrefixForColumns(orderByColumnNames(orderByItems));
    }

    public List<String> orderByColumnNames(List<OrderByItem> orderByItems) {
        List<String> columnNames = new ArrayList<>();
        for (OrderByItem item : orderByItems) {
            columnNames.add(unqualifiedColumnName(item.getColumn().getColumnName().getName()));
        }
        return columnNames;
    }

    public boolean hasUniformOrderDirection(List<OrderByItem> orderByItems) {
        if (orderByItems.isEmpty()) {
            return true;
        }
        boolean ascending = orderByItems.get(0).isAscending();
        for (OrderByItem item : orderByItems) {
            if (item.isAscending() != ascending) {
                return false;
            }
        }
        return true;
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
