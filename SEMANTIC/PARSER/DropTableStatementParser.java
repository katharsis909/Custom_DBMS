package SEMANTIC.PARSER;

import SEMANTIC.AST_NODES.DropTableStatement;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;

import LEXICAL.*;

public class DropTableStatementParser {
    public static DropTableStatement parse(ParserContext ctx) throws ParseException, LexerException {
        // Expect "DROP"
        if (ctx.current().getType() != TokenType.DROP) {
            throw new ParseException("Expected 'DROP' but found: " + ctx.current().getLexeme());
        }
        ctx.advance();

        // Expect "TABLE"
        if (ctx.current().getType() != TokenType.TABLE) {
            throw new ParseException("Expected 'TABLE' after DROP but found: " + /ctx.current().getLexeme());
        }
        ctx.advance();

        // Parse table name as identifier
        //Below method itself does expect and advance
        Identifier tableName = IdentifierParser.parse(ctx);

        DropTableStatement stmt = new DropTableStatement();
        stmt.setTableName(tableName);
        return stmt;
    }
}
