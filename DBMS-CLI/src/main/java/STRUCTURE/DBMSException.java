package STRUCTURE;

public class DBMSException extends Exception {
    private final Integer position;

    public DBMSException() {
        this.position = null;
    }

    public DBMSException(String message) {
        super(message);
        this.position = null;
    }

    public DBMSException(String message, int position) {
        super(message);
        this.position = position;
    }

    public DBMSException(String message, Throwable cause) {
        super(message, cause);
        this.position = null;
    }

    public DBMSException(Throwable cause) {
        super(cause);
        this.position = null;
    }

    public Integer getPosition() {
        return position;
    }
}
