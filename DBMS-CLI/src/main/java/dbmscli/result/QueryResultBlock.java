package dbmscli.result;

import java.util.ArrayList;
import java.util.List;

public class QueryResultBlock {
    private final String message;
    private final List<String> columns;
    private final List<List<String>> rows;

    private QueryResultBlock(String message, List<String> columns, List<List<String>> rows) {
        this.message = message;
        this.columns = columns;
        this.rows = rows;
    }

    public static QueryResultBlock message(String message) {
        return new QueryResultBlock(message, null, null);
    }

    public static QueryResultBlock table(List<String> columns, List<List<String>> rows) {
        List<String> copiedColumns = new ArrayList<>(columns);
        List<List<String>> copiedRows = new ArrayList<>();
        for (List<String> row : rows) {
            copiedRows.add(new ArrayList<>(row));
        }
        return new QueryResultBlock(null, copiedColumns, copiedRows);
    }

    public boolean isTable() {
        return columns != null && rows != null;
    }

    public boolean isEmpty() {
        return !isTable() && (message == null || message.isBlank());
    }

    public String getMessage() {
        return message;
    }

    public List<String> getColumns() {
        return columns == null ? null : new ArrayList<>(columns);
    }

    public List<List<String>> getRows() {
        if (rows == null) {
            return null;
        }

        List<List<String>> copiedRows = new ArrayList<>();
        for (List<String> row : rows) {
            copiedRows.add(new ArrayList<>(row));
        }
        return copiedRows;
    }

    public String renderText() {
        if (isTable()) {
            int[] widths = new int[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                widths[i] = columns.get(i).length();
            }
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    widths[i] = Math.max(widths[i], row.get(i).length());
                }
            }

            StringBuilder builder = new StringBuilder();
            builder.append(formatRow(columns, widths));
            for (List<String> row : rows) {
                builder.append(System.lineSeparator()).append(formatRow(row, widths));
            }
            return builder.toString();
        }

        return message == null ? "" : message;
    }

    private String formatRow(List<String> values, int[] widths) {
        StringBuilder builder = new StringBuilder();
        builder.append("| ");
        for (int i = 0; i < values.size(); i++) {
            builder.append(String.format("%-" + widths[i] + "s", values.get(i)));
            builder.append(" |");
            if (i < values.size() - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }
}
