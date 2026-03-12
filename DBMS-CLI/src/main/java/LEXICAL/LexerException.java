package LEXICAL;

public class LexerException extends Exception {
    private final Integer position;

    public LexerException(String message) {
        super(message);
        this.position = null;
    }

    public LexerException(String message, int position) {
        super(message);
        this.position = position;
    }

    public Integer getPosition() {
        return position;
    }
}
