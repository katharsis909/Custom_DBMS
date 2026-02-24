package SEMANTIC.AST_NODES;

import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.util.List;

public class ConditionList {
    private List<UnaryCondition> conditions;

    public ConditionList(List<UnaryCondition> conditions) {
        this.setConditions(conditions);
    }

    public List<UnaryCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<UnaryCondition> conditions) {
        this.conditions = conditions;
    }

    /*
        evaluate shall need a Record/row
        evaluate should return boolean for all
    */
    public boolean evaluate(Record record) throws DBMSException
    //record sent from above;(by "FROM {table_name}" in SELECT)
    {
        for (UnaryCondition condition : getConditions()) {
            if (!condition.evaluate(record)) {
                return false; // short-circuit on first failure (AND logic)
            }
        }
        return true;
    }
}
