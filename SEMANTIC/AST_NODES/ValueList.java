package SEMANTIC.AST_NODES;

import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;

import java.util.List;

public class ValueList {
    private List<Value> values;

    public List<Value> getValues() {
        return values;
    }

    public void setValues(List<Value> values) {
        this.values = values;
    }

    public List<DBMSDataType> evaluate(List<Column> columnList) throws DBMSException {
        if( values.size() != columnList.size())
            throw new DBMSException("Inserted Values length - " + values.size() +" do not match Table columns length - " + columnList.size() + ".");
        List<DBMSDataType> result = new java.util.ArrayList<>();

        for(int i = 0; i < values.size(); i++)
        {
            Value v = values.get(i);
            result.add(v.evaluate(columnList.get(i)));
        }

        return result;
    }

}
