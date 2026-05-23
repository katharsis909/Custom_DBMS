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
        List<ColumnDefinitionList.ForeignKeyDefinition> foreignKeys = new ArrayList<>();

        parseListItem(ctx, defs, primaryKeyColumns, foreignKeys);

        while (ctx.current().getType() == TokenType.COMMA) {
            ctx.advance(); // skip ","
            parseListItem(ctx, defs, primaryKeyColumns, foreignKeys);
        }

        ColumnDefinitionList list = new ColumnDefinitionList(defs);
        list.setPrimaryKeyColumns(primaryKeyColumns);
        list.setForeignKeys(foreignKeys);
        return list;
    }

    private static void parseListItem(
            ParserContext ctx,
            List<ColumnDefinition> defs,
            List<String> primaryKeyColumns,
            List<ColumnDefinitionList.ForeignKeyDefinition> foreignKeys
    ) throws ParseException, LexerException {
        TokenType currentType = ctx.current().getType();
        if (currentType == TokenType.PRIMARY) {
            parseTablePrimaryKey(ctx, primaryKeyColumns);
            return;
        }
        if (currentType == TokenType.FOREIGN) {
            parseTableForeignKey(ctx, foreignKeys);
            return;
        }

        ColumnDefinition def = ColumnDefinitionParser.parse(ctx);
        defs.add(def);
        if (def.isPrimaryKey()) {
            primaryKeyColumns.add(def.getColumnName().getName());
        }
        if (def.hasForeignKey()) {
            foreignKeys.add(new ColumnDefinitionList.ForeignKeyDefinition(
                    def.getColumnName().getName(),
                    def.getForeignTableName().getName(),
                    def.getForeignColumnName().getName()
            ));
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

    private static void parseTableForeignKey(
            ParserContext ctx,
            List<ColumnDefinitionList.ForeignKeyDefinition> foreignKeys
    ) throws ParseException, LexerException {
        ctx.advance();
        if (ctx.current().getType() != TokenType.KEY) {
            throw ctx.error("Expected KEY after FOREIGN");
        }
        ctx.advance();
        if (ctx.current().getType() != TokenType.LPAREN) {
            throw ctx.error("Expected '(' after FOREIGN KEY");
        }
        ctx.advance();

        Identifier columnName = IdentifierParser.parse(ctx);

        if (ctx.current().getType() != TokenType.RPAREN) {
            throw ctx.error("Expected ')' after FOREIGN KEY column");
        }
        ctx.advance();
        if (ctx.current().getType() != TokenType.REFERENCES) {
            throw ctx.error("Expected REFERENCES after FOREIGN KEY column");
        }
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

        foreignKeys.add(new ColumnDefinitionList.ForeignKeyDefinition(
                columnName.getName(),
                referencedTable.getName(),
                referencedColumn.getName()
        ));
    }
}
