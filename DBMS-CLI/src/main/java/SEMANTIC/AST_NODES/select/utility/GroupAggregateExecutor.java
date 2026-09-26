package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.AggregateFunction;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.GroupResult;
import SEMANTIC.AST_NODES.HavingCondition;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.SelectedColumnList;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import dbmscli.result.QueryResultBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GroupAggregateExecutor {

    public QueryResultBlock executeGrouped(
            List<Record> records,
            SelectedColumnList selectedColumnList,
            List<ColumnMention> groupByColumns,
            List<HavingCondition> havingConditions,
            List<OrderByItem> orderByItems,
            RecordSortEngine sortEngine
    ) throws DBMSException {
        validateGroupedSelect(selectedColumnList, groupByColumns);
        Map<String, List<Record>> groups = new LinkedHashMap<>();
        for (Record record : records) {
            String key = groupKey(record, groupByColumns);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
        }

        List<List<String>> rows = new ArrayList<>();
        List<GroupResult> groupResults = new ArrayList<>();
        for (List<Record> groupRecords : groups.values()) {
            if (!matchesHaving(groupRecords, havingConditions)) {
                continue;
            }
            Record first = groupRecords.get(0);
            List<String> row = new ArrayList<>();
            for (ColumnMention column : selectedColumnList.getColumns()) {
                row.add(column.evaluate(first).toString());
            }
            for (AggregateFunction aggregate : selectedColumnList.getAggregateFunctions()) {
                row.add(evaluateAggregate(aggregate, groupRecords));
            }
            groupResults.add(new GroupResult(first, row));
        }

        sortEngine.sortGroupResultsIfNeeded(groupResults, orderByItems, selectedColumnList);
        for (GroupResult groupResult : groupResults) {
            rows.add(groupResult.row);
        }
        return QueryResultBlock.table(buildGroupedHeaders(selectedColumnList), rows);
    }

    public boolean isGroupedQuery(SelectedColumnList selectedColumnList, List<ColumnMention> groupByColumns) {
        return !groupByColumns.isEmpty() || selectedColumnList.hasAggregates();
    }

    private void validateGroupedSelect(SelectedColumnList selectedColumnList, List<ColumnMention> groupByColumns) throws DBMSException {
        if (selectedColumnList.isSelectAll()) {
            throw new DBMSException("SELECT * is not allowed with GROUP BY or aggregate functions.");
        }
        for (ColumnMention column : selectedColumnList.getColumns()) {
            if (!isGroupColumn(column, groupByColumns)) {
                throw new DBMSException("Column '" + column.getColumnName().getName()
                        + "' must appear in GROUP BY or be used inside an aggregate function.");
            }
        }
    }

    private boolean isGroupColumn(ColumnMention columnMention, List<ColumnMention> groupByColumns) {
        for (ColumnMention groupByColumn : groupByColumns) {
            if (groupByColumn.getColumnName().getName().equals(columnMention.getColumnName().getName())) {
                return true;
            }
        }
        return false;
    }

    private String groupKey(Record record, List<ColumnMention> groupByColumns) throws DBMSException {
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

    public boolean matchesHaving(List<Record> groupRecords, List<HavingCondition> havingConditions) throws DBMSException {
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
        if (RecordSortEngine.isNumeric(left) && RecordSortEngine.isNumeric(right)) {
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

    public String evaluateAggregate(AggregateFunction aggregate, List<Record> records) throws DBMSException {
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

    private List<String> buildGroupedHeaders(SelectedColumnList selectedColumnList) {
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
}
