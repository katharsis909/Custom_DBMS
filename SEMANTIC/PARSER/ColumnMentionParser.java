package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;

public class ColumnMentionParser {
    public static ColumnMention parse(ParserContext ctx) throws ParseException, LexerException {
        Identifier id = IdentifierParser.parse(ctx);
        ColumnMention col = new ColumnMention();
        col.setColumnName(id);
        return col;
    }
}
