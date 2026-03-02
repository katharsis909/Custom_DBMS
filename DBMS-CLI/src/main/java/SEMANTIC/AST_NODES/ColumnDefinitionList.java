package SEMANTIC.AST_NODES;

import java.util.List;

public class ColumnDefinitionList {
    private List<ColumnDefinition> columnList;

    public ColumnDefinitionList(List<ColumnDefinition> columnList) {
        this.setColumnList(columnList);
    }

    public List<ColumnDefinition> getColumnList() {
        return columnList;
    }

    public void setColumnList(List<ColumnDefinition> columnList) {
        this.columnList = columnList;
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
