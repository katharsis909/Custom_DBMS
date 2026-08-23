package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.Table;
import dbmscli.result.QueryResultBlock;
import disk_persistence.TableIterator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

public class SelectStatement extends Statement {
    private SelectedColumnList selectedColumnList;
    private Identifier tableName;
    private Identifier tableAlias;
    private List<JoinClause> joins = new ArrayList<>();
    private WhereClause whereClause; // can be null
    private List<ColumnMention> groupByColumns = new ArrayList<>();
    private List<HavingCondition> havingConditions = new ArrayList<>();
    private List<OrderByItem> orderByItems = new ArrayList<>();

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

    public Identifier getTableAlias() {
        return tableAlias;
    }

    public void setTableAlias(Identifier tableAlias) {
        this.tableAlias = tableAlias;
    }

    public List<JoinClause> getJoins() {
        return new ArrayList<>(joins);
    }

    public void setJoins(List<JoinClause> joins) {
        this.joins = new ArrayList<>(joins);
    }

    public WhereClause getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(WhereClause whereClause) {
        this.whereClause = whereClause;
    }

    public List<ColumnMention> getGroupByColumns() {
        return new ArrayList<>(groupByColumns);
    }

    public void setGroupByColumns(List<ColumnMention> groupByColumns) {
        this.groupByColumns = new ArrayList<>(groupByColumns);
    }

    public List<HavingCondition> getHavingConditions() {
        return new ArrayList<>(havingConditions);
    }

    public void setHavingConditions(List<HavingCondition> havingConditions) {
        this.havingConditions = new ArrayList<>(havingConditions);
    }

    public List<OrderByItem> getOrderByItems() {
        return new ArrayList<>(orderByItems);
    }

    public void setOrderByItems(List<OrderByItem> orderByItems) {
        this.orderByItems = new ArrayList<>(orderByItems);
    }

    // Executes SELECT by scanning the table, applying the optional WHERE clause, and returning the matching rows as a result table.
    public QueryResultBlock execute(Catalog db) throws DBMSException {
        try {
            Table table = db.getTable(getTableName().getName());
            if (!joins.isEmpty()) {
                return executeJoin(db, table);
            }
            List<Record> indexedRecords = chooseSingleTableAccessPath(table);

            List<List<String>> rows = new ArrayList<>();
            List<Record> filteredRecords = new ArrayList<>();
            if (indexedRecords != null) {
                for (Record record : indexedRecords) {
                    if (matchesWhere(record)) {
                        filteredRecords.add(record);
                    }
                }
            } else {
                TableIterator iterator = table.iterator();
                while (iterator.hasNext()) {
                    Record record = iterator.next();
                    if (matchesWhere(record)) {
                        filteredRecords.add(record);
                    }
                }
            }

            if (isGroupedQuery()) {
                return executeGrouped(filteredRecords);
            }
            sortRecordsIfNeeded(filteredRecords);
            for (Record record : filteredRecords) {
                addSelectedRow(table, rows, record);
            }

            List<String> headers = buildHeaders(selectedColumnList, table);
            return QueryResultBlock.table(headers, rows);
        } catch (DBMSException exception) {
            throw attachPosition(exception, getSourcePosition());
        }
    }

    private QueryResultBlock executeJoin(Catalog catalog, Table baseTable) throws DBMSException {
        List<TableBinding> bindings = buildOptimizedJoinOrder(catalog, baseTable);
        List<Record> combinedRecords = new ArrayList<>();
        buildJoinedRecords(bindings, 0, new Record(), combinedRecords);

        List<List<String>> rows = new ArrayList<>();
        List<Record> filteredRecords = new ArrayList<>();
        for (Record record : combinedRecords) {
            if (joinsMatch(record) && matchesWhere(record)) {
                filteredRecords.add(record);
            }
        }

        if (isGroupedQuery()) {
            return executeGrouped(filteredRecords);
        }
        sortRecordsIfNeeded(filteredRecords);
        for (Record record : filteredRecords) {
            if (selectedColumnList.isSelectAll()) {
                rows.add(joinSelectAllRow(bindings, record));
            } else {
                addSelectedRow(null, rows, record);
            }
        }

        return QueryResultBlock.table(buildJoinHeaders(bindings), rows);
    }

