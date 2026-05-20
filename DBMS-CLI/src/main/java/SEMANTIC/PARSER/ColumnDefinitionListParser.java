package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnDefinitionList;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

import java.util.ArrayList;
import java.util.List;

public class ColumnDefinitionListParser {
    public static ColumnDefinitionList parse(ParserContext ctx) throws ParseException, LexerException {
        List<ColumnDefinition> defs = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();

        parseListItem(ctx, defs, primaryKeyColumns);

        while (ctx.current().getType() == TokenType.COMMA) {
            ctx.advance(); // skip ","
            parseListItem(ctx, defs, primaryKeyColumns);
        }

        ColumnDefinitionList list = new ColumnDefinitionList(defs);
        list.setPrimaryKeyColumns(primaryKeyColumns);
        return list;
    }

    private static void parseListItem(
            ParserContext ctx,
            List<ColumnDefinition> defs,
            List<String> primaryKeyColumns
    ) throws ParseException, LexerException {
        if (ctx.current().getType() == TokenType.PRIMARY) {
            parseTablePrimaryKey(ctx, primaryKeyColumns);
            return;
        }

        ColumnDefinition def = ColumnDefinitionParser.parse(ctx);
        defs.add(def);
        if (def.isPrimaryKey()) {
            primaryKeyColumns.add(def.getColumnName().getName());
        }
    }

    private static void parseTablePrimaryKey(
            ParserContext ctx,
            List<String> primaryKeyColumns
    ) throws ParseException, LexerException {
        ctx.advance();
        if (ctx.current().getType() != TokenType.KEY) {
            throw ctx.error("Expected KEY after PRIMARY");
        }
        ctx.advance();
        if (ctx.current().getType() != TokenType.LPAREN) {
            throw ctx.error("Expected '(' after PRIMARY KEY");
        }
        ctx.advance();

        Identifier firstColumn = IdentifierParser.parse(ctx);
        primaryKeyColumns.add(firstColumn.getName());

        while (ctx.current().getType() == TokenType.COMMA) {
            ctx.advance();
            Identifier nextColumn = IdentifierParser.parse(ctx);
            primaryKeyColumns.add(nextColumn.getName());
        }

        if (ctx.current().getType() != TokenType.RPAREN) {
            throw ctx.error("Expected ')' after PRIMARY KEY columns");
        }
        ctx.advance();
    }
}
