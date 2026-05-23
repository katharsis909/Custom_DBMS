package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.Statement;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.util.ParserContext;

public class CreateStatementParser {
    public static Statement parse(ParserContext ctx) throws ParseException, LexerException {
        int statementPosition = ctx.current().getPosition();
        if (ctx.current().getType() != TokenType.CREATE) {
            throw ctx.error("Expected 'CREATE'");
        }
        ctx.advance();

        if (ctx.current().getType() == TokenType.TABLE) {
            return CreateTableStatementParser.parseAfterCreate(ctx, statementPosition);
        }
        if (ctx.current().getType() == TokenType.INDEX) {
            return CreateIndexStatementParser.parseAfterCreate(ctx, statementPosition);
        }
        throw ctx.error("Expected TABLE or INDEX after CREATE");
    }
}
