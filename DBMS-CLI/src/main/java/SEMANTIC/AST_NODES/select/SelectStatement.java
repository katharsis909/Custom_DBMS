package SEMANTIC.AST_NODES.select;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.HavingCondition;
import SEMANTIC.AST_NODES.JoinClause;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;

import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.SelectedColumnList;

import SEMANTIC.AST_NODES.SingleTableAccessPath;
import SEMANTIC.AST_NODES.SingleTableRows;
import SEMANTIC.AST_NODES.Statement;
import SEMANTIC.AST_NODES.TableBinding;
import SEMANTIC.AST_NODES.WhereClause;
import SEMANTIC.AST_NODES.select.utility.AccessPathPlanner;
import SEMANTIC.AST_NODES.select.utility.GroupAggregateExecutor;
import SEMANTIC.AST_NODES.select.utility.JoinExecutor;
import SEMANTIC.AST_NODES.select.utility.JoinOrderPlanner;
import SEMANTIC.AST_NODES.select.utility.RecordSortEngine;
import SEMANTIC.AST_NODES.select.utility.ResultProjectionEngine;
import STRUCTURE.Catalog;
import java.util.Iterator;
import java.util.Set;

import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.table.Table;

import dbmscli.result.QueryResultBlock;

import disk_persistence.TableIterator;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement extends Statement {
    private SelectedColumnList selectedColumnList;
    private Identifier tableName;
    private Identifier tableAlias;
    private List<JoinClause> joins = new ArrayList<>();
    private WhereClause whereClause;
    private List<ColumnMention> groupByColumns = new ArrayList<>();
    private List<HavingCondition> havingConditions = new ArrayList<>();
    private List<OrderByItem> orderByItems = new ArrayList<>();

    private final AccessPathPlanner accessPathPlanner = new AccessPathPlanner();
    private final JoinOrderPlanner joinPlanner = new JoinOrderPlanner();
    private final JoinExecutor joinExecutor = new JoinExecutor();
    private final GroupAggregateExecutor groupExecutor = new GroupAggregateExecutor();
    private final RecordSortEngine sortEngine = new RecordSortEngine();
    private final ResultProjectionEngine projectionEngine = new ResultProjectionEngine();

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

    @Override
    public QueryResultBlock execute(Catalog db) throws DBMSException {
        try {
            Table table = db.getTable(getTableName().getName());
            if (!joins.isEmpty()) {
                return executeJoin(db, table);
            }
            return executeSingleTable(table);
        } catch (DBMSException exception) {
            throw attachPosition(exception, getSourcePosition());
        }
    }

    private QueryResultBlock executeSingleTable(Table table) throws DBMSException {
        SingleTableRows tableRows = collectSingleTableRecords(table);
        List<Record> filteredRecords = tableRows.records;
        if (groupExecutor.isGroupedQuery(selectedColumnList, groupByColumns)) {
            return groupExecutor.executeGrouped(filteredRecords, selectedColumnList, groupByColumns, havingConditions, orderByItems, sortEngine);
        }
        if (!tableRows.orderSatisfied) {
            sortEngine.sortRecordsIfNeeded(filteredRecords, orderByItems);
        }
        return projectionEngine.buildSingleTableResult(table, filteredRecords, selectedColumnList);
    }

    private SingleTableRows collectSingleTableRecords(Table table) throws DBMSException {
        SingleTableAccessPath accessPath = accessPathPlanner.chooseSingleTableAccessPath(
                table, whereClause, orderByItems, groupExecutor.isGroupedQuery(selectedColumnList, groupByColumns)
        );
        if (accessPath.useOrderIndex) {
            QUERY_PLANNER.OrderIndexMatch orderMatch = accessPathPlanner.findOrderIndexMatch(table, orderByItems, groupExecutor.isGroupedQuery(selectedColumnList, groupByColumns));
            List<String> indexedColumns = accessPathPlanner.orderByColumnNames(orderByItems).subList(0, accessPath.orderPrefixLength);
            Iterator<Record> orderedIterator = table.orderedRecordIterator(indexedColumns, orderMatch.scanAscending);
            if (orderedIterator == null) {
                List<Record> records = table.orderedRecordsFor(indexedColumns, orderMatch.scanAscending);
                if (records != null) {
                    orderedIterator = records.iterator();
                }
            }
            return new SingleTableRows(filterMatchingRecords(orderedIterator), orderMatch.completeCovered);
        }
        if (accessPath.useWhereIndex) {
            Iterator<Record> whereIterator = table.indexedRecordIterator(getWhereClause());
            if (whereIterator == null || !whereIterator.hasNext()) {
                List<Record> records = table.indexedRecordsFor(getWhereClause());
                if (records != null && !records.isEmpty()) {
                    whereIterator = records.iterator();
                }
            }
            return new SingleTableRows(filterMatchingRecords(whereIterator), false);
        }
        return new SingleTableRows(filterMatchingRecords(table.iterator()), false);
    }

    private List<Record> filterMatchingRecords(Iterator<Record> iterator) throws DBMSException {
        List<Record> filteredRecords = new ArrayList<>();
        if (iterator == null) {
            return filteredRecords;
        }
        while (iterator.hasNext()) {
            Record record = iterator.next();
            if (matchesWhere(record)) {
                filteredRecords.add(record);
            }
        }
        return filteredRecords;
    }

    private List<Record> filterMatchingRecords(disk_persistence.TableIterator iterator) throws DBMSException {
        List<Record> filteredRecords = new ArrayList<>();
        if (iterator == null) {
            return filteredRecords;
        }
        while (iterator.hasNext()) {
            Record record = iterator.next();
            if (matchesWhere(record)) {
                filteredRecords.add(record);
            }
        }
        return filteredRecords;
    }

    private QueryResultBlock executeJoin(Catalog catalog, Table baseTable) throws DBMSException {
        List<TableBinding> bindings = joinPlanner.buildOptimizedJoinOrder(catalog, tableName, tableAlias, baseTable, joins, whereClause, orderByItems);
        Iterator<Record> joinedIterator = joinExecutor.joinedRecordIterator(bindings, joins, whereClause);

        if (groupExecutor.isGroupedQuery(selectedColumnList, groupByColumns)) {
            List<Record> filteredRecords = new ArrayList<>();
            while (joinedIterator.hasNext()) {
                filteredRecords.add(joinedIterator.next());
            }
            return groupExecutor.executeGrouped(filteredRecords, selectedColumnList, groupByColumns, havingConditions, orderByItems, sortEngine);
        }

        Set<String> qualifiedOrderTables = joinPlanner.qualifiedOrderByTables(orderByItems);
        TableBinding leadingBinding = bindings.get(0);
        boolean leadingTableQualified = qualifiedOrderTables.contains(leadingBinding.alias) || qualifiedOrderTables.contains(leadingBinding.tableName);

        int usableOrderPrefix = 0;
        if (leadingTableQualified && orderByItems != null && !orderByItems.isEmpty()) {
            usableOrderPrefix = accessPathPlanner.usableOrderIndexPrefixLength(leadingBinding.table, orderByItems, false);
        }

        Iterator<Record> finalRecordIterator = joinedIterator;
        if (usableOrderPrefix > 0 && usableOrderPrefix < orderByItems.size()) {
            finalRecordIterator = new SEMANTIC.AST_NODES.select.utility.SubGroupSortIterator(
                    joinedIterator, orderByItems, usableOrderPrefix, sortEngine
            );
        } else if (usableOrderPrefix == 0 && orderByItems != null && !orderByItems.isEmpty()) {
            List<Record> allRecords = new ArrayList<>();
            while (joinedIterator.hasNext()) {
                allRecords.add(joinedIterator.next());
            }
            sortEngine.sortRecordsIfNeeded(allRecords, orderByItems);
            finalRecordIterator = allRecords.iterator();
        }

        List<List<String>> rows = new ArrayList<>();
        while (finalRecordIterator.hasNext()) {
            joinExecutor.addJoinedSelectedRow(bindings, rows, finalRecordIterator.next(), selectedColumnList, projectionEngine);
        }

        return QueryResultBlock.table(joinExecutor.buildJoinHeaders(bindings, selectedColumnList), rows);
    }

    private boolean matchesWhere(Record record) throws DBMSException {
        return getWhereClause() == null || getWhereClause().evaluate(record);
    }

    private DBMSException attachPosition(DBMSException exception, int position) {
        if (exception.getPosition() != null) {
            return exception;
        }
        return new DBMSException(exception.getMessage(), position);
    }
}
