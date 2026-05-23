package Converter;

import SEMANTIC.AST_NODES.ColumnDefinition;
import STRUCTURE.Column;

import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static Column toColumnFromDefinition(ColumnDefinition columnDefinition)
    {
        String columnName = columnDefinition.getColumnName().getName();
        String columnType = columnDefinition.getDataType().getDataType();
        Column column = new Column(columnName, columnType, columnDefinition.isPrimaryKey());
        if (columnDefinition.hasForeignKey()) {
            column.setForeignTableName(columnDefinition.getForeignTableName().getName());
            column.setForeignColumnName(columnDefinition.getForeignColumnName().getName());
        }
        return column;
    }

    public static List<Column> toColumnListFromDefinition(List<ColumnDefinition> columnDefs) {
        return columnDefs.stream()
                .map(Main::toColumnFromDefinition)
                .collect(Collectors.toList());
    }
}
