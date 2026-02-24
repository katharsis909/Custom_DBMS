package SEMANTIC.AST_NODES;

import STRUCTURE.DBMSDataType;

public class Operator
{
    private String symbol;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public boolean evaluate(DBMSDataType left, DBMSDataType right) {
        // Right now, only "=" is supported
            return left.equals(right); // uses DBMSDataType.equals()
    }
}
