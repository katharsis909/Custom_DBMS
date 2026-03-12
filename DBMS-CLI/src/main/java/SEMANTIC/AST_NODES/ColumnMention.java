package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

public class ColumnMention {
    private Identifier columnName;
    private int sourcePosition = -1;

    public Identifier getColumnName() {
        return columnName;
    }

    public void setColumnName(Identifier columnName) {
        this.columnName = columnName;
    }

    public int getSourcePosition() {
        return sourcePosition;
    }

    public void setSourcePosition(int sourcePosition) {
        this.sourcePosition = sourcePosition;
    }

    public DBMSDataType evaluate(Record record) throws DBMSException {
        try {
            return record.getValue(getColumnName().getName());
        } catch (DBMSException exception) {
            throw new DBMSException(exception.getMessage(), sourcePosition);
        }
        //time waste, as info already present above
    }
}
