package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.Statement;

import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

public class StatementParser {
    public static Statement parse(ParserContext ctx) throws ParseException, LexerException {
        TokenType type = ctx.current().getType();

        switch (type) {
            case CREATE:
                return CreateTableStatementParser.parse(ctx);
            case SELECT:
                return SelectStatementParser.parse(ctx);
            case DROP:
                return DropTableStatementParser.parse(ctx);
            case INSERT:
                return InsertIntoStatementParser.parse(ctx);
            default:
                throw new ParseException("Unknown statement start: " + ctx.current().getLexeme());
        }
    }
}
