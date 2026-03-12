 

import LEXICAL.Lexer;
import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.StatementList;
import SEMANTIC.PARSER.StatementListParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import dbmscli.SourceDocument;
import dbmscli.SqlErrorFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Main {
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
                System.out.println(SqlErrorFormatter.format(sourceDocument.getText(), e));
            }
        }
        scanner.close();
        System.out.println("Goodbye.");
    }
}
