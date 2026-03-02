package SEMANTIC.PARSER.util;

import LEXICAL.*;
import SEMANTIC.PARSER.Exception.ParseException;

public class ParserContext {
    private final Lexer lexer;
    private Token current;

    public Lexer getLexer() {
        return lexer;
    }

    public Token getCurrent() {
        return current;
    }

    public void setCurrent(Token current) {
        this.current = current;
    }

    public ParserContext(Lexer lexer) throws LexerException {
        this.lexer = lexer;
        current = lexer.nextToken();
    }

    public Token current() {
        return current;
    }

    public void advance() throws LexerException {
        current = lexer.nextToken();
        //save globally
    }

    /*public boolean match(TokenType type) throws LexerException {
        if (current.getType() == type) {
            advance();
            return true;
        }
        return false;
    }*/

    public Token expect(TokenType type) throws ParseException, LexerException {
        if (current.getType() == type) {
            Token tok = current;
            advance();
            //no need to save globally as already done inside advance()
            return tok;
        } else {
            throw new ParseException("Expected token " + type + " but found " + current.getType() + " at position " + current.getPosition());
        }
    }

    public boolean isAtEnd() {
        return current.getType() == TokenType.EOF;
    }

}
