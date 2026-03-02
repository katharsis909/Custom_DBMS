package SEMANTIC.AST_NODES.LEAF_NODES;

import STRUCTURE.DBMSDataType;
import STRUCTURE.MyInt;

public class NumericLiteral extends Literal
//Evaluates to: A MyInt (your custom DBMSDataType).
{
    private int value;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public DBMSDataType evaluate() {
        return MyInt.convtoDB_DT(String.valueOf(getValue()));
    }
}
