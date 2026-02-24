package SEMANTIC.PARSER.LEAF;

import LEXICAL.*;
import SEMANTIC.AST_NODES.LEAF_NODES.*;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.util.*;

public class IdentifierParser 
{
    public static Identifier parse(ParserContext ctx) throws ParseException, LexerException
    //Identifier bana ke deta hai ye method
    {
        Token token = ctx.expect(TokenType.IDENTIFIER);
        Identifier id = new Identifier();
        id.setName(token.getLexeme().toLowerCase());
        return id;
    }
}
