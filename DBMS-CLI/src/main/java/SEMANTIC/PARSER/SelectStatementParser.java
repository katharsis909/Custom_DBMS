package SEMANTIC.PARSER;

import LEXICAL.LexerException;
import SEMANTIC.AST_NODES.SelectStatement;
import SEMANTIC.AST_NODES.SelectedColumnList;
import SEMANTIC.AST_NODES.JoinClause;
import SEMANTIC.AST_NODES.WhereClause;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;

import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;
import SEMANTIC.PARSER.Exception.ParseException;

import LEXICAL.TokenType;

import java.util.ArrayList;
import java.util.List;

public class SelectStatementParser {
    public static SelectStatement parse(ParserContext ctx) throws ParseException, LexerException {
        int statementPosition = ctx.current().getPosition();
        if (ctx.current().getType() != TokenType.SELECT) {
            throw ctx.error("Expected SELECT, found: " + ctx.current().getLexeme());
        }
        ctx.advance(); // consume "SELECT"

        SelectedColumnList selectedColumns = SelectedColumnListParser.parse(ctx);

        if (ctx.current().getType() != TokenType.FROM) {
            throw ctx.error("Expected FROM, found: " + ctx.current().getLexeme());
        }
        ctx.advance(); // consume "FROM"

        Identifier tableName = IdentifierParser.parse(ctx);
        Identifier tableAlias = TableAliasParser.parseOptionalAlias(ctx);
        List<JoinClause> joins = new ArrayList<>();
        while (ctx.current().getType() == TokenType.JOIN) {
            joins.add(JoinClauseParser.parse(ctx));
        }

        WhereClause whereClause = null;
        if (ctx.current().getType() == TokenType.WHERE) {
            whereClause = WhereClauseParser.parse(ctx);
        }
        List<SEMANTIC.AST_NODES.ColumnMention> groupByColumns = new ArrayList<>();
        if (ctx.current().getType() == TokenType.GROUP) {
            ctx.advance();
            if (ctx.current().getType() != TokenType.BY) {
                throw ctx.error("Expected BY after GROUP");
            }
            ctx.advance();
            groupByColumns.add(ColumnMentionParser.parse(ctx));
            while (ctx.current().getType() == TokenType.COMMA) {
                ctx.advance();
                groupByColumns.add(ColumnMentionParser.parse(ctx));
            }
        }
        List<SEMANTIC.AST_NODES.HavingCondition> havingConditions = new ArrayList<>();
        if (ctx.current().getType() == TokenType.HAVING) {
            ctx.advance();
            havingConditions.add(HavingConditionParser.parse(ctx));
            while (ctx.current().getType() == TokenType.AND) {
                ctx.advance();
                havingConditions.add(HavingConditionParser.parse(ctx));
            }
        }
        List<SEMANTIC.AST_NODES.OrderByItem> orderByItems = new ArrayList<>();
        if (ctx.current().getType() == TokenType.ORDER) {
            ctx.advance();
            if (ctx.current().getType() != TokenType.BY) {
                throw ctx.error("Expected BY after ORDER");
            }
            ctx.advance();
            orderByItems.add(OrderByItemParser.parse(ctx));
            while (ctx.current().getType() == TokenType.COMMA) {
                ctx.advance();
                orderByItems.add(OrderByItemParser.parse(ctx));
            }
        }

        SelectStatement stmt = new SelectStatement();
        stmt.setSourcePosition(statementPosition);
        stmt.setSelectedColumnList(selectedColumns);
        stmt.setTableName(tableName);
        stmt.setTableAlias(tableAlias);
        stmt.setJoins(joins);
        stmt.setWhereClause(whereClause);
        stmt.setGroupByColumns(groupByColumns);
        stmt.setHavingConditions(havingConditions);
        stmt.setOrderByItems(orderByItems);

        return stmt;
    }
}
