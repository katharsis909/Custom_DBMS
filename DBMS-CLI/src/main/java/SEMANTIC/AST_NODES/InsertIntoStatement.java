package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;

import java.util.List;

//grammar expression needs to be modified too
public class InsertIntoStatement extends Statement {
    private Identifier tableName;
    private ValueList valueList;

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public ValueList getValueList() {
        return valueList;
    }

    public void setValueList(ValueList valueList) {
        this.valueList = valueList;
    }

    public void evaluate(Catalog catalog) throws DBMSException {
        Table table = catalog.getTable(getTableName().getName());
        if (table == null) {
            throw new DBMSException("Table not found: " + getTableName().getName());
        }

        List<DBMSDataType> values = valueList.evaluate(table.getColumnList());
        table.addRecord(values);
    }
}
