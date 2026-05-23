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
        int comparison = compare(left, right);
        switch (symbol) {
            case "=":
                return comparison == 0;
            case "!=":
                return comparison != 0;
            case "<":
                return comparison < 0;
            case "<=":
                return comparison <= 0;
            case ">":
                return comparison > 0;
            case ">=":
                return comparison >= 0;
            default:
                throw new IllegalArgumentException("Unsupported operator: " + symbol);
        }
    }

    private int compare(DBMSDataType left, DBMSDataType right) {
        if (!left.typeEquals(right)) {
            return left.getType().compareTo(right.getType());
        }
        if (left.getType().equals("INT")) {
            return Integer.compare(Integer.parseInt(left.toString()), Integer.parseInt(right.toString()));
        }
        return left.toString().compareTo(right.toString());
    }
}
