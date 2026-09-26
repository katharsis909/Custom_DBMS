package SEMANTIC.AST_NODES;

import STRUCTURE.Record;
import java.util.List;

public final class GroupResult {
    public final Record representative;
    public final List<String> row;

    public GroupResult(Record representative, List<String> row) {
        this.representative = representative;
        this.row = row;
    }
}
