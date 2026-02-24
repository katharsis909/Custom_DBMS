package SEMANTIC.PARSER.LEAF;

import SEMANTIC.AST_NODES.LEAF_NODES.*;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.*;

public class LiteralParser {

    public static Literal parse(ParserContext context) throws ParseException, LexerException {
        Token token = context.current();

        switch (token.getType()) {
            case STRING_LITERAL:
                context.advance();
                //hence, advance function alone is also good
                StringLiteral strLit = new StringLiteral();
                strLit.setValue(token.getLexeme());
                return strLit;

            case NUMERIC_LITERAL:
                context.advance();
                    NumericLiteral numLit = new NumericLiteral();
                    numLit.setValue(Integer.valueOf(token.getLexeme()));
                    //NUMERIC_LITERAL matched means, Integer.valueOf will be successful 100%
                    return numLit;
            default:
                throw new ParseException("Expected String/Numeric literal but found: " + token.getLexeme() + " at position " + token.getPosition());
        }
    }
}
