package SEMANTIC.AST_NODES;

public class OrderByItem {
    private ColumnMention column;
    private boolean ascending = true;

    public ColumnMention getColumn() {
        return column;
    }

    public void setColumn(ColumnMention column) {
        this.column = column;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }
}
