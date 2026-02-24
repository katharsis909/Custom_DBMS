package STRUCTURE;

public class DBMSException extends Exception {
    public DBMSException() {
    }

    public DBMSException(String message) {
        super(message);
    }

    public DBMSException(String message, Throwable cause) {
        super(message, cause);
    }

    public DBMSException(Throwable cause) {
        super(cause);
    }
}
