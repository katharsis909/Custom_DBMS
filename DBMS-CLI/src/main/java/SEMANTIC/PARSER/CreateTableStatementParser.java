package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.CreateTableStatement;
import SEMANTIC.AST_NODES.ColumnDefinitionList;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

public class CreateTableStatementParser {
    public static CreateTableStatement parse(ParserContext ctx) throws ParseException, LexerException {
        int statementPosition = ctx.current().getPosition();
        // Expect: CREATE TABLE
        if (ctx.current().getType() != TokenType.CREATE)
            throw ctx.error("Expected 'CREATE' at start of CREATE TABLE statement");
        ctx.advance();
        return parseAfterCreate(ctx, statementPosition);
    }

    static CreateTableStatement parseAfterCreate(ParserContext ctx, int statementPosition) throws ParseException, LexerException {
        if (ctx.current().getType() != TokenType.TABLE)
            throw ctx.error("Expected 'TABLE' after 'CREATE'");
        ctx.advance();

        // Parse table name
        Identifier tableName = IdentifierParser.parse(ctx);

        // Expect "("
        if (ctx.current().getType() != TokenType.LPAREN)
            throw ctx.error("Expected '(' after table name");
        ctx.advance();

        // Parse column columnList
        ColumnDefinitionList columns = ColumnDefinitionListParser.parse(ctx);

        // Expect ")"
        if (ctx.current().getType() != TokenType.RPAREN)
            throw ctx.error("Expected ')' after column columnList");
        ctx.advance();

        // Construct AST
        CreateTableStatement stmt = new CreateTableStatement();
        stmt.setSourcePosition(statementPosition);
        stmt.setTableName(tableName);
        stmt.setColumns(columns);
        return stmt;
    }
}
