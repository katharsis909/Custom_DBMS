package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.util.ParserContext;

public class OrderByItemParser {
    public static OrderByItem parse(ParserContext ctx) throws ParseException, LexerException {
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
