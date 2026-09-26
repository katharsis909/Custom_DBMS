package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.util.ParserContext;

public class OrderByItemParser {
    public static OrderByItem parse(ParserContext ctx) throws ParseException, LexerException {
        TokenType currentType = ctx.current().getType();
        if (currentType == TokenType.COUNT || currentType == TokenType.SUM ||
            currentType == TokenType.AVG || currentType == TokenType.MIN ||
            currentType == TokenType.MAX) {
            String fnName = ctx.current().getLexeme().toUpperCase();
            ctx.advance();
            String colName = "*";
            if (ctx.current().getType() == TokenType.LPAREN) {
                ctx.advance();
                if (ctx.current().getType() == TokenType.STAR) {
                    colName = "*";
                    ctx.advance();
                } else if (ctx.current().getType() == TokenType.IDENTIFIER) {
                    colName = ctx.current().getLexeme();
                    ctx.advance();
                }
                if (ctx.current().getType() == TokenType.RPAREN) {
                    ctx.advance();
                }
            }
            Identifier id = new Identifier();
            id.setName(fnName + "(" + colName + ")");
            ColumnMention column = new ColumnMention();
            column.setColumnName(id);

            OrderByItem item = new OrderByItem();
            item.setColumn(column);
            if (ctx.current().getType() == TokenType.ASC) {
                ctx.advance();
                item.setAscending(true);
            } else if (ctx.current().getType() == TokenType.DESC) {
                ctx.advance();
                item.setAscending(false);
            }
            return item;
        }

        ColumnMention column = ColumnMentionParser.parse(ctx);
        OrderByItem item = new OrderByItem();
        item.setColumn(column);
        if (ctx.current().getType() == TokenType.ASC) {
            ctx.advance();
            item.setAscending(true);
        } else if (ctx.current().getType() == TokenType.DESC) {
            ctx.advance();
            item.setAscending(false);
        }
        return item;
    }
}
