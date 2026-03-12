package dbmscli.result;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {
    private final List<QueryResultBlock> blocks = new ArrayList<>();

    public void addBlock(QueryResultBlock block) {
        if (block != null && !block.isEmpty()) {
            blocks.add(block);
        }
    }

    public List<QueryResultBlock> getBlocks() {
        return new ArrayList<>(blocks);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public String renderText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append(blocks.get(i).renderText());
        }
        return builder.toString();
    }
}
