package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.AggregateFunction;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;

public class AggregateFunctionParser {
    public static AggregateFunction parse(ParserContext ctx) throws ParseException, LexerException {
        Token functionToken = ctx.current();
        if (!isAggregate(functionToken.getType())) {
            throw ctx.error("Expected aggregate function");
        }
        ctx.advance();
        if (ctx.current().getType() != TokenType.LPAREN) {
            throw ctx.error("Expected '(' after aggregate function");
        }
        ctx.advance();

        AggregateFunction aggregate = new AggregateFunction();
        aggregate.setFunctionName(functionToken.getLexeme().toUpperCase());
        if (ctx.current().getType() == TokenType.STAR) {
            if (functionToken.getType() != TokenType.COUNT) {
                throw ctx.error("Only COUNT supports '*'");
            }
            aggregate.setCountAll(true);
            ctx.advance();
        } else {
            ColumnMention column = ColumnMentionParser.parse(ctx);
            aggregate.setColumn(column);
        }

        if (ctx.current().getType() != TokenType.RPAREN) {
            throw ctx.error("Expected ')' after aggregate argument");
        }
        ctx.advance();

        if (ctx.current().getType() == TokenType.AS) {
            ctx.advance();
            aggregate.setAlias(IdentifierParser.parse(ctx).getName());
        }
        return aggregate;
    }

    static boolean isAggregate(TokenType type) {
        return type == TokenType.COUNT
                || type == TokenType.SUM
                || type == TokenType.AVG
                || type == TokenType.MIN
                || type == TokenType.MAX;
    }
}
