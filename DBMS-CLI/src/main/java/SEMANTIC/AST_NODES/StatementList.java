package SEMANTIC.AST_NODES;

import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;

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

    public void evaluate(Catalog db) throws DBMSException {
        //String log = "empty_for_now";
        for (Statement statement : getStatements()) {
            statement.evaluate(db);
        }
        //return log;
    }
}
