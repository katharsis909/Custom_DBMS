package dbmscli;

import LEXICAL.LexerException;
import SEMANTIC.PARSER.Exception.ParseException;
import STRUCTURE.DBMSException;

public final class SqlErrorFormatter {
    private SqlErrorFormatter() {
    }

    public static String format(String sql, Exception exception) {
        SourceDocument sourceDocument = SourceDocument.fromText(sql);
        String message = exception.getMessage();
        Integer position = extractPosition(exception);

        if (position == null || sourceDocument.isEmpty()) {
            return "Error: " + message;
        }

        SourceDocument.SourceLocation location = sourceDocument.locate(position);
        return "Error at line " + location.getLine() + ", column " + location.getColumn()
                + " (position " + position + "): " + message
                + System.lineSeparator() + location.getLineText();
    }

    private static Integer extractPosition(Exception exception) {
        if (exception instanceof ParseException) {
            return ((ParseException) exception).getPosition();
        }
        if (exception instanceof LexerException) {
            return ((LexerException) exception).getPosition();
        }
        if (exception instanceof DBMSException) {
            return ((DBMSException) exception).getPosition();
        }
        return null;
    }
}
