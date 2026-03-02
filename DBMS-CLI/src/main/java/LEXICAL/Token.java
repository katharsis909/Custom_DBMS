package LEXICAL;

public class Token {
    private TokenType type;
    private String lexeme;
    private int position;

    public TokenType getType() {
        return type;
    }
    public void setType(TokenType type) {
        this.type = type;
    }
    public String getLexeme(){
        return lexeme;
    }
    public void setLexeme(String lexeme) {
        this.lexeme = lexeme;
    }
    public int getPosition() {
        return position;
    }
    public void setPosition(int position) {
        this.position = position;
    }

    public Token(TokenType type, String lexeme, int position) {
        this.type = type;
        this.lexeme = lexeme;
        this.position = position;
    }
    
    @Override
    public String toString() {
        return type + "('" + lexeme + "') at pos " + position;
    }
}
