package LEXICAL;

import java.util.Map;

public class Lexer {
    private final String input;
    private int pos = 0;
    //Position of current pointer(character pointer & not word pointer)
    //to input string
    private int length;
    
    private static final Map<String, TokenType> keywords = Map.ofEntries(
            Map.entry("CREATE", TokenType.CREATE),
            Map.entry("TABLE", TokenType.TABLE),
            Map.entry("DROP", TokenType.DROP),
            Map.entry("SELECT", TokenType.SELECT),
            Map.entry("FROM", TokenType.FROM),
            Map.entry("WHERE", TokenType.WHERE),
            Map.entry("INT", TokenType.INT),
            Map.entry("STRING", TokenType.STRING),
            Map.entry("INSERT", TokenType.INSERT),
            Map.entry("INTO", TokenType.INTO),
            Map.entry("AND", TokenType.AND)
        );
    //Keywords

    public Lexer(String input) 
    {
        this.input = input;
        this.length = input.length();
        //needed for checking - If reached the end or not
    }

    public Token nextToken() throws LexerException {
        skipWhitespace();

        if (pos >= length) 
            return new Token(TokenType.EOF, "", pos);
        //Token creation requires 3 parameters

        char c = input.charAt(pos);

        // Symbols aka Single length characters
        //No need for lookahead
        switch (c) {
            case ',': pos++; return new Token(TokenType.COMMA, ",", pos - 1);
                //pos already++; Hence, pos-1
            case '*': pos++; return new Token(TokenType.STAR, "*", pos - 1);
            case '(': pos++; return new Token(TokenType.LPAREN, "(", pos - 1);
            case ')': pos++; return new Token(TokenType.RPAREN, ")", pos - 1);
            case '=': pos++; return new Token(TokenType.EQUAL, "=", pos - 1);
            case '\'': return stringLiteral();
            // \' means ' ; Which is the String literal delimiter
            case ';': pos++; return new Token(TokenType.SEMICOLON, ";", pos - 1);
        }

        //Number
        if (Character.isDigit(c)) 
            return numericLiteral();
        //heads to the finite automata state for numericLiteral

        //Identifier or keyword
        else if (Character.isLetter(c) || c == '_') 
        //Identifiers can start with '_'
            return identifierOrKeyword();

        /* already handled quite above
        else if(c == '\'')
        {
            return stringLiteral();
        }*/
        else
        //Fallback
        //return new Token(TokenType.INVALID, Character.toString(c), pos++);
        throw new LexerException("INVALID token at position " + pos);
    }

    private void skipWhitespace() 
    {
        while (pos < length && Character.isWhitespace(input.charAt(pos))) 
            pos++;
    }

    private Token identifierOrKeyword() {
        int start = pos;
        boolean valid = true;
        while (pos < length) {
            char c = input.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_') 
            //Characters can be '_'
            {
                pos++;
            } else if (isTokenTerminator(c)) {
                break; // End of valid identifier/keyword
            } else {
                // Illegal character found mid-identifier
                valid = false;
                while (pos < length && !isTokenTerminator(input.charAt(pos))) 
                //For printing purposes; 
                //access all characters till toke terminates.
                    pos++;
                break;
            }
        }

        String word = input.substring(start, pos);
        if (!valid) 
        {
            return new Token(TokenType.INVALID, word, start);
        }
        TokenType type = keywords.getOrDefault(word.toUpperCase(), TokenType.IDENTIFIER);
        //Second parameter provides the default Value Object(Instance) i.e, IDENTIFIER
        return new Token(type, word, start);
    }

    private Token numericLiteral() {
        int start = pos;
        while (pos < length && Character.isDigit(input.charAt(pos))) 
            pos++;
        //All consecutive digits have beenn written
        //Next character may not be a white space tho, Hence - :
        if (pos < length && !isTokenTerminator(input.charAt(pos))) 
        {
            // Consume until the next terminator or whitespace to form the invalid token
            while (pos < length && !isTokenTerminator(input.charAt(pos))) 
            {
                pos++;
            }
            return new Token(TokenType.INVALID, input.substring(start, pos), start);
        }

        //Else a Valid numeric literal
        return new Token(TokenType.NUMERIC_LITERAL, input.substring(start, pos), start);
    }

    private boolean isTokenTerminator(char c) 
    {
        return Character.isWhitespace(c) || "=,();".indexOf(c) >= 0;
        // operators like =, (, ; need not be separate by spaces;
        //if character not foun
    }

    private Token stringLiteral()
    //vl - Handle escape sequence one day
    {
        int start = pos;
        pos++; // Skip starting quote
        StringBuilder sb = new StringBuilder();
        while (pos < length && input.charAt(pos) != '\'') 
        {
            sb.append(input.charAt(pos++));
        }
        if (pos < length && input.charAt(pos) == '\'') 
        {
            pos++; 
            //Skip ending quote
            return new Token(TokenType.STRING_LITERAL, sb.toString(), start);
        } else 
        {
            return new Token(TokenType.INVALID, "Unterminated string: '" + sb.toString(), start);
        }
    }
}
