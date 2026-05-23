package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Literal;

public class HavingCondition {
    private AggregateFunction aggregateFunction;
    private Operator operator;
    private Literal value;

    public AggregateFunction getAggregateFunction() {
        return aggregateFunction;
    }

    public void setAggregateFunction(AggregateFunction aggregateFunction) {
        this.aggregateFunction = aggregateFunction;
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
}
