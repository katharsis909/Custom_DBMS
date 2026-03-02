package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

public class ColumnMention {
    private Identifier columnName;

    public Identifier getColumnName() {
        return columnName;
    }

    public void setColumnName(Identifier columnName) {
        this.columnName = columnName;
    }

    public DBMSDataType evaluate(Record record) throws DBMSException {
        return record.getValue(getColumnName().getName());
        //time waste, as info already present above
    }
}
