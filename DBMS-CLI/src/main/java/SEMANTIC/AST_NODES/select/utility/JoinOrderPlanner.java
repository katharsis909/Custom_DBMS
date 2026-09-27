package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.JoinClause;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.TableBinding;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.WhereClause;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import STRUCTURE.table.Table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JoinOrderPlanner {

    public List<TableBinding> buildOptimizedJoinOrder(
            Catalog catalog,
            Identifier tableName,
            Identifier tableAlias,
            Table baseTable,
            List<JoinClause> joins
    ) throws DBMSException {
        return buildOptimizedJoinOrder(catalog, tableName, tableAlias, baseTable, joins, null, List.of());
    }

    public List<TableBinding> buildOptimizedJoinOrder(
            Catalog catalog,
            Identifier tableName,
            Identifier tableAlias,
            Table baseTable,
            List<JoinClause> joins,
            WhereClause whereClause,
            List<OrderByItem> orderByItems
    ) throws DBMSException {
        List<TableBinding> bindings = new ArrayList<>();
        bindings.add(new TableBinding(tableName.getName(), aliasOrName(tableName, tableAlias), baseTable));
        for (JoinClause join : joins) {
            bindings.add(new TableBinding(
                    join.getTableName().getName(),
                    aliasOrName(join.getTableName(), join.getAlias()),
                    catalog.getTable(join.getTableName().getName())
            ));
        }

        boolean hasWhere = whereClause != null && whereClause.getConditions() != null && !whereClause.getConditions().getConditions().isEmpty();
        boolean hasOrderBy = orderByItems != null && !orderByItems.isEmpty();

        if (hasWhere || hasOrderBy) {
            Set<String> qualifiedOrderTables = qualifiedOrderByTables(orderByItems);
            bindings.sort((left, right) -> {
                try {
                    double scoreLeft = calculateFilteringScore(left, whereClause);
                    double scoreRight = calculateFilteringScore(right, whereClause);

                    if (scoreLeft != scoreRight) {
                        return Double.compare(scoreRight, scoreLeft); // Higher score first
                    }

                    boolean leftOrderQual = qualifiedOrderTables.contains(left.alias) || qualifiedOrderTables.contains(left.tableName);
                    boolean rightOrderQual = qualifiedOrderTables.contains(right.alias) || qualifiedOrderTables.contains(right.tableName);
                    if (leftOrderQual != rightOrderQual) {
                        return leftOrderQual ? -1 : 1;
                    }

                    return Integer.compare(left.table.getApproximateRowCount(), right.table.getApproximateRowCount());
                } catch (DBMSException e) {
                    return 0;
                }
            });
        } else {
            // Fallback when NO WHERE and NO ORDER BY: Order by least row count first for INLJ
            bindings.sort((left, right) -> {
                try {
                    return Integer.compare(left.table.getApproximateRowCount(), right.table.getApproximateRowCount());
                } catch (DBMSException e) {
                    return 0;
                }
            });
        }

        return bindings;
    }

    public double calculateFilteringScore(TableBinding binding, WhereClause whereClause) throws DBMSException {
        if (whereClause == null || whereClause.getConditions() == null) {
            return 0.0;
        }

        int rowCount = Math.max(1, binding.table.getApproximateRowCount());
        int totalColumns = Math.max(1, binding.table.getColumnList().size());
        double indexPoints = 0.0;

        Map<String, List<String>> colOps = new HashMap<>();
        for (UnaryCondition condition : whereClause.getConditions().getConditions()) {
            String fullCol = condition.getColumnName().getName();
            String unqual = unqualifiedColumnName(fullCol);

            if (columnBelongsToBinding(fullCol, binding)) {
                if (binding.table.hasIndexOnColumn(unqual)) {
                    colOps.computeIfAbsent(unqual, k -> new ArrayList<>()).add(condition.getOperator().getSymbol());
                }
            }
        }

        for (List<String> ops : colOps.values()) {
            boolean hasEq = ops.contains("=");
            boolean hasLower = ops.contains(">") || ops.contains(">=");
            boolean hasUpper = ops.contains("<") || ops.contains("<=");

            if (hasEq) {
                indexPoints += 1.5;
            } else if (hasLower && hasUpper) {
                indexPoints += 1.0;
            } else if (hasLower || hasUpper) {
                indexPoints += 0.5;
            }
        }

        return (rowCount * indexPoints) / totalColumns;
    }

    public Set<String> qualifiedOrderByTables(List<OrderByItem> orderByItems) {
        Set<String> validTables = new HashSet<>();
        Set<String> seenAndFinishedTables = new HashSet<>();
        String currentTable = null;

        for (OrderByItem item : orderByItems) {
            String colName = item.getColumn().getColumnName().getName();
            String tableName = extractTableName(colName);
            if (tableName == null) {
                currentTable = null;
                continue;
            }
            if (!tableName.equals(currentTable)) {
                if (currentTable != null) {
                    seenAndFinishedTables.add(currentTable);
                }
                currentTable = tableName;
            }
            if (seenAndFinishedTables.contains(tableName)) {
                validTables.remove(tableName);
            } else {
                validTables.add(tableName);
            }
        }
        return validTables;
    }

    private boolean columnBelongsToBinding(String fullCol, TableBinding binding) {
        String prefix = binding.tableName + ".";
        String aliasPrefix = binding.alias + ".";
        if (fullCol.startsWith(prefix) || fullCol.startsWith(aliasPrefix)) {
            return true;
        }
        return !fullCol.contains(".") && binding.table.hasColumn(fullCol);
    }

    private String extractTableName(String fullCol) {
        int dot = fullCol.indexOf('.');
        return dot < 0 ? null : fullCol.substring(0, dot);
    }

    private String unqualifiedColumnName(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    public String aliasOrName(Identifier tableName, Identifier alias) {
        return alias == null ? tableName.getName() : alias.getName();
    }
}
