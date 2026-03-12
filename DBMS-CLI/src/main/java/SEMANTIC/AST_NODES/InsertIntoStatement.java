package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;
import dbmscli.result.QueryResultBlock;

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

    public QueryResultBlock execute(Catalog catalog) throws DBMSException {
        try {
            Table table = catalog.getTable(getTableName().getName());
            if (table == null) {
                throw new DBMSException("Table not found: " + getTableName().getName(), getSourcePosition());
            }

            List<DBMSDataType> values = valueList.evaluate(table.getColumnList());
            table.addRecord(values);
        } catch (DBMSException exception) {
            throw attachPosition(exception, getSourcePosition());
        }
        return null;
    }

    private DBMSException attachPosition(DBMSException exception, int position) {
        if (exception.getPosition() != null) {
            return exception;
        }
        return new DBMSException(exception.getMessage(), position);
    }
}
