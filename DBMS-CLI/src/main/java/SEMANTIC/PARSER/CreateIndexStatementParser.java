package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.CreateIndexStatement;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;

import java.util.ArrayList;
import java.util.List;

public class CreateIndexStatementParser {
    static CreateIndexStatement parseAfterCreate(ParserContext ctx, int statementPosition) throws ParseException, LexerException {
        if (ctx.current().getType() != TokenType.INDEX) {
            throw ctx.error("Expected INDEX after CREATE");
        }
        ctx.advance();

        Identifier indexName = IdentifierParser.parse(ctx);

        if (ctx.current().getType() != TokenType.ON) {
            throw ctx.error("Expected ON after index name");
        }
        ctx.advance();

        Identifier tableName = IdentifierParser.parse(ctx);

        if (ctx.current().getType() != TokenType.LPAREN) {
            throw ctx.error("Expected '(' after table name");
        }
        ctx.advance();

        List<Identifier> columnNames = new ArrayList<>();
        columnNames.add(IdentifierParser.parse(ctx));

        while (ctx.current().getType() == TokenType.COMMA) {
            ctx.advance();
            columnNames.add(IdentifierParser.parse(ctx));
        }

        if (ctx.current().getType() != TokenType.RPAREN) {
            throw ctx.error("Expected ')' after index columns");
        }
        ctx.advance();

        CreateIndexStatement statement = new CreateIndexStatement();
        statement.setSourcePosition(statementPosition);
        statement.setIndexName(indexName);
        statement.setTableName(tableName);
        statement.setColumnNames(columnNames);
        return statement;
    }
}
