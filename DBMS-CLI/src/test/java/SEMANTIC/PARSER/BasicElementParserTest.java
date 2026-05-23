package SEMANTIC.PARSER;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.DataType;
import SEMANTIC.AST_NODES.Operator;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.Value;
import SEMANTIC.AST_NODES.WhereClause;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.LEAF_NODES.Literal;
import SEMANTIC.AST_NODES.LEAF_NODES.NumericLiteral;
import SEMANTIC.AST_NODES.LEAF_NODES.StringLiteral;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.LEAF.LiteralParser;
import SEMANTIC.PARSER.util.ParserContext;

class BasicElementParserTest {

    @Test
    void shouldParseIdentifierAndLowercaseItsName() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        when(ctx.expect(TokenType.IDENTIFIER)).thenReturn(token(TokenType.IDENTIFIER, "Students", 0));

        Identifier result = IdentifierParser.parse(ctx);

        assertEquals("students", result.getName());
    }

    @Test
    void shouldParseStringLiteralAndAdvance() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        when(ctx.current()).thenReturn(token(TokenType.STRING_LITERAL, "hello", 0));
        doNothing().when(ctx).advance();

        Literal result = LiteralParser.parse(ctx);

        assertEquals(StringLiteral.class, result.getClass());
        assertEquals("hello", ((StringLiteral) result).getValue());
        verify(ctx).advance();
    }

    @Test
    void shouldParseNumericLiteralAndAdvance() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        when(ctx.current()).thenReturn(token(TokenType.NUMERIC_LITERAL, "42", 0));
        doNothing().when(ctx).advance();

        Literal result = LiteralParser.parse(ctx);

        assertEquals(NumericLiteral.class, result.getClass());
        assertEquals(42, ((NumericLiteral) result).getValue());
        verify(ctx).advance();
    }

    @Test
    void shouldThrowParseExceptionForUnsupportedLiteralToken() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Token invalid = token(TokenType.IDENTIFIER, "name", 5);
        ParseException parseException = new ParseException("Expected String/Numeric literal but found: name", 5);

        when(ctx.current()).thenReturn(invalid);
        when(ctx.errorAt("Expected String/Numeric literal but found: name", 5)).thenReturn(parseException);

        ParseException thrown = assertThrows(ParseException.class, () -> LiteralParser.parse(ctx));

        assertSame(parseException, thrown);
    }

    @Test
    void shouldParseDataTypeAndNormalizeCapitalization() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        when(ctx.current()).thenReturn(token(TokenType.STRING, "string", 0), token(TokenType.STRING, "string", 0));
        doNothing().when(ctx).advance();

        DataType result = DataTypeParser.parse(ctx);

        assertEquals("STRING", result.getDataType());
        verify(ctx).advance();
    }

    @Test
    void shouldThrowParseExceptionForInvalidDataTypeToken() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Token invalid = token(TokenType.IDENTIFIER, "name", 3);
        ParseException parseException = new ParseException("Expected data type INT or STRING but found: name", 3);

        when(ctx.current()).thenReturn(invalid, invalid, invalid);
        when(ctx.error("Expected data type INT or STRING but found: name")).thenReturn(parseException);

        ParseException thrown = assertThrows(ParseException.class, () -> DataTypeParser.parse(ctx));

        assertSame(parseException, thrown);
    }

    @Test
    void shouldParseEqualsOperatorAndAdvance() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        when(ctx.current()).thenReturn(token(TokenType.EQUAL, "=", 8));
        doNothing().when(ctx).advance();

        Operator result = OperatorParser.parse(ctx);

        assertEquals("=", result.getSymbol());
        verify(ctx).advance();
    }

    @Test
    void shouldThrowParseExceptionForInvalidOperator() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Token invalid = token(TokenType.COMMA, ",", 4);
        ParseException parseException = new ParseException("Expected comparison operator but found: ,", 4);

        when(ctx.current()).thenReturn(invalid);
        when(ctx.errorAt("Expected comparison operator but found: ,", 4)).thenReturn(parseException);

        ParseException thrown = assertThrows(ParseException.class, () -> OperatorParser.parse(ctx));

        assertSame(parseException, thrown);
    }

    @Test
    void shouldParseColumnMentionAndPreserveSourcePosition() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier identifier = identifier("name");
        when(ctx.current()).thenReturn(token(TokenType.IDENTIFIER, "name", 11));

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(identifier);

            ColumnMention result = ColumnMentionParser.parse(ctx);

            assertEquals(11, result.getSourcePosition());
            assertSame(identifier, result.getColumnName());
        }
    }

    @Test
    void shouldParseValueAndPreserveSourcePosition() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Literal literal = stringLiteral("hello");
        when(ctx.current()).thenReturn(token(TokenType.STRING_LITERAL, "hello", 13));

        try (MockedStatic<LiteralParser> literalMock = mockStatic(LiteralParser.class)) {
            literalMock.when(() -> LiteralParser.parse(ctx)).thenReturn(literal);

            Value result = ValueParser.parse(ctx);

            assertEquals(13, result.getSourcePosition());
            assertSame(literal, result.getLiteral());
        }
    }

    @Test
    void shouldParseColumnDefinitionFromIdentifierAndDataType() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier identifier = identifier("id");
        DataType dataType = dataType("INT");
        when(ctx.current()).thenReturn(token(TokenType.COMMA, ",", 6));

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<DataTypeParser> dataTypeMock = mockStatic(DataTypeParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(identifier);
            dataTypeMock.when(() -> DataTypeParser.parse(ctx)).thenReturn(dataType);

            ColumnDefinition result = ColumnDefinitionParser.parse(ctx);

            assertSame(identifier, result.getColumnName());
            assertSame(dataType, result.getDataType());
            assertEquals(false, result.isPrimaryKey());
        }
    }

    @Test
    void shouldParseInlinePrimaryKeyColumnDefinition() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier identifier = identifier("id");
        DataType dataType = dataType("INT");
        when(ctx.current()).thenReturn(
                token(TokenType.PRIMARY, "PRIMARY", 7),
                token(TokenType.KEY, "KEY", 15));
        doNothing().when(ctx).advance();

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<DataTypeParser> dataTypeMock = mockStatic(DataTypeParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(identifier);
            dataTypeMock.when(() -> DataTypeParser.parse(ctx)).thenReturn(dataType);

            ColumnDefinition result = ColumnDefinitionParser.parse(ctx);

            assertSame(identifier, result.getColumnName());
            assertSame(dataType, result.getDataType());
            assertEquals(true, result.isPrimaryKey());
            verify(ctx, org.mockito.Mockito.times(2)).advance();
        }
    }

    @Test
    void shouldParseUnaryConditionAndPreserveSourcePosition() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier identifier = identifier("age");
        Operator operator = operator("=");
        Literal literal = numericLiteral(21);
        when(ctx.current()).thenReturn(token(TokenType.IDENTIFIER, "age", 17));

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<OperatorParser> operatorMock = mockStatic(OperatorParser.class);
             MockedStatic<LiteralParser> literalMock = mockStatic(LiteralParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(identifier);
            operatorMock.when(() -> OperatorParser.parse(ctx)).thenReturn(operator);
            literalMock.when(() -> LiteralParser.parse(ctx)).thenReturn(literal);

            UnaryCondition result = UnaryConditionParser.parse(ctx);

            assertEquals(17, result.getSourcePosition());
            assertSame(identifier, result.getColumnName());
            assertSame(operator, result.getOperator());
            assertSame(literal, result.getValue());
        }
    }

    @Test
    void shouldParseWhereClauseByDelegatingToConditionListParser() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        SEMANTIC.AST_NODES.ConditionList conditions = mock(SEMANTIC.AST_NODES.ConditionList.class);
        when(ctx.current()).thenReturn(token(TokenType.WHERE, "WHERE", 4));
        doNothing().when(ctx).advance();

        try (MockedStatic<ConditionListParser> conditionListMock = mockStatic(ConditionListParser.class)) {
            conditionListMock.when(() -> ConditionListParser.parse(ctx)).thenReturn(conditions);

            WhereClause result = WhereClauseParser.parse(ctx);

            assertSame(conditions, result.getConditions());
            verify(ctx).advance();
        }
    }

    @Test
    void shouldThrowParseExceptionWhenWhereKeywordIsMissing() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Token current = token(TokenType.IDENTIFIER, "name", 2);
        ParseException parseException = new ParseException("Expected WHERE, found: name", 2);

        when(ctx.current()).thenReturn(current, current);
        when(ctx.error("Expected WHERE, found: name")).thenReturn(parseException);

        ParseException thrown = assertThrows(ParseException.class, () -> WhereClauseParser.parse(ctx));

        assertSame(parseException, thrown);
    }

    private static Token token(TokenType type, String lexeme, int position) {
        return new Token(type, lexeme, position);
    }

    private static Identifier identifier(String name) {
        Identifier identifier = new Identifier();
        identifier.setName(name);
        return identifier;
    }

    private static DataType dataType(String name) {
        DataType dataType = new DataType();
        dataType.setDataType(name);
        return dataType;
    }

    private static Operator operator(String symbol) {
        Operator operator = new Operator();
        operator.setSymbol(symbol);
        return operator;
    }

    private static StringLiteral stringLiteral(String value) {
        StringLiteral literal = new StringLiteral();
        literal.setValue(value);
        return literal;
    }

    private static NumericLiteral numericLiteral(int value) {
        NumericLiteral literal = new NumericLiteral();
        literal.setValue(value);
        return literal;
    }
}
