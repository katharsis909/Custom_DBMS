package dbmscli;

import LEXICAL.Lexer;
import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.StatementList;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.StatementListParser;
import SEMANTIC.PARSER.util.ParserContext;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class DbmsCliEngine {
    private Catalog catalog = new Catalog();

    public synchronized String execute(String sql) throws LexerException, ParseException, DBMSException {
        Lexer lexer = new Lexer(sql);
        ParserContext context = new ParserContext(lexer);
        StatementList statementList = StatementListParser.parse(context);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(outputStream, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            statementList.evaluate(catalog);
        } finally {
            System.setOut(originalOut);
        }

        return outputStream.toString(StandardCharsets.UTF_8).trim();
    }

    public synchronized void reset() {
        this.catalog = new Catalog();
    }
}
