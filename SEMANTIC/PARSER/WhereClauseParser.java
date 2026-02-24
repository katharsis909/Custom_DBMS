package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.WhereClause;
import SEMANTIC.AST_NODES.ConditionList;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

public class WhereClauseParser {
    public static WhereClause parse(ParserContext ctx) throws ParseException, LexerException {
        if (ctx.current().getType() != TokenType.WHERE) {
            throw new ParseException("Expected WHERE, found: " + ctx.current().getLexeme());
        }

        ctx.advance(); // skip "WHERE"

        ConditionList conditionList = ConditionListParser.parse(ctx);

        WhereClause clause = new WhereClause();
        clause.setConditions(conditionList);
        return clause;
    }
}
