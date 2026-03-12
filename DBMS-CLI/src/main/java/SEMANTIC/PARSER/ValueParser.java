package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.Value;
import SEMANTIC.AST_NODES.LEAF_NODES.Literal;
import SEMANTIC.PARSER.LEAF.LiteralParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;

public class ValueParser {
    public static Value parse(ParserContext ctx) throws ParseException, LexerException {
        int valuePosition = ctx.current().getPosition();
        Literal literal = LiteralParser.parse(ctx);

        Value value = new Value();
        value.setSourcePosition(valuePosition);
        value.setLiteral(literal);

        return value;
    }
}
