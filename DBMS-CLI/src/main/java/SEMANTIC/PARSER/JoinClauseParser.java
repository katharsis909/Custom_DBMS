package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.JoinClause;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;

public class JoinClauseParser {
    public static JoinClause parse(ParserContext ctx) throws ParseException, LexerException {
        if (ctx.current().getType() != TokenType.JOIN) {
            throw ctx.error("Expected JOIN");
        }
        ctx.advance();

        Identifier tableName = IdentifierParser.parse(ctx);
        Identifier alias = TableAliasParser.parseOptionalAlias(ctx);
        if (ctx.current().getType() != TokenType.ON) {
            throw ctx.error("Expected ON after JOIN table");
        }
        ctx.advance();

        ColumnMention leftColumn = ColumnMentionParser.parse(ctx);
        if (ctx.current().getType() != TokenType.EQUAL) {
            throw ctx.error("Expected '=' in JOIN condition");
        }
        ctx.advance();
        ColumnMention rightColumn = ColumnMentionParser.parse(ctx);

        JoinClause joinClause = new JoinClause();
        joinClause.setTableName(tableName);
        joinClause.setAlias(alias);
        joinClause.setLeftColumn(leftColumn);
        joinClause.setRightColumn(rightColumn);
        return joinClause;
    }
}
