package SEMANTIC.PARSER;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.Statement;
import SEMANTIC.AST_NODES.StatementList;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.util.ParserContext;

class StatementListParserTest {

    @Test
    void shouldParseSingleStatementFollowedBySemicolonAndEof() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Statement statement = mock(Statement.class);

        when(ctx.current()).thenReturn(
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.SEMICOLON, ";", 6),
                token(TokenType.EOF, "", 7));
        doNothing().when(ctx).advance();

        try (MockedStatic<StatementParser> statementParserMock = mockStatic(StatementParser.class)) {
            statementParserMock.when(() -> StatementParser.parse(ctx)).thenReturn(statement);

            StatementList result = StatementListParser.parse(ctx);

            assertEquals(1, result.getStatements().size());
            assertSame(statement, result.getStatements().get(0));
            statementParserMock.verify(() -> StatementParser.parse(ctx), times(1));
            verify(ctx, times(1)).advance();
        }
    }

    @Test
    void shouldParseMultipleStatementsUntilEof() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Statement firstStatement = mock(Statement.class);
        Statement secondStatement = mock(Statement.class);

        when(ctx.current()).thenReturn(
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.SEMICOLON, ";", 6),
                token(TokenType.DROP, "DROP", 8),
                token(TokenType.SEMICOLON, ";", 12),
                token(TokenType.EOF, "", 13));
        doNothing().when(ctx).advance();

        try (MockedStatic<StatementParser> statementParserMock = mockStatic(StatementParser.class)) {
            statementParserMock.when(() -> StatementParser.parse(ctx)).thenReturn(firstStatement, secondStatement);

            StatementList result = StatementListParser.parse(ctx);

            assertEquals(2, result.getStatements().size());
            assertSame(firstStatement, result.getStatements().get(0));
            assertSame(secondStatement, result.getStatements().get(1));
            statementParserMock.verify(() -> StatementParser.parse(ctx), times(2));
            verify(ctx, times(2)).advance();
        }
    }

    @Test
    void shouldThrowParseExceptionWhenSemicolonIsMissingAfterStatement() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Statement statement = mock(Statement.class);
        ParseException parseException = new ParseException("Expected ';' after statement, found: ", 7);

        when(ctx.current()).thenReturn(
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.EOF, "", 7),
                token(TokenType.EOF, "", 7));
        when(ctx.error("Expected ';' after statement, found: ")).thenReturn(parseException);

        try (MockedStatic<StatementParser> statementParserMock = mockStatic(StatementParser.class)) {
            statementParserMock.when(() -> StatementParser.parse(ctx)).thenReturn(statement);

            ParseException thrown = assertThrows(ParseException.class, () -> StatementListParser.parse(ctx));

            assertSame(parseException, thrown);
            statementParserMock.verify(() -> StatementParser.parse(ctx), times(1));
            verify(ctx, never()).advance();
        }
    }

    @Test
    void shouldThrowParseExceptionWhenUnexpectedTokenAppearsAfterStatement() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Statement statement = mock(Statement.class);
        ParseException parseException = new ParseException("Expected ';' after statement, found: name", 4);
        Token identifier = token(TokenType.IDENTIFIER, "name", 4);

        when(ctx.current()).thenReturn(
                token(TokenType.SELECT, "SELECT", 0),
                identifier,
                identifier);
        when(ctx.error("Expected ';' after statement, found: name")).thenReturn(parseException);

        try (MockedStatic<StatementParser> statementParserMock = mockStatic(StatementParser.class)) {
            statementParserMock.when(() -> StatementParser.parse(ctx)).thenReturn(statement);

            ParseException thrown = assertThrows(ParseException.class, () -> StatementListParser.parse(ctx));

            assertSame(parseException, thrown);
            statementParserMock.verify(() -> StatementParser.parse(ctx), times(1));
            verify(ctx, never()).advance();
        }
    }

    @Test
    void shouldReturnEmptyStatementListWhenInputStartsAtEof() throws Exception {
        ParserContext ctx = mock(ParserContext.class);

        when(ctx.current()).thenReturn(token(TokenType.EOF, "", 0));

        try (MockedStatic<StatementParser> statementParserMock = mockStatic(StatementParser.class)) {
            StatementList result = StatementListParser.parse(ctx);

            assertEquals(0, result.getStatements().size());
            statementParserMock.verifyNoInteractions();
            verify(ctx, never()).advance();
        }
    }

    private static Token token(TokenType type, String lexeme, int position) {
        return new Token(type, lexeme, position);
    }
}
