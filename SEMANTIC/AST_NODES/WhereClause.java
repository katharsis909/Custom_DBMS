package SEMANTIC.AST_NODES;

import STRUCTURE.DBMSException;
import STRUCTURE.Record;

public class WhereClause
{
    private ConditionList conditions;

    public ConditionList getConditions() {
        return conditions;
    }

    public void setConditions(ConditionList conditions) {
        this.conditions = conditions;
    }

    public boolean evaluate(Record record) throws DBMSException
    //record sent from above;(by "FROM {table_name}" in SELECT)
    {
        return getConditions().evaluate(record);
    }
}
