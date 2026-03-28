package SEMANTIC.AST_NODES;

import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import dbmscli.result.QueryResultBlock;

public abstract class Statement
//abstract class allows common attributes

{
    private int sourcePosition = -1;

    // Executes the statement logic and returns structured output for callers that want to render or combine results.
    public abstract QueryResultBlock execute(Catalog catalog) throws DBMSException;

    // Convenience entry point for interactive use: execute the statement and print any non-empty result block.
    public void evaluate(Catalog catalog) throws DBMSException {
        QueryResultBlock result = execute(catalog);
        if (result != null && !result.isEmpty()) {
            System.out.println(result.renderText());
        }
    }

    public int getSourcePosition() {
        return sourcePosition;
    }

    public void setSourcePosition(int sourcePosition) {
        this.sourcePosition = sourcePosition;
    }
}
