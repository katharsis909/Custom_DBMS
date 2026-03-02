package LEXICAL;

public class LexerTest {
    public static void main(String[] args) throws LexerException {
        String sql = "SELECT name FROM students;;DROP TABLE x";
        Lexer lexer = new Lexer(sql);

        Token token;
        do {
            token = lexer.nextToken();
            System.out.println(token);
        } while (token.getType() != TokenType.EOF);
    }
}
