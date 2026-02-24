package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.LEAF_NODES.Literal;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

public class UnaryCondition {
    private Identifier columnName;
    private Operator operator; // only "=" for now
    private Literal value;

    public Identifier getColumnName() {
        return columnName;
    }

    public void setColumnName(Identifier columnName) {
        this.columnName = columnName;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public Literal getValue() {
        return value;
    }

    public void setValue(Literal value) {
        this.value = value;
    }

    public boolean evaluate(Record record) throws DBMSException {
        DBMSDataType actualValue = record.getValue(getColumnName().getName());
        // Extract the actual value from the record using the column name
        //hover on Columnname to know the Class of it

        //Record is a hashmap, so returns you the value associated with the columnname

        // Evaluate the literal (returns MyInt or MyString)

        DBMSDataType expectedValue = getValue().evaluate();
        //Token gets converted to DBMSDataType,     for below evaluation!

        return getOperator().evaluate(actualValue, expectedValue);
        //Use the operator to evaluate the comparison
    }
}
