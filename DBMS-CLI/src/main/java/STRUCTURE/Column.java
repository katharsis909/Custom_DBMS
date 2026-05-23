package STRUCTURE;

public class Column {
    private String columnName;
    private String column_type;
    private boolean primaryKey;
    private String foreignTableName;
    private String foreignColumnName;

    public Column(String column_name, String column_type) {
        this.columnName = column_name;
        this.column_type = column_type;
    }

    public Column(String column_name, String column_type, boolean primaryKey) {
        this.columnName = column_name;
        this.column_type = column_type;
        this.primaryKey = primaryKey;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getColumn_type() {
        return column_type;
    }

    public void setColumn_type(String column_type) {
        this.column_type = column_type;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean hasForeignKey() {
        return foreignTableName != null && foreignColumnName != null;
    }

    public String getForeignTableName() {
        return foreignTableName;
    }

    public void setForeignTableName(String foreignTableName) {
        this.foreignTableName = foreignTableName;
    }

    public String getForeignColumnName() {
        return foreignColumnName;
    }

    public void setForeignColumnName(String foreignColumnName) {
        this.foreignColumnName = foreignColumnName;
    }
}
