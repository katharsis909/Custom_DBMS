package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnDefinitionList;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

import java.util.ArrayList;
import java.util.List;

public class ColumnDefinitionListParser {
    public static ColumnDefinitionList parse(ParserContext ctx) throws ParseException, LexerException {
        List<ColumnDefinition> defs = new ArrayList<>();

        // First column definition
        defs.add(ColumnDefinitionParser.parse(ctx));
        //inside function returns the AST_NODE; keep adding them to list

        // Parse more if there are commas
        while (ctx.current().getType() == TokenType.COMMA) {
            ctx.advance(); // skip ","
            defs.add(ColumnDefinitionParser.parse(ctx));
        }

        return new ColumnDefinitionList(defs);
    }
}
