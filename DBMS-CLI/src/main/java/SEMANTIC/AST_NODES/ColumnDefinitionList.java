package SEMANTIC.AST_NODES;

import java.util.ArrayList;
import java.util.List;

public class ColumnDefinitionList {
    private List<ColumnDefinition> columnList;
    private List<String> primaryKeyColumns;

    public ColumnDefinitionList(List<ColumnDefinition> columnList) {
        this.setColumnList(columnList);
        this.primaryKeyColumns = new ArrayList<>();
    }

    public List<ColumnDefinition> getColumnList() {
        return columnList;
    }

    public void setColumnList(List<ColumnDefinition> columnList) {
        this.columnList = columnList;
    }

    public List<String> getPrimaryKeyColumns() {
        return new ArrayList<>(primaryKeyColumns);
    }

    public void setPrimaryKeyColumns(List<String> primaryKeyColumns) {
        this.primaryKeyColumns = new ArrayList<>(primaryKeyColumns);
    }

    public ColumnDefinitionList evaluate() {
        for (int i = 0; i < getColumnList().size(); i++) {
            getColumnList().get(i).evaluate();
            //doing pure time waste
        }
        return this;
        //return itself haha!
    }
}
