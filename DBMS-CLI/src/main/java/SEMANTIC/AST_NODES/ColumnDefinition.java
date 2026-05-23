package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;

public class ColumnDefinition {
    private Identifier columnName;
    private DataType dataType; // "INT" or "STRING"
    private boolean primaryKey;
    private Identifier foreignTableName;
    private Identifier foreignColumnName;

    public Identifier getColumnName() {
        return columnName;
    }

    public void setColumnName(Identifier columnName) {
        this.columnName = columnName;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Identifier getForeignTableName() {
        return foreignTableName;
    }

    public void setForeignTableName(Identifier foreignTableName) {
        this.foreignTableName = foreignTableName;
    }

    public Identifier getForeignColumnName() {
        return foreignColumnName;
    }

    public void setForeignColumnName(Identifier foreignColumnName) {
        this.foreignColumnName = foreignColumnName;
    }

    public boolean hasForeignKey() {
        return foreignTableName != null && foreignColumnName != null;
    }

    public String getColumnDataType() {
        return getDataType().getDataType();
    }

    public ColumnDefinition evaluate() {
        return this;
        //doing time waste
    }
}
