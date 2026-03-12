package dbmscli;

import LEXICAL.Lexer;
import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.StatementList;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.StatementListParser;
import SEMANTIC.PARSER.util.ParserContext;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import dbmscli.result.ExecutionResult;

public class DbmsCliEngine {
    private Catalog catalog = new Catalog();

    public synchronized String execute(String sql) throws LexerException, ParseException, DBMSException {
        return executeStructured(sql).renderText().trim();
    }

    public synchronized ExecutionResult executeStructured(String sql) throws LexerException, ParseException, DBMSException {
        Lexer lexer = new Lexer(sql);
        ParserContext context = new ParserContext(lexer);
        StatementList statementList = StatementListParser.parse(context);
        return statementList.execute(catalog);
    }

    public synchronized void reset() {
        this.catalog = new Catalog();
    }
}
