package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.ConditionList;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;
import LEXICAL.TokenType;

import java.util.ArrayList;
import java.util.List;

public class ConditionListParser {
    public static ConditionList parse(ParserContext ctx) throws ParseException, LexerException {
        List<UnaryCondition> conditions = new ArrayList<>();

        // Parse the first unary condition
        conditions.add(UnaryConditionParser.parse(ctx));

        // Continue parsing "AND" <unary_condition> while available
        while (ctx.current().getType() == TokenType.AND) {
            ctx.advance(); // skip "AND"
            conditions.add(UnaryConditionParser.parse(ctx));
        }
        //It is not a must that "AND" appears, so not using EXPECT

        return new ConditionList(conditions);
    }
}
