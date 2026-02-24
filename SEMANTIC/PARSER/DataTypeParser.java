package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.DataType;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;

public class DataTypeParser {
    public static DataType parse(ParserContext ctx) throws ParseException, LexerException {
        TokenType type = ctx.current().getType();

        if (type == TokenType.INT || type == TokenType.STRING) {
            DataType dt = new DataType();
            dt.setDataType(ctx.current().getLexeme().toUpperCase()); // Ensure uniform capitalization
            ctx.advance();
            return dt;
        } else {
            throw new ParseException("Expected data type INT or STRING but found: " + ctx.current().getLexeme());
        }
    }
}
