package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.SelectedColumnList;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

import java.util.ArrayList;

public class SelectedColumnListParser {
    public static SelectedColumnList parse(ParserContext ctx) throws ParseException, LexerException {
        SelectedColumnList scl = new SelectedColumnList();

        TokenType currentType = ctx.current().getType();
        if (currentType == TokenType.STAR) {
            ctx.advance();
            scl.setSelectAll(true);
                scl.setColumns(null);
                return scl;
        } else {
            scl.setSelectAll(false);
            scl.setColumns(new ArrayList<>());
            scl.setAggregateFunctions(new ArrayList<>());

            parseSelectItem(ctx, scl, currentType);

            while (ctx.current().getType() == TokenType.COMMA) {
                ctx.advance(); // Skip ','
                parseSelectItem(ctx, scl, ctx.current().getType());
            }
        }

        return scl;
    }

    private static void parseSelectItem(ParserContext ctx, SelectedColumnList scl) throws ParseException, LexerException {
        parseSelectItem(ctx, scl, ctx.current().getType());
    }

    private static void parseSelectItem(ParserContext ctx, SelectedColumnList scl, TokenType currentType) throws ParseException, LexerException {
        if (AggregateFunctionParser.isAggregate(currentType)) {
            scl.getAggregateFunctions().add(AggregateFunctionParser.parse(ctx));
            return;
        }
        ColumnMention col = ColumnMentionParser.parse(ctx);
        scl.getColumns().add(col);
    }
}
