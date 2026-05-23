package SEMANTIC.AST_NODES;

public class AggregateFunction {
    private String functionName;
    private ColumnMention column;
    private boolean countAll;
    private String alias;

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public ColumnMention getColumn() {
        return column;
    }

    public void setColumn(ColumnMention column) {
        this.column = column;
    }

    public boolean isCountAll() {
        return countAll;
    }

    public void setCountAll(boolean countAll) {
        this.countAll = countAll;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
