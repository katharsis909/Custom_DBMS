package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Literal;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;

public class Value {
    private Literal literal;

    public Literal getLiteral() {
        return literal;
    }

    public void setLiteral(Literal literal) {
        this.literal = literal;
    }

    public DBMSDataType evaluate(Column column) throws DBMSException {
        DBMSDataType temp = getLiteral().evaluate();
        if( !temp.typeEquals(column.getColumn_type()) )
            throw new DBMSException("Inserted value - " + temp.toString() + " with data type " + temp.getType() + ", which does not matches the data type " + column.getColumn_type() + " of column - " + column.getColumnName());
        return temp;
    }
}
