package SEMANTIC.PARSER.Exception;

public class ParseException extends Exception {
    private final Integer position;

    public ParseException(String message) {
        super(message);
        this.position = null;
    }

    public ParseException(String message, int position) {
        super(message);
        this.position = position;
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
        this.position = null;
    }

    public Integer getPosition() {
        return position;
    }
}
