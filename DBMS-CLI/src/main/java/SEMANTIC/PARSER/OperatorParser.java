package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.AST_NODES.Operator;
import SEMANTIC.PARSER.util.ParserContext;

public class OperatorParser {
    public static Operator parse(ParserContext ctx) throws ParseException, LexerException {
        Token token = ctx.current();

        if (token.getType() != TokenType.EQUAL) {
            throw ctx.errorAt("Expected '=' operator but found: " + token.getLexeme(), token.getPosition());
        }
        //expect function of ctx will print EQUAL instead of =; so do not use

        ctx.advance();
        Operator op = new Operator();
        op.setSymbol(token.getLexeme());
        return op;
    }
}
