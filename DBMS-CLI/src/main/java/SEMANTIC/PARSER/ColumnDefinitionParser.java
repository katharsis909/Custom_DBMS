package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.DataType;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

public class ColumnDefinitionParser {
    public static ColumnDefinition parse(ParserContext ctx) throws ParseException, LexerException {
        Identifier columnName = IdentifierParser.parse(ctx);
        DataType dataType = DataTypeParser.parse(ctx);

        ColumnDefinition def = new ColumnDefinition();
        def.setColumnName(columnName);
        def.setDataType(dataType);
        if (ctx.current().getType() == TokenType.PRIMARY) {
            ctx.advance();
            if (ctx.current().getType() != TokenType.KEY) {
                throw ctx.error("Expected KEY after PRIMARY");
            }
            ctx.advance();
            def.setPrimaryKey(true);
        }
        if (ctx.current().getType() == TokenType.REFERENCES) {
            ctx.advance();
            Identifier referencedTable = IdentifierParser.parse(ctx);
            if (ctx.current().getType() != TokenType.LPAREN) {
                throw ctx.error("Expected '(' after referenced table");
            }
            ctx.advance();
            Identifier referencedColumn = IdentifierParser.parse(ctx);
            if (ctx.current().getType() != TokenType.RPAREN) {
                throw ctx.error("Expected ')' after referenced column");
            }
            ctx.advance();
            def.setForeignTableName(referencedTable);
            def.setForeignColumnName(referencedColumn);
        }

        return def;
    }
}
