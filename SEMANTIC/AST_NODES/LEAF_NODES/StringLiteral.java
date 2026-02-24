package SEMANTIC.AST_NODES.LEAF_NODES;

import STRUCTURE.DBMSDataType;
import STRUCTURE.MyString;

public class StringLiteral extends Literal
//Evaluates to: A MyString (your custom DBMSDataType).
{
    private String value;

    public DBMSDataType evaluate()
    {
        return MyString.convtoDB_DT(getValue());
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}