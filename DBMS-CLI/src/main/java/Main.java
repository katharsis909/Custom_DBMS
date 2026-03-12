 

import LEXICAL.Lexer;
import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.StatementList;
import SEMANTIC.PARSER.StatementListParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern POSITION_PATTERN = Pattern.compile("position\\s+(\\d+)");

    public static void main(String[] args) {
        Catalog db = new Catalog();
        //new db created on Main.main()
        Scanner scanner = new Scanner(System.in);

        System.out.println("Welcome to MiniSQL (type 'exit;' to quit)");

        while (true) {
            System.out.println();
            System.out.print(">> ");
            //for every one-line or multi-line input
            //accepts until last line ends with ;
            StringBuilder inputBuilder = new StringBuilder();
            List<String> inputLines = new ArrayList<>();
            String line;
            //processes each line and appends to inputBuilder
            //inputBuilder will be used for Parsing

            // Read multi-line input until ';'
            try {
                while (!(line = scanner.nextLine()).trim().endsWith(";")) {
                    inputLines.add(line);
                    inputBuilder.append(line).append('\n');
                    //System.out.print(".. ");
                }
            } catch (NoSuchElementException e) {
                break;
            }
            inputLines.add(line);
            inputBuilder.append(line);

            SourceDocument sourceDocument = new SourceDocument(inputBuilder.toString().trim(), inputLines);
            if (sourceDocument.getText().equalsIgnoreCase("exit;")) {
                break;
            }

            try {
                // Execution workflow: run the current input through lexing, parsing,
                // and evaluation, then use the same source document for readable errors.
                Lexer lexer = new Lexer(sourceDocument.getText());
                //sabse pehle lexer use kia input
                ParserContext ctx = new ParserContext(lexer);
                //all info now in token iterator
                StatementList stmtlist = StatementListParser.parse(ctx);
                //current catalog pass karo
                //above statement passed the final AST-Node
                stmtlist.evaluate(db);
                //recursively evaluate

            } catch (ParseException | DBMSException | LexerException e) {
                System.out.println(formatErrorMessage(sourceDocument, e.getMessage()));
            }
        }
        scanner.close();
        System.out.println("Goodbye.");
    }

    private static String formatErrorMessage(SourceDocument sourceDocument, String message) {
        Integer position = extractPosition(message);
        if (position == null || sourceDocument.isEmpty()) {
            return "Error: " + message;
        }

        // Error display logic: convert the flat parser offset into a line and
        // column so the CLI can point at the failing part of the input.
        SourceDocument.SourceLocation location = sourceDocument.locate(position);
        StringBuilder pointer = new StringBuilder();
        for (int i = 1; i < location.getColumn(); i++) {
            pointer.append(' ');
        }
        pointer.append('^');

        return "Error at line " + location.getLine() + ", column " + location.getColumn() + ": " + message
                + System.lineSeparator() + location.getLineText()
                + System.lineSeparator() + pointer;
    }

    private static Integer extractPosition(String message) {
        Matcher matcher = POSITION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