    private List<TableBinding> buildOptimizedJoinOrder(Catalog catalog, Table baseTable) throws DBMSException {
        List<TableBinding> bindings = new ArrayList<>();
        bindings.add(new TableBinding(tableName.getName(), aliasOrName(tableName, tableAlias), baseTable));
        for (JoinClause join : joins) {
            bindings.add(new TableBinding(
                    join.getTableName().getName(),
                    aliasOrName(join.getTableName(), join.getAlias()),
                    catalog.getTable(join.getTableName().getName())
            ));
        }

        bindings.sort((left, right) -> {
            try {
                return Integer.compare(joinCost(left), joinCost(right));
            } catch (DBMSException exception) {
                return 0;
            }
        });
        return bindings;
    }

    private int joinCost(TableBinding binding) throws DBMSException {
        int cost = binding.table.getRowCount();
        for (JoinClause join : joins) {
            if (tableHasIndexedJoinColumn(binding, join.getLeftColumn())
                    || tableHasIndexedJoinColumn(binding, join.getRightColumn())) {
                cost -= 1000;
            }
        }
        return cost;
    }

    private boolean tableHasIndexedJoinColumn(TableBinding binding, ColumnMention columnMention) {
        String name = columnMention.getColumnName().getName();
        String prefix = binding.tableName + ".";
        String aliasPrefix = binding.alias + ".";
        if (name.startsWith(prefix)) {
            return binding.table.hasIndexOnColumn(name.substring(prefix.length()));
        }
        if (name.startsWith(aliasPrefix)) {
            return binding.table.hasIndexOnColumn(name.substring(aliasPrefix.length()));
        }
        return binding.table.hasIndexOnColumn(name);
    }

