package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.JoinClause;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.TableBinding;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import STRUCTURE.table.Table;

import java.util.ArrayList;
import java.util.List;

public class JoinOrderPlanner {

    public List<TableBinding> buildOptimizedJoinOrder(
            Catalog catalog,
            Identifier tableName,
            Identifier tableAlias,
            Table baseTable,
            List<JoinClause> joins
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

        bindings.sort((left, right) -> {
            try {
                return Integer.compare(joinCost(left, joins), joinCost(right, joins));
            } catch (DBMSException exception) {
                return 0;
            }
        });
        return bindings;
    }

    private int joinCost(TableBinding binding, List<JoinClause> joins) throws DBMSException {
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

    public String aliasOrName(Identifier tableName, Identifier alias) {
        return alias == null ? tableName.getName() : alias.getName();
    }
}
