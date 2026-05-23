package SEMANTIC.PARSER;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.CreateTableStatement;
import SEMANTIC.AST_NODES.DropTableStatement;
import SEMANTIC.AST_NODES.InsertIntoStatement;
import SEMANTIC.AST_NODES.SelectStatement;
import SEMANTIC.AST_NODES.Statement;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.util.ParserContext;

class StatementParserTest {

    @Test
    void shouldDispatchCreateStatementsToCreateTableStatementParser() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        CreateTableStatement statement = mock(CreateTableStatement.class);

        when(ctx.current()).thenReturn(token(TokenType.CREATE, "CREATE", 0));

        try (MockedStatic<CreateStatementParser> createMock = mockStatic(CreateStatementParser.class)) {
            createMock.when(() -> CreateStatementParser.parse(ctx)).thenReturn(statement);

            Statement result = StatementParser.parse(ctx);

            assertSame(statement, result);
            createMock.verify(() -> CreateStatementParser.parse(ctx));
        }
    }

    @Test
    void shouldDispatchSelectStatementsToSelectStatementParser() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        SelectStatement statement = mock(SelectStatement.class);

        when(ctx.current()).thenReturn(token(TokenType.SELECT, "SELECT", 0));

        try (MockedStatic<SelectStatementParser> selectMock = mockStatic(SelectStatementParser.class)) {
            selectMock.when(() -> SelectStatementParser.parse(ctx)).thenReturn(statement);

            Statement result = StatementParser.parse(ctx);

            assertSame(statement, result);
            selectMock.verify(() -> SelectStatementParser.parse(ctx));
        }
    }

    @Test
    void shouldDispatchDropStatementsToDropTableStatementParser() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        DropTableStatement statement = mock(DropTableStatement.class);

        when(ctx.current()).thenReturn(token(TokenType.DROP, "DROP", 0));

        try (MockedStatic<DropTableStatementParser> dropMock = mockStatic(DropTableStatementParser.class)) {
            dropMock.when(() -> DropTableStatementParser.parse(ctx)).thenReturn(statement);

            Statement result = StatementParser.parse(ctx);

            assertSame(statement, result);
            dropMock.verify(() -> DropTableStatementParser.parse(ctx));
        }
    }

    @Test
    void shouldDispatchInsertStatementsToInsertIntoStatementParser() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        InsertIntoStatement statement = mock(InsertIntoStatement.class);

        when(ctx.current()).thenReturn(token(TokenType.INSERT, "INSERT", 0));

        try (MockedStatic<InsertIntoStatementParser> insertMock = mockStatic(InsertIntoStatementParser.class)) {
            insertMock.when(() -> InsertIntoStatementParser.parse(ctx)).thenReturn(statement);

            Statement result = StatementParser.parse(ctx);

            assertSame(statement, result);
            insertMock.verify(() -> InsertIntoStatementParser.parse(ctx));
        }
    }

    @Test
    void shouldThrowParseExceptionForUnknownStatementStart() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        ParseException parseException = new ParseException("Unknown statement start: name", 0);
        Token identifier = token(TokenType.IDENTIFIER, "name", 0);

        when(ctx.current()).thenReturn(identifier, identifier);
        when(ctx.error("Unknown statement start: name")).thenReturn(parseException);

        ParseException thrown = assertThrows(ParseException.class, () -> StatementParser.parse(ctx));

        assertSame(parseException, thrown);
    }

    private static Token token(TokenType type, String lexeme, int position) {
        return new Token(type, lexeme, position);
    }
}
