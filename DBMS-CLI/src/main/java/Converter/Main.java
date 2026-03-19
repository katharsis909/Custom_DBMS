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
        return  new Column(columnName, columnType);
    }

    public static List<Column> toColumnListFromDefinition(List<ColumnDefinition> columnDefs) {
        return columnDefs.stream()
                .map(Main::toColumnFromDefinition)
                .collect(Collectors.toList());
    }
}
