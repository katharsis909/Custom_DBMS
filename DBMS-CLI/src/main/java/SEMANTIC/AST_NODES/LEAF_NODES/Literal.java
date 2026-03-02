package SEMANTIC.AST_NODES.LEAF_NODES;

import STRUCTURE.DBMSDataType;

public abstract class Literal {
    public abstract DBMSDataType evaluate();
    /*
    //question
    //Could not implement because DBMSDataType does not has convToDB_DT defined
    public DBMSDataType evaluate() throws DBMSException
    {
        DBMSDataType.conv
    }*/
}
