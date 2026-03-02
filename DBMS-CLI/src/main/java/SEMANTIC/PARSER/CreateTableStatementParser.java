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
        // Expect: CREATE TABLE
        if (ctx.current().getType() != TokenType.CREATE)
            throw new ParseException("Expected 'CREATE' at start of CREATE TABLE statement");
        ctx.advance();

        if (ctx.current().getType() != TokenType.TABLE)
            throw new ParseException("Expected 'TABLE' after 'CREATE'");
        ctx.advance();

        // Parse table name
        Identifier tableName = IdentifierParser.parse(ctx);

        // Expect "("
        if (ctx.current().getType() != TokenType.LPAREN)
            throw new ParseException("Expected '(' after table name");
        ctx.advance();

        // Parse column columnList
        ColumnDefinitionList columns = ColumnDefinitionListParser.parse(ctx);

        // Expect ")"
        if (ctx.current().getType() != TokenType.RPAREN)
            throw new ParseException("Expected ')' after column columnList");
        ctx.advance();

        // Construct AST
        CreateTableStatement stmt = new CreateTableStatement();
        stmt.setTableName(tableName);
        stmt.setColumns(columns);
        return stmt;
    }
}
