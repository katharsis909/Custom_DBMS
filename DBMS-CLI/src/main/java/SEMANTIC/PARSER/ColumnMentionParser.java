package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;

public class ColumnMentionParser {
    public static ColumnMention parse(ParserContext ctx) throws ParseException, LexerException {
        int columnPosition = ctx.current().getPosition();
        Identifier id = IdentifierParser.parse(ctx);
        if (ctx.current().getType() == TokenType.DOT) {
            ctx.advance();
            Identifier columnName = IdentifierParser.parse(ctx);
            Identifier qualifiedIdentifier = new Identifier();
            qualifiedIdentifier.setName(id.getName() + "." + columnName.getName());
            id = qualifiedIdentifier;
        }
        ColumnMention col = new ColumnMention();
        col.setSourcePosition(columnPosition);
        col.setColumnName(id);
        return col;
    }
}
