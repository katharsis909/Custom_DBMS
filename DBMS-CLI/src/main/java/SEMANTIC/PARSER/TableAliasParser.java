package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;

public class TableAliasParser {
    public static Identifier parseOptionalAlias(ParserContext ctx) throws ParseException, LexerException {
        if (ctx.current().getType() == TokenType.AS) {
            ctx.advance();
            return IdentifierParser.parse(ctx);
        }
        if (ctx.current().getType() == TokenType.IDENTIFIER) {
            return IdentifierParser.parse(ctx);
        }
        return null;
    }
}