    private void buildJoinedRecords(
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

    private Record copyRecord(Record record) {
        Record copy = new Record();
        for (java.util.Map.Entry<String, DBMSDataType> entry : record.getAllValues().entrySet()) {
            copy.setValue(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private void addTableRecord(Record target, TableBinding binding, Record source) throws DBMSException {
        for (Column column : binding.table.getColumnList()) {
            DBMSDataType value = source.getValue(column.getColumnName());
            target.setValue(binding.tableName + "." + column.getColumnName(), value);
            target.setValue(binding.alias + "." + column.getColumnName(), value);
            target.setValue(column.getColumnName(), value);
        }
    }

    private boolean joinsMatch(Record record) throws DBMSException {
        for (JoinClause join : joins) {
            if (!join.evaluate(record)) {
                return false;
            }
        }
        return true;
    }

    private List<String> buildJoinHeaders(List<TableBinding> bindings) {
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

    private List<String> joinSelectAllRow(List<TableBinding> bindings, Record record) throws DBMSException {
        List<String> row = new ArrayList<>();
        for (TableBinding binding : bindings) {
            for (Column column : binding.table.getColumnList()) {
                row.add(record.getValue(binding.tableName + "." + column.getColumnName()).toString());
            }
        }
        return row;
    }

    private QueryResultBlock executeGrouped(List<Record> records) throws DBMSException {
        validateGroupedSelect();
        String groupingStrategy = chooseGroupingStrategy();
        Map<String, List<Record>> groups = new LinkedHashMap<>();
        for (Record record : records) {
            String key = groupKey(record);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }

        List<List<String>> rows = new ArrayList<>();
        List<GroupResult> groupResults = new ArrayList<>();
        for (List<Record> groupRecords : groups.values()) {
            if (!matchesHaving(groupRecords)) {
                continue;
            }
            Record first = groupRecords.get(0);
            List<String> row = new ArrayList<>();
            for (ColumnMention column : selectedColumnList.getColumns()) {
                row.add(column.evaluate(first).toString());
            }
            int aggregateIndex = 1;
            for (AggregateFunction aggregate : selectedColumnList.getAggregateFunctions()) {
                row.add(evaluateAggregate(aggregate, groupRecords));
                aggregateIndex++;
            }
            groupResults.add(new GroupResult(first, row));
        }
        sortGroupResultsIfNeeded(groupResults);
        for (GroupResult groupResult : groupResults) {
            rows.add(groupResult.row);
        }
        return QueryResultBlock.table(buildGroupedHeaders(), rows);
    }

    private boolean matchesHaving(List<Record> groupRecords) throws DBMSException {
        for (HavingCondition condition : havingConditions) {
            String aggregateValue = evaluateAggregate(condition.getAggregateFunction(), groupRecords);
            String expectedValue = condition.getValue().evaluate().toString();
            if (!compareHaving(aggregateValue, expectedValue, condition.getOperator().getSymbol())) {
                return false;
            }
        }
        return true;
    }

    private boolean compareHaving(String left, String right, String operator) {
        int comparison;
        if (isNumeric(left) && isNumeric(right)) {
            comparison = Double.compare(Double.parseDouble(left), Double.parseDouble(right));
        } else {
            comparison = left.compareTo(right);
        }
        switch (operator) {
            case "=":
                return comparison == 0;
            case "!=":
                return comparison != 0;
            case "<":
                return comparison < 0;
            case "<=":
                return comparison <= 0;
            case ">":
                return comparison > 0;
            case ">=":
                return comparison >= 0;
            default:
                return false;
        }
    }

    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean matchesWhere(Record record) throws DBMSException {
        return getWhereClause() == null || getWhereClause().evaluate(record);
    }

    private void addSelectedRow(Table table, List<List<String>> rows, Record record) throws DBMSException {
        List<DBMSDataType> selectedValues = selectedColumnList.evaluate(record, table);
        List<String> row = new ArrayList<>();
        for (DBMSDataType value : selectedValues) {
            row.add(value.toString());
        }
        rows.add(row);
    }

    private boolean isGroupedQuery() {
        return !groupByColumns.isEmpty() || selectedColumnList.hasAggregates();
    }

    private void validateGroupedSelect() throws DBMSException {
        if (selectedColumnList.isSelectAll()) {
            throw new DBMSException("SELECT * is not allowed with GROUP BY or aggregate functions.");
        }
        for (ColumnMention column : selectedColumnList.getColumns()) {
            if (!isGroupColumn(column)) {
                throw new DBMSException("Column '" + column.getColumnName().getName()
                        + "' must appear in GROUP BY or be used inside an aggregate function.");
            }
        }
    }

    private boolean isGroupColumn(ColumnMention columnMention) {
        for (ColumnMention groupByColumn : groupByColumns) {
            if (groupByColumn.getColumnName().getName().equals(columnMention.getColumnName().getName())) {
                return true;
            }
        }
        return false;
    }

    private String groupKey(Record record) throws DBMSException {
        if (groupByColumns.isEmpty()) {
            return "__all__";
        }
        StringBuilder key = new StringBuilder();
        for (ColumnMention groupByColumn : groupByColumns) {
            String value = groupByColumn.evaluate(record).toString();
            key.append(value.length()).append(':').append(value).append('|');
        }
        return key.toString();
    }

    private String evaluateAggregate(AggregateFunction aggregate, List<Record> records) throws DBMSException {
        switch (aggregate.getFunctionName()) {
            case "COUNT":
                return Integer.toString(records.size());
            case "SUM":
                return Integer.toString(sum(aggregate, records));
            case "AVG":
                return Double.toString(records.isEmpty() ? 0.0 : ((double) sum(aggregate, records)) / records.size());
            case "MIN":
                return minOrMax(aggregate, records, true);
            case "MAX":
                return minOrMax(aggregate, records, false);
            default:
                throw new DBMSException("Unsupported aggregate function: " + aggregate.getFunctionName());
        }
    }

    private int sum(AggregateFunction aggregate, List<Record> records) throws DBMSException {
        int sum = 0;
        for (Record record : records) {
            sum += Integer.parseInt(aggregate.getColumn().evaluate(record).toString());
        }
        return sum;
    }

    private String minOrMax(AggregateFunction aggregate, List<Record> records, boolean min) throws DBMSException {
        String best = null;
        for (Record record : records) {
            String value = aggregate.getColumn().evaluate(record).toString();
            if (best == null || (min ? value.compareTo(best) < 0 : value.compareTo(best) > 0)) {
                best = value;
            }
        }
        return best == null ? "" : best;
    }

    private List<String> buildGroupedHeaders() {
        List<String> headers = new ArrayList<>();
        for (ColumnMention column : selectedColumnList.getColumns()) {
            headers.add(column.getColumnName().getName());
        }
        int aggregateIndex = 1;
        for (AggregateFunction aggregate : selectedColumnList.getAggregateFunctions()) {
            headers.add(aggregate.getAlias() == null ? "agg" + aggregateIndex : aggregate.getAlias());
            aggregateIndex++;
        }
        return headers;
    }

    private String chooseGroupingStrategy() {
        // Hook for the query optimizer: grouping falls back to hash unless a
        // later B+ tree iterator can stream ordered group keys directly.
        return "hash";
    }

    private List<Record> chooseSingleTableAccessPath(Table table) throws DBMSException {
        List<Record> whereIndexedRecords = table.indexedRecordsFor(getWhereClause());
        if (canUseOrderIndex(table)) {
            List<Record> orderedRecords = table.orderedRecordsFor(orderByColumnNames(), orderByItems.get(0).isAscending());
            if (whereIndexedRecords == null) {
                return orderedRecords;
            }
            return chooseCheaperSingleTablePath(table, whereIndexedRecords, orderedRecords);
        }
        return whereIndexedRecords;
    }

    private boolean canUseOrderIndex(Table table) throws DBMSException {
        if (orderByItems.isEmpty() || isGroupedQuery() || !hasUniformOrderDirection()) {
            return false;
        }
        if (!table.hasIndexStartingWithColumns(orderByColumnNames())) {
            return false;
        }
        int rowCount = Math.max(1, table.getRowCount());
        double orderCost = rowCount;
        double whereCost = getWhereClause() == null ? rowCount : rowCount * Math.max(1, getWhereClause().getConditions().getConditions().size());
        double sortCost = rowCount * (Math.log(rowCount) / Math.log(2));
        return orderCost < whereCost + sortCost;
    }

    private List<Record> chooseCheaperSingleTablePath(
            Table table,
            List<Record> whereIndexedRecords,
            List<Record> orderedRecords
    ) throws DBMSException {
        int rowCount = Math.max(1, table.getRowCount());
        int whereConditionCount = getWhereClause() == null
                ? 1
                : Math.max(1, getWhereClause().getConditions().getConditions().size());
        int whereResultCount = Math.max(1, whereIndexedRecords.size());

        double wherePathCost = (double) rowCount * whereConditionCount
                + whereResultCount * (Math.log(whereResultCount) / Math.log(2));
        double orderPathCost = rowCount + (double) rowCount * whereConditionCount;

        return orderPathCost < wherePathCost ? orderedRecords : whereIndexedRecords;
    }

    private void sortRecordsIfNeeded(List<Record> records) {
        if (orderByItems.isEmpty()) {
            return;
        }
        records.sort(recordComparator());
    }

    private void sortGroupResultsIfNeeded(List<GroupResult> groupResults) {
        if (orderByItems.isEmpty()) {
            return;
        }
        groupResults.sort((left, right) -> compareRecords(left.representative, right.representative));
    }

    private Comparator<Record> recordComparator() {
        return this::compareRecords;
    }

    private int compareRecords(Record left, Record right) {
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

    private int compareOrderValues(String left, String right) {
        if (isNumeric(left) && isNumeric(right)) {
            return Double.compare(Double.parseDouble(left), Double.parseDouble(right));
        }
        return left.compareTo(right);
    }

    private String unqualifiedColumnName(String name) {
        int dotIndex = name.indexOf('.');
        return dotIndex < 0 ? name : name.substring(dotIndex + 1);
    }

    private List<String> orderByColumnNames() {
        List<String> columnNames = new ArrayList<>();
        for (OrderByItem item : orderByItems) {
            columnNames.add(unqualifiedColumnName(item.getColumn().getColumnName().getName()));
        }
        return columnNames;
    }

    private boolean hasUniformOrderDirection() {
        boolean ascending = orderByItems.get(0).isAscending();
        for (OrderByItem item : orderByItems) {
            if (item.isAscending() != ascending) {
                return false;
            }
        }
        return true;
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

    private String aliasOrName(Identifier tableName, Identifier alias) {
        return alias == null ? tableName.getName() : alias.getName();
    }

    private static final class TableBinding {
        private final String tableName;
        private final String alias;
        private final Table table;

        private TableBinding(String tableName, String alias, Table table) {
            this.tableName = tableName;
            this.alias = alias;
            this.table = table;
        }
    }

    private static final class GroupResult {
        private final Record representative;
        private final List<String> row;

        private GroupResult(Record representative, List<String> row) {
            this.representative = representative;
            this.row = row;
        }
    }
}
