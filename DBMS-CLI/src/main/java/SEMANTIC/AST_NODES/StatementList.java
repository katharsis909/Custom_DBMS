package SEMANTIC.AST_NODES;

import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import dbmscli.result.ExecutionResult;
import dbmscli.result.QueryResultBlock;

import java.util.List;

public class StatementList {
    private List<Statement> statements;

    public StatementList(List<Statement> statements)
    //Let us wait as to how these shall be called and where
    {
        this.setStatements(statements);
    }

    public List<Statement> getStatements() {
        return statements;
    }

    public void setStatements(List<Statement> statements) {
        this.statements = statements;
    }

    // Evaluates each statement in order and lets each one print its own interactive output, if any.
    public void evaluate(Catalog db) throws DBMSException {
        //String log = "empty_for_now";
        for (Statement statement : getStatements()) {
            statement.evaluate(db);
        }
        //return log;
    }

    // Executes each statement in order and collects all non-empty result blocks into one aggregate execution result.
    public ExecutionResult execute(Catalog db) throws DBMSException {
        ExecutionResult result = new ExecutionResult();
        for (Statement statement : getStatements()) {
            QueryResultBlock block = statement.execute(db);
            result.addBlock(block);
        }
        return result;
    }
}
