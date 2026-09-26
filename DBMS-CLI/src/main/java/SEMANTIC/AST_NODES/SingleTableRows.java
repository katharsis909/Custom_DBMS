package SEMANTIC.AST_NODES;

import STRUCTURE.Record;
import java.util.List;

public final class SingleTableRows {
    public final List<Record> records;
    public final boolean orderSatisfied;

    public SingleTableRows(List<Record> records, boolean orderSatisfied) {
        this.records = records;
        this.orderSatisfied = orderSatisfied;
    }
}
