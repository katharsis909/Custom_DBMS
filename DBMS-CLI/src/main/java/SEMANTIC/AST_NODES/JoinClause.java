package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

public class JoinClause {
    private Identifier tableName;
    private Identifier alias;
    private ColumnMention leftColumn;
    private ColumnMention rightColumn;

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public Identifier getAlias() {
        return alias;
    }

    public void setAlias(Identifier alias) {
        this.alias = alias;
    }

    public ColumnMention getLeftColumn() {
        return leftColumn;
    }

    public void setLeftColumn(ColumnMention leftColumn) {
        this.leftColumn = leftColumn;
    }

    public ColumnMention getRightColumn() {
        return rightColumn;
    }

    public void setRightColumn(ColumnMention rightColumn) {
        this.rightColumn = rightColumn;
    }

    public boolean evaluate(Record record) throws DBMSException {
        return leftColumn.evaluate(record).equals(rightColumn.evaluate(record));
    }
}
