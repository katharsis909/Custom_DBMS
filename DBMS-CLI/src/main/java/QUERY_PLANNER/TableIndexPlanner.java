package QUERY_PLANNER;

import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.WhereClause;
import STRUCTURE.DBMSException;
import STRUCTURE.IndexDefinition;
import STRUCTURE.table.utility.IndexKeyCodec;
import STRUCTURE.table.utility.TableIndexManager;
import STRUCTURE.table.utility.TableSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableIndexPlanner {
    private final TableSchema schema;
    private final TableIndexManager indexManager;

    public TableIndexPlanner(TableSchema schema, TableIndexManager indexManager) {
        this.schema = schema;
        this.indexManager = indexManager;
    }

    public IndexedAccess bestIndexedAccess(WhereClause whereClause) throws DBMSException {
        if (whereClause == null) {
            return null;
        }

        IndexDefinition selectedIndex = null;
        IndexedLookup selectedLookup = null;

        for (IndexDefinition index : indexManager.candidateIndexes(schema.getPrimaryKeyColumns())) {
            IndexedLookup lookup = buildLookup(index, whereClause);
            if (lookup == null || (selectedLookup != null && lookup.score <= selectedLookup.score)) {
                continue;
            }
            selectedIndex = index;
            selectedLookup = lookup;
        }

        if (selectedIndex == null || selectedLookup == null) {
            return null;
        }
        return new IndexedAccess(selectedIndex, selectedLookup);
    }

    public boolean hasUsableIndexForWhere(WhereClause whereClause) throws DBMSException {
        return bestIndexedAccess(whereClause) != null;
    }

    public boolean hasSingleIndexCoveringWhereColumns(WhereClause whereClause) throws DBMSException {
        Set<String> whereColumns = whereColumnNames(whereClause);
        if (whereColumns.isEmpty()) {
            return false;
        }
        for (IndexDefinition index : indexManager.candidateIndexes(schema.getPrimaryKeyColumns())) {
            if (leadingColumnsCover(index.columnNames, whereColumns)) {
                return buildLookup(index, whereClause) != null;
            }
        }
        return false;
    }

    public boolean hasIndexOnColumn(String columnName) {
        return hasIndexStartingWithColumns(List.of(columnName));
    }

    public boolean hasIndexStartingWithColumns(List<String> columnNames) {
        return indexManager.indexStoreForLeadingColumns(columnNames, schema.getPrimaryKeyColumns()) != null;
    }

    public int longestIndexPrefixForColumns(List<String> columnNames) {
        int longestPrefix = 0;
        for (IndexDefinition index : indexManager.candidateIndexes(schema.getPrimaryKeyColumns())) {
            longestPrefix = Math.max(longestPrefix, matchingLeadingColumns(index.columnNames, columnNames));
        }
        return longestPrefix;
    }

    public OrderIndexMatch findBestOrderIndexMatch(List<OrderByItem> orderByItems) {
        if (orderByItems == null || orderByItems.isEmpty()) {
            return OrderIndexMatch.none();
        }

        OrderIndexMatch bestMatch = OrderIndexMatch.none();

        for (IndexDefinition index : indexManager.candidateIndexes(schema.getPrimaryKeyColumns())) {
            int forwardCount = 0;
            int reverseCount = 0;
            int maxComparable = Math.min(index.columnNames.size(), orderByItems.size());

            // Check forward scan
            for (int i = 0; i < maxComparable; i++) {
                String reqCol = unqualifiedColumnName(orderByItems.get(i).getColumn().getColumnName().getName());
                boolean reqAsc = orderByItems.get(i).isAscending();
                String indexCol = index.columnNames.get(i);
                boolean indexAsc = index.isAscending(i);

                if (reqCol.equals(indexCol) && reqAsc == indexAsc) {
                    forwardCount++;
                } else {
                    break;
                }
            }

            // Check reverse scan (flips all index column directions)
            for (int i = 0; i < maxComparable; i++) {
                String reqCol = unqualifiedColumnName(orderByItems.get(i).getColumn().getColumnName().getName());
                boolean reqAsc = orderByItems.get(i).isAscending();
                String indexCol = index.columnNames.get(i);
                boolean indexAsc = index.isAscending(i);

                if (reqCol.equals(indexCol) && reqAsc == !indexAsc) {
                    reverseCount++;
                } else {
                    break;
                }
            }

            if (forwardCount >= reverseCount && forwardCount > bestMatch.prefixLength) {
                bestMatch = new OrderIndexMatch(index, forwardCount, true, forwardCount == orderByItems.size());
            } else if (reverseCount > forwardCount && reverseCount > bestMatch.prefixLength) {
                bestMatch = new OrderIndexMatch(index, reverseCount, false, reverseCount == orderByItems.size());
            }
        }

        return bestMatch;
    }

    private IndexedLookup buildLookup(IndexDefinition index, WhereClause whereClause) throws DBMSException {
        if (index.columnNames.size() == 1) {
            boolean asc = index.isAscending(0);
            ColumnBounds bounds = boundsForColumn(whereClause, index.columnNames.get(0));
            if (bounds.equalityKey != null) {
                String eqKey = asc ? bounds.equalityKey : IndexKeyCodec.invert(bounds.equalityKey);
                return IndexedLookup.equality(eqKey, 3);
            }
            if (bounds.lowerKey != null || bounds.upperKey != null) {
                String encLower = bounds.lowerKey == null ? null : (asc ? bounds.lowerKey : IndexKeyCodec.invert(bounds.lowerKey));
                String encUpper = bounds.upperKey == null ? null : (asc ? bounds.upperKey : IndexKeyCodec.invert(bounds.upperKey));
                if (!asc) {
                    String temp = encLower;
                    encLower = encUpper;
                    encUpper = temp;
                }
                String lowerKey = encLower == null ? "" : encLower;
                String upperKey = encUpper == null ? String.valueOf(Character.MAX_VALUE) : encUpper;
                return IndexedLookup.range(lowerKey, upperKey, 1);
            }
            return null;
        }

        List<String> equalityPrefix = new ArrayList<>();
        int equalityColumns = 0;

        for (int i = 0; i < index.columnNames.size(); i++) {
            String columnName = index.columnNames.get(i);
            boolean asc = index.isAscending(i);
            ColumnBounds bounds = boundsForColumn(whereClause, columnName);
            if (bounds.equalityKey != null) {
                String eqKey = asc ? bounds.equalityKey : IndexKeyCodec.invert(bounds.equalityKey);
                equalityPrefix.add(IndexKeyCodec.encodeIndexComponent(eqKey));
                equalityColumns++;
                continue;
            }

            if (bounds.lowerKey != null || bounds.upperKey != null) {
                String prefix = IndexKeyCodec.joinIndexPrefix(equalityPrefix);
                String encLower = bounds.lowerKey == null ? null : (asc ? bounds.lowerKey : IndexKeyCodec.invert(bounds.lowerKey));
                String encUpper = bounds.upperKey == null ? null : (asc ? bounds.upperKey : IndexKeyCodec.invert(bounds.upperKey));
                if (!asc) {
                    String temp = encLower;
                    encLower = encUpper;
                    encUpper = temp;
                }
                String lowerKey = encLower == null
                        ? prefix
                        : prefix + IndexKeyCodec.encodeIndexComponent(encLower);
                String upperKey = encUpper == null
                        ? prefix + Character.MAX_VALUE
                        : prefix + IndexKeyCodec.encodeIndexComponent(encUpper) + Character.MAX_VALUE;
                return IndexedLookup.range(lowerKey, upperKey, (equalityColumns * 2) + 1);
            }
            break;
        }

        if (equalityColumns == index.columnNames.size() && equalityColumns > 0) {
            return IndexedLookup.equality(IndexKeyCodec.joinIndexPrefix(equalityPrefix), (equalityColumns * 2) + 1);
        }

        if (equalityColumns > 0) {
            String prefix = IndexKeyCodec.joinIndexPrefix(equalityPrefix);
            return IndexedLookup.range(prefix, prefix + Character.MAX_VALUE, equalityColumns * 2);
        }
        return null;
    }

    private ColumnBounds boundsForColumn(WhereClause whereClause, String columnName) throws DBMSException {
        String equalityKey = null;
        String lowerKey = null;
        String upperKey = null;

        for (UnaryCondition condition : whereClause.getConditions().getConditions()) {
            if (!unqualifiedColumnName(condition.getColumnName().getName()).equals(columnName)) {
                continue;
            }

            String key = IndexKeyCodec.indexKey(condition.getValue().evaluate());
            String symbol = condition.getOperator().getSymbol();
            if (symbol.equals("=")) {
                equalityKey = key;
            } else if (symbol.equals(">") || symbol.equals(">=")) {
                lowerKey = lowerKey == null || key.compareTo(lowerKey) > 0 ? key : lowerKey;
            } else if (symbol.equals("<") || symbol.equals("<=")) {
                upperKey = upperKey == null || key.compareTo(upperKey) < 0 ? key : upperKey;
            }
        }

        return new ColumnBounds(equalityKey, lowerKey, upperKey);
    }

    private boolean leadingColumnsCover(List<String> candidateColumns, Set<String> requestedColumns) {
        if (requestedColumns.size() > candidateColumns.size()) {
            return false;
        }
        Set<String> leadingColumns = new HashSet<>();
        for (int i = 0; i < requestedColumns.size(); i++) {
            leadingColumns.add(candidateColumns.get(i));
        }
        return leadingColumns.containsAll(requestedColumns);
    }

    private int matchingLeadingColumns(List<String> candidateColumns, List<String> requestedColumns) {
        int maxComparableColumns = Math.min(candidateColumns.size(), requestedColumns.size());
        int matchingColumns = 0;
        while (matchingColumns < maxComparableColumns
                && candidateColumns.get(matchingColumns).equals(requestedColumns.get(matchingColumns))) {
            matchingColumns++;
        }
        return matchingColumns;
    }

    private Set<String> whereColumnNames(WhereClause whereClause) {
        Set<String> columnNames = new HashSet<>();
        if (whereClause == null || whereClause.getConditions() == null) {
            return columnNames;
        }
        for (UnaryCondition condition : whereClause.getConditions().getConditions()) {
            columnNames.add(unqualifiedColumnName(condition.getColumnName().getName()));
        }
        return columnNames;
    }

    private String unqualifiedColumnName(String name) {
        int dotIndex = name.indexOf('.');
        return dotIndex < 0 ? name : name.substring(dotIndex + 1);
    }
}
