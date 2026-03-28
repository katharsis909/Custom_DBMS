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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnDefinitionList;
import SEMANTIC.AST_NODES.CreateTableStatement;
import SEMANTIC.AST_NODES.DropTableStatement;
import SEMANTIC.AST_NODES.InsertIntoStatement;
import SEMANTIC.AST_NODES.SelectedColumnList;
import SEMANTIC.AST_NODES.SelectStatement;
import SEMANTIC.AST_NODES.ValueList;
import SEMANTIC.AST_NODES.WhereClause;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.PARSER.Exception.ParseException;
import SEMANTIC.PARSER.LEAF.IdentifierParser;
import SEMANTIC.PARSER.util.ParserContext;

class StatementBodyParserTest {

    @Test
    void shouldParseCreateTableStatement() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier tableName = identifier("students");
        ColumnDefinitionList columns = new ColumnDefinitionList(List.of(mock(ColumnDefinition.class)));

        when(ctx.current()).thenReturn(
                token(TokenType.CREATE, "CREATE", 0),
                token(TokenType.CREATE, "CREATE", 0),
                token(TokenType.TABLE, "TABLE", 7),
                token(TokenType.LPAREN, "(", 22),
                token(TokenType.RPAREN, ")", 30),
                token(TokenType.RPAREN, ")", 30));
        doNothing().when(ctx).advance();

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<ColumnDefinitionListParser> listMock = mockStatic(ColumnDefinitionListParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(tableName);
            listMock.when(() -> ColumnDefinitionListParser.parse(ctx)).thenReturn(columns);

            CreateTableStatement result = CreateTableStatementParser.parse(ctx);

            assertEquals(0, result.getSourcePosition());
            assertSame(tableName, result.getTableName());
            assertSame(columns, result.getColumns());
        }
    }

    @Test
    void shouldParseDropTableStatement() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier tableName = identifier("students");

        when(ctx.current()).thenReturn(
                token(TokenType.DROP, "DROP", 0),
                token(TokenType.DROP, "DROP", 0),
                token(TokenType.TABLE, "TABLE", 5));
        doNothing().when(ctx).advance();

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(tableName);

            DropTableStatement result = DropTableStatementParser.parse(ctx);

            assertEquals(0, result.getSourcePosition());
            assertSame(tableName, result.getTableName());
        }
    }

    @Test
    void shouldParseInsertIntoStatement() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier tableName = identifier("students");
        ValueList values = mock(ValueList.class);

        when(ctx.current()).thenReturn(
                token(TokenType.INSERT, "INSERT", 0),
                token(TokenType.INSERT, "INSERT", 0),
                token(TokenType.INTO, "INTO", 7),
                token(TokenType.LPAREN, "(", 20),
                token(TokenType.RPAREN, ")", 28),
                token(TokenType.RPAREN, ")", 28));
        doNothing().when(ctx).advance();

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<ValueListParser> valueListMock = mockStatic(ValueListParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(tableName);
            valueListMock.when(() -> ValueListParser.parse(ctx)).thenReturn(values);

            InsertIntoStatement result = InsertIntoStatementParser.parse(ctx);

            assertEquals(0, result.getSourcePosition());
            assertSame(tableName, result.getTableName());
            assertSame(values, result.getValueList());
        }
    }

    @Test
    void shouldParseSelectStatementWithWhereClause() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        SelectedColumnList selectedColumns = mock(SelectedColumnList.class);
        Identifier tableName = identifier("students");
        WhereClause whereClause = mock(WhereClause.class);

        when(ctx.current()).thenReturn(
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.FROM, "FROM", 12),
                token(TokenType.WHERE, "WHERE", 26));
        doNothing().when(ctx).advance();

        try (MockedStatic<SelectedColumnListParser> selectedMock = mockStatic(SelectedColumnListParser.class);
             MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<WhereClauseParser> whereMock = mockStatic(WhereClauseParser.class)) {
            selectedMock.when(() -> SelectedColumnListParser.parse(ctx)).thenReturn(selectedColumns);
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(tableName);
            whereMock.when(() -> WhereClauseParser.parse(ctx)).thenReturn(whereClause);

            SelectStatement result = SelectStatementParser.parse(ctx);

            assertEquals(0, result.getSourcePosition());
            assertSame(selectedColumns, result.getSelectedColumnList());
            assertSame(tableName, result.getTableName());
            assertSame(whereClause, result.getWhereClause());
        }
    }

    @Test
    void shouldParseSelectStatementWithoutWhereClause() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        SelectedColumnList selectedColumns = mock(SelectedColumnList.class);
        Identifier tableName = identifier("students");
        Token eof = token(TokenType.EOF, "", 20);

        when(ctx.current()).thenReturn(
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.SELECT, "SELECT", 0),
                token(TokenType.FROM, "FROM", 12),
                eof,
                eof);
        doNothing().when(ctx).advance();

        try (MockedStatic<SelectedColumnListParser> selectedMock = mockStatic(SelectedColumnListParser.class);
             MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class);
             MockedStatic<WhereClauseParser> whereMock = mockStatic(WhereClauseParser.class)) {
            selectedMock.when(() -> SelectedColumnListParser.parse(ctx)).thenReturn(selectedColumns);
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(tableName);

            SelectStatement result = SelectStatementParser.parse(ctx);

            assertSame(selectedColumns, result.getSelectedColumnList());
            assertSame(tableName, result.getTableName());
            assertNull(result.getWhereClause());
            whereMock.verifyNoInteractions();
        }
    }

    @Test
    void shouldThrowParseExceptionWhenCreateTableMissingOpeningParenthesis() throws Exception {
        ParserContext ctx = mock(ParserContext.class);
        Identifier tableName = identifier("students");
        Token invalid = token(TokenType.IDENTIFIER, "id", 22);
        ParseException parseException = new ParseException("Expected '(' after table name", 22);

        when(ctx.current()).thenReturn(
                token(TokenType.CREATE, "CREATE", 0),
                token(TokenType.CREATE, "CREATE", 0),
                token(TokenType.TABLE, "TABLE", 7),
                invalid,
                invalid);
        doNothing().when(ctx).advance();
        when(ctx.error("Expected '(' after table name")).thenReturn(parseException);

        try (MockedStatic<IdentifierParser> identifierMock = mockStatic(IdentifierParser.class)) {
            identifierMock.when(() -> IdentifierParser.parse(ctx)).thenReturn(tableName);

            ParseException thrown = assertThrows(ParseException.class, () -> CreateTableStatementParser.parse(ctx));

            assertSame(parseException, thrown);
        }
    }

    private static Token token(TokenType type, String lexeme, int position) {
        return new Token(type, lexeme, position);
    }

    private static Identifier identifier(String name) {
        Identifier identifier = new Identifier();
        identifier.setName(name);
        return identifier;
    }
}
