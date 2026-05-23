package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.Operator;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.LEAF_NODES.Literal;

import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.LEAF.LiteralParser;
import SEMANTIC.PARSER.util.ParserContext;
import LEXICAL.TokenType;

public class UnaryConditionParser {
    public static UnaryCondition parse(ParserContext ctx) throws ParseException, LexerException {
        int conditionPosition = ctx.current().getPosition();
        Identifier columnName = IdentifierParser.parse(ctx);
        if (ctx.current().getType() == TokenType.DOT) {
            ctx.advance();
            Identifier rightName = IdentifierParser.parse(ctx);
            Identifier qualifiedName = new Identifier();
            qualifiedName.setName(columnName.getName() + "." + rightName.getName());
            columnName = qualifiedName;
        }
        Operator operator = OperatorParser.parse(ctx);
        Literal value = LiteralParser.parse(ctx);

        UnaryCondition condition = new UnaryCondition();
        condition.setSourcePosition(conditionPosition);
        condition.setColumnName(columnName);
        condition.setOperator(operator);
        condition.setValue(value);

        return condition;
    }
}
