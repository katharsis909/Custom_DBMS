package SEMANTIC.AST_NODES;

import java.util.ArrayList;
import java.util.List;

public class ColumnDefinitionList {
    private List<ColumnDefinition> columnList;
    private List<String> primaryKeyColumns;
    private List<ForeignKeyDefinition> foreignKeys;

    public ColumnDefinitionList(List<ColumnDefinition> columnList) {
        this.setColumnList(columnList);
        this.primaryKeyColumns = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
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

    public List<ForeignKeyDefinition> getForeignKeys() {
        return new ArrayList<>(foreignKeys);
    }

    public void setForeignKeys(List<ForeignKeyDefinition> foreignKeys) {
        this.foreignKeys = new ArrayList<>(foreignKeys);
    }

    public ColumnDefinitionList evaluate() {
        for (int i = 0; i < getColumnList().size(); i++) {
            getColumnList().get(i).evaluate();
            //doing pure time waste
        }
        return this;
        //return itself haha!
    }

    public static class ForeignKeyDefinition {
        private final String columnName;
        private final String referencedTableName;
        private final String referencedColumnName;

        public ForeignKeyDefinition(String columnName, String referencedTableName, String referencedColumnName) {
            this.columnName = columnName;
            this.referencedTableName = referencedTableName;
            this.referencedColumnName = referencedColumnName;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getReferencedTableName() {
            return referencedTableName;
        }

        public String getReferencedColumnName() {
            return referencedColumnName;
        }
    }
}
