 

import LEXICAL.Lexer;
import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.StatementList;
import SEMANTIC.PARSER.StatementListParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;

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
            String line;
            //processes each line and appends to inputBuilder
            //inputBuilder will be used for Parsing

            // Read multi-line input until ';'
            while (!(line = scanner.nextLine()).trim().endsWith(";")) {
                inputBuilder.append(line).append(" ");
                //each line's end implicitly corresponds to a ' '
                //System.out.print(".. ");
            }
            inputBuilder.append(line);

            String input = inputBuilder.toString().trim();
            if (input.equalsIgnoreCase("exit;")) {
                break;
            }

            try {
                Lexer lexer = new Lexer(input);
                //sabse pehle lexer use kia input
                ParserContext ctx = new ParserContext(lexer);
                //all info now in token iterator
                StatementList stmtlist = StatementListParser.parse(ctx);
                //current catalog pass karo
                //above statement passed the final AST-Node
                stmtlist.evaluate(db);
                //recursively evaluate

            } catch (ParseException | DBMSException | LexerException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
        System.out.println("Goodbye.");
    }
}
