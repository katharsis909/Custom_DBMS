package SEMANTIC.AST_NODES;

import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;

public abstract class Statement
//abstract class allows common attributes

{
    public abstract void evaluate(Catalog catalog) throws DBMSException;
    //cannot make common now; because all return different objects
}
