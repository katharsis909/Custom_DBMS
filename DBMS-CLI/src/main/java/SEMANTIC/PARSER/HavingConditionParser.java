package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.AggregateFunction;
import SEMANTIC.AST_NODES.HavingCondition;
import SEMANTIC.AST_NODES.Operator;
import SEMANTIC.AST_NODES.LEAF_NODES.Literal;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.LiteralParser;
import SEMANTIC.PARSER.util.ParserContext;

public class HavingConditionParser {
    public static HavingCondition parse(ParserContext ctx) throws ParseException, LexerException {
        AggregateFunction aggregateFunction = AggregateFunctionParser.parse(ctx);
        Operator operator = OperatorParser.parse(ctx);
        Literal value = LiteralParser.parse(ctx);

        HavingCondition condition = new HavingCondition();
        condition.setAggregateFunction(aggregateFunction);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }
}
