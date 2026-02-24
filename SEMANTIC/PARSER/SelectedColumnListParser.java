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

        if (ctx.current().getType() == TokenType.STAR) {
            ctx.advance();
            scl.setSelectAll(true);
                scl.setColumns(null);
                return scl;
        } else {
            scl.setSelectAll(false);
            scl.setColumns(new ArrayList<>());

            ColumnMention col = ColumnMentionParser.parse(ctx);
            scl.getColumns().add(col);

            while (ctx.current().getType() == TokenType.COMMA) {
                ctx.advance(); // Skip ','
                ColumnMention nextCol = ColumnMentionParser.parse(ctx);
                scl.getColumns().add(nextCol);
            }
        }

        return scl;
    }
}
