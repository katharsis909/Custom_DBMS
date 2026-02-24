package Converter;

import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.CreateTableStatement;
import SEMANTIC.AST_NODES.SelectedColumnList;
import STRUCTURE.Column;
import STRUCTURE.Table;

import java.util.ArrayList;
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

    public static Table toTable(CreateTableStatement stmt) {
        Table table = new Table();
        table.setTable_name(stmt.getTableName().getName());
        table.setColumnList(toColumnListFromDefinition(stmt.getColumns().getColumnList()));
        //stmt.columns give Object which "contains" list of columns defined as - columnList
        table.setRecordList(new ArrayList<>());
        return table;
    }

    /*
    public static ColumnMention toColumnMention(Column column)
    {
        return new ColumnMention(column.getColumnName());
    }*/
}
