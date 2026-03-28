package SEMANTIC.PARSER;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnDefinitionList;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.ConditionList;
import SEMANTIC.AST_NODES.SelectedColumnList;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.Value;
import SEMANTIC.AST_NODES.ValueList;
import SEMANTIC.PARSER.util.ParserContext;

class CollectionParserTest {

    @Test
    void shouldParseColumnDefinitionListSeparatedByCommas() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        ColumnDefinition first = mock(ColumnDefinition.class);
        ColumnDefinition second = mock(ColumnDefinition.class);

        when(ctx.current()).thenReturn(
                token(TokenType.COMMA, ",", 5),
                token(TokenType.RPAREN, ")", 9));
        doNothing().when(ctx).advance();

        try (MockedStatic<ColumnDefinitionParser> definitionMock = mockStatic(ColumnDefinitionParser.class)) {
            definitionMock.when(() -> ColumnDefinitionParser.parse(ctx)).thenReturn(first, second);

            ColumnDefinitionList result = ColumnDefinitionListParser.parse(ctx);

            assertEquals(List.of(first, second), result.getColumnList());
            verify(ctx, times(1)).advance();
        }
    }

    @Test
    void shouldParseConditionListJoinedByAnd() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        UnaryCondition first = mock(UnaryCondition.class);
        UnaryCondition second = mock(UnaryCondition.class);

        when(ctx.current()).thenReturn(
                token(TokenType.AND, "AND", 7),
                token(TokenType.EOF, "", 12));
        doNothing().when(ctx).advance();

        try (MockedStatic<UnaryConditionParser> conditionMock = mockStatic(UnaryConditionParser.class)) {
            conditionMock.when(() -> UnaryConditionParser.parse(ctx)).thenReturn(first, second);

            ConditionList result = ConditionListParser.parse(ctx);

            assertEquals(List.of(first, second), result.getConditions());
            verify(ctx, times(1)).advance();
        }
    }

    @Test
    void shouldParseValueListSeparatedByCommasAndPreserveSourcePosition() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Value first = mock(Value.class);
        Value second = mock(Value.class);

        when(ctx.current()).thenReturn(
                token(TokenType.STRING_LITERAL, "hello", 14),
                token(TokenType.COMMA, ",", 21),
                token(TokenType.RPAREN, ")", 25));
        doNothing().when(ctx).advance();

        try (MockedStatic<ValueParser> valueMock = mockStatic(ValueParser.class)) {
            valueMock.when(() -> ValueParser.parse(ctx)).thenReturn(first, second);

            ValueList result = ValueListParser.parse(ctx);

            assertEquals(14, result.getSourcePosition());
            assertEquals(List.of(first, second), result.getValues());
            verify(ctx, times(1)).advance();
        }
    }

    @Test
    void shouldParseSelectAllColumnList() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        when(ctx.current()).thenReturn(token(TokenType.STAR, "*", 0));
        doNothing().when(ctx).advance();

        SelectedColumnList result = SelectedColumnListParser.parse(ctx);

        assertEquals(true, result.isSelectAll());
        assertNull(result.getColumns());
        verify(ctx).advance();
    }

    @Test
    void shouldParseExplicitSelectedColumnsSeparatedByCommas() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        ColumnMention first = mock(ColumnMention.class);
        ColumnMention second = mock(ColumnMention.class);

        when(ctx.current()).thenReturn(
                token(TokenType.IDENTIFIER, "name", 0),
                token(TokenType.COMMA, ",", 4),
                token(TokenType.FROM, "FROM", 8));
        doNothing().when(ctx).advance();

        try (MockedStatic<ColumnMentionParser> columnMock = mockStatic(ColumnMentionParser.class)) {
            columnMock.when(() -> ColumnMentionParser.parse(ctx)).thenReturn(first, second);

            SelectedColumnList result = SelectedColumnListParser.parse(ctx);

            assertEquals(false, result.isSelectAll());
            assertEquals(List.of(first, second), result.getColumns());
            verify(ctx, times(1)).advance();
        }
    }

    private static Token token(TokenType type, String lexeme, int position) {
        return new Token(type, lexeme, position);
    }
}
