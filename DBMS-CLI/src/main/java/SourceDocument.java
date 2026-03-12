import java.util.ArrayList;
import java.util.List;

public class SourceDocument {
    private final String text;
    private final List<String> lines;

    public SourceDocument(String text, List<String> lines) {
        this.text = text;
        this.lines = new ArrayList<>(lines);
    }

    public String getText() {
        return text;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public SourceLocation locate(int position) {
        int remaining = Math.max(position, 0);

        // Position lookup logic: walk line by line until the remaining offset
        // lands inside one stored line.
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (remaining <= line.length()) {
                return new SourceLocation(i + 1, remaining + 1, line);
            }
            remaining -= line.length();
            // Newline accounting logic: each stored line boundary consumes one
            // extra character in the flattened parser position.
            if (i < lines.size() - 1) {
                remaining--;
            }
        }

        String lastLine = lines.get(lines.size() - 1);
        return new SourceLocation(lines.size(), lastLine.length() + 1, lastLine);
    }

    public static class SourceLocation {
        private final int line;
        private final int column;
        private final String lineText;

        public SourceLocation(int line, int column, String lineText) {
            this.line = line;
            this.column = column;
            this.lineText = lineText;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public String getLineText() {
            return lineText;
        }
    }
}
