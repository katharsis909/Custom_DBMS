package SEMANTIC.AST_NODES;

import Converter.Main;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.LEAF_NODES.NumericLiteral;
import SEMANTIC.AST_NODES.LEAF_NODES.StringLiteral;
import STRUCTURE.Catalog;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.MyInt;
import STRUCTURE.MyString;
import STRUCTURE.Record;
import STRUCTURE.Table;
import dbmscli.result.ExecutionResult;
import dbmscli.result.QueryResultBlock;
import disk_persistence.TableIterator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AstNodeTest {

    @Test
    void leafAndMetadataNodesStoreValuesAndEvaluateLiterals() {
        Identifier identifier = new Identifier();
        identifier.setName("students");

        DataType dataType = new DataType();
        dataType.setDataType("INT");

        NumericLiteral numericLiteral = new NumericLiteral();
        numericLiteral.setValue(42);

        StringLiteral stringLiteral = new StringLiteral();
        stringLiteral.setValue("alice");

        Operator operator = new Operator();
        operator.setSymbol("=");

        assertEquals("students", identifier.getName());
        assertEquals("INT", dataType.getDataType());
        assertEquals("42", numericLiteral.evaluate().toString());
        assertEquals("alice", stringLiteral.evaluate().toString());
        assertTrue(operator.evaluate(MyInt.convtoDB_DT("42"), MyInt.convtoDB_DT("42")));
        assertFalse(operator.evaluate(MyInt.convtoDB_DT("42"), MyInt.convtoDB_DT("7")));
    }

    @Test
    void columnDefinitionAndListReturnConfiguredNodes() {
        ColumnDefinition columnDefinition = new ColumnDefinition();
        columnDefinition.setColumnName(identifier("id"));
        columnDefinition.setDataType(dataType("INT"));

        ColumnDefinitionList columnDefinitionList = new ColumnDefinitionList(List.of(columnDefinition));

        assertSame(columnDefinition, columnDefinition.evaluate());
        assertEquals("INT", columnDefinition.getColumnDataType());
        assertSame(columnDefinitionList, columnDefinitionList.evaluate());
        assertEquals(List.of(columnDefinition), columnDefinitionList.getColumnList());
    }

    @Test
    void columnMentionReadsColumnValueFromRecord() throws Exception {
        Record record = new Record();
        record.setValue("name", MyString.convtoDB_DT("alice"));

        ColumnMention columnMention = new ColumnMention();
        columnMention.setColumnName(identifier("name"));

        assertEquals("alice", columnMention.evaluate(record).toString());
    }

    @Test
    void columnMentionWrapsMissingColumnErrorsWithItsSourcePosition() {
        ColumnMention columnMention = new ColumnMention();
        columnMention.setColumnName(identifier("missing"));
        columnMention.setSourcePosition(9);

        DBMSException exception = assertThrows(DBMSException.class, () -> columnMention.evaluate(new Record()));

        assertEquals("Column 'missing' not found in Table.", exception.getMessage());
        assertEquals(9, exception.getPosition());
    }

    @Test
    void unaryConditionUsesRecordValueAndOperator() throws Exception {
        Record record = new Record();
        record.setValue("age", MyInt.convtoDB_DT("21"));

        UnaryCondition condition = new UnaryCondition();
        condition.setColumnName(identifier("age"));
        condition.setOperator(operator("="));
        condition.setValue(numericLiteral(21));

        assertTrue(condition.evaluate(record));
    }

    @Test
    void unaryConditionWrapsMissingColumnErrorsWithItsSourcePosition() {
        UnaryCondition condition = new UnaryCondition();
        condition.setColumnName(identifier("age"));
        condition.setOperator(operator("="));
        condition.setValue(numericLiteral(21));
        condition.setSourcePosition(17);

        DBMSException exception = assertThrows(DBMSException.class, () -> condition.evaluate(new Record()));

        assertEquals("Column 'age' not found in Table.", exception.getMessage());
        assertEquals(17, exception.getPosition());
    }

    @Test
    void conditionListShortCircuitsOnFirstFalseCondition() throws Exception {
        UnaryCondition first = mock(UnaryCondition.class);
        UnaryCondition second = mock(UnaryCondition.class);
        Record record = new Record();

        when(first.evaluate(record)).thenReturn(false);

        ConditionList conditionList = new ConditionList(List.of(first, second));

        assertFalse(conditionList.evaluate(record));
        verify(first).evaluate(record);
        verify(second, never()).evaluate(record);
    }

    @Test
    void whereClauseDelegatesToConditionList() throws Exception {
        ConditionList conditionList = mock(ConditionList.class);
        Record record = new Record();
        when(conditionList.evaluate(record)).thenReturn(true);

        WhereClause whereClause = new WhereClause();
        whereClause.setConditions(conditionList);

        assertTrue(whereClause.evaluate(record));
        verify(conditionList).evaluate(record);
    }

    @Test
    void valueEvaluatesLiteralAgainstColumnType() throws Exception {
        Value value = new Value();
        value.setLiteral(stringLiteral("alice"));

        DBMSDataType result = value.evaluate(new Column("name", "STRING"));

        assertEquals("alice", result.toString());
    }

    @Test
    void valueUsesSourcePositionForTypeMismatch() {
        Value value = new Value();
        value.setLiteral(stringLiteral("alice"));
        value.setSourcePosition(21);

        DBMSException exception = assertThrows(DBMSException.class, () -> value.evaluate(new Column("id", "INT")));

        assertTrue(exception.getMessage().contains("does not matches the data type INT"));
        assertEquals(21, exception.getPosition());
    }

    @Test
    void valueListEvaluatesEachValueAgainstTheMatchingColumn() throws Exception {
        ValueList valueList = new ValueList();
        valueList.setValues(List.of(value(numericLiteral(1)), value(stringLiteral("alice"))));

        List<DBMSDataType> result = valueList.evaluate(List.of(new Column("id", "INT"), new Column("name", "STRING")));

        assertEquals(List.of("1", "alice"), result.stream().map(DBMSDataType::toString).toList());
    }

    @Test
    void valueListReportsMismatchedLengthsUsingItsSourcePosition() {
        ValueList valueList = new ValueList();
        valueList.setValues(List.of(value(numericLiteral(1))));
        valueList.setSourcePosition(23);

        DBMSException exception = assertThrows(
                DBMSException.class,
                () -> valueList.evaluate(List.of(new Column("id", "INT"), new Column("name", "STRING")))
        );

        assertEquals("Inserted Values length - 1 do not match Table columns length - 2.", exception.getMessage());
        assertEquals(23, exception.getPosition());
    }

    @Test
    void selectedColumnListSupportsSelectAllAndExplicitColumns() throws Exception {
        Record record = new Record();
        record.setValue("id", MyInt.convtoDB_DT("1"));
        record.setValue("name", MyString.convtoDB_DT("alice"));

        Table table = mock(Table.class);
        when(table.getValueFromRecord(record)).thenReturn(List.of(MyInt.convtoDB_DT("1"), MyString.convtoDB_DT("alice")));

        SelectedColumnList selectAll = new SelectedColumnList();
        selectAll.setSelectAll(true);

        assertEquals(List.of("1", "alice"), selectAll.evaluate(record, table).stream().map(DBMSDataType::toString).toList());

        SelectedColumnList explicit = new SelectedColumnList();
        explicit.setColumns(List.of(columnMention("name")));
        explicit.setSelectAll(false);

        assertEquals(List.of("alice"), explicit.evaluate(record, table).stream().map(DBMSDataType::toString).toList());
    }

    @Test
    void statementEvaluatePrintsOnlyNonEmptyResults() throws Exception {
        Catalog catalog = mock(Catalog.class);
        Statement statement = new TestStatement(QueryResultBlock.message("hello"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output));
        try {
            statement.evaluate(catalog);
        } finally {
            System.setOut(originalOut);
        }

        assertEquals("hello" + System.lineSeparator(), output.toString());
        assertEquals(-1, statement.getSourcePosition());
        statement.setSourcePosition(33);
        assertEquals(33, statement.getSourcePosition());
    }

    @Test
    void statementListDelegatesEvaluateAndCollectsNonEmptyExecutionBlocks() throws Exception {
        Catalog catalog = mock(Catalog.class);
        Statement first = mock(Statement.class);
        Statement second = mock(Statement.class);

        QueryResultBlock tableBlock = QueryResultBlock.table(List.of("name"), List.of(List.of("alice")));
        when(first.execute(catalog)).thenReturn(tableBlock);
        when(second.execute(catalog)).thenReturn(QueryResultBlock.message(""));

        StatementList statementList = new StatementList(List.of(first, second));

        statementList.evaluate(catalog);
        ExecutionResult result = statementList.execute(catalog);

        verify(first).evaluate(catalog);
        verify(second).evaluate(catalog);
        assertEquals(1, result.getBlocks().size());
        assertEquals(tableBlock.renderText(), result.getBlocks().get(0).renderText());
    }

    @Test
    void createTableStatementDelegatesToCatalogUsingConvertedColumns() throws Exception {
        Catalog catalog = mock(Catalog.class);
        ColumnDefinitionList definitions = new ColumnDefinitionList(List.of(columnDefinition("id", "INT")));
        List<Column> convertedColumns = List.of(new Column("id", "INT"));

        CreateTableStatement statement = new CreateTableStatement();
        statement.setTableName(identifier("students"));
        statement.setColumns(definitions);

        try (MockedStatic<Main> converter = mockStatic(Main.class)) {
            converter.when(() -> Main.toColumnListFromDefinition(definitions.getColumnList())).thenReturn(convertedColumns);

            assertNull(statement.execute(catalog));

            verify(catalog).addTable("students", convertedColumns);
        }
    }

    @Test
    void createTableStatementAddsSourcePositionToCatalogErrors() throws Exception {
        Catalog catalog = mock(Catalog.class);
        ColumnDefinitionList definitions = new ColumnDefinitionList(List.of(columnDefinition("id", "INT")));
        List<Column> convertedColumns = List.of(new Column("id", "INT"));

        CreateTableStatement statement = new CreateTableStatement();
        statement.setTableName(identifier("students"));
        statement.setColumns(definitions);
        statement.setSourcePosition(40);

        try (MockedStatic<Main> converter = mockStatic(Main.class)) {
            converter.when(() -> Main.toColumnListFromDefinition(definitions.getColumnList())).thenReturn(convertedColumns);
            doThrow(new DBMSException("duplicate table")).when(catalog).addTable("students", convertedColumns);

            DBMSException exception = assertThrows(DBMSException.class, () -> statement.execute(catalog));

            assertEquals("duplicate table", exception.getMessage());
            assertEquals(40, exception.getPosition());
        }
    }

    @Test
    void dropTableStatementDelegatesToCatalogAndPreservesExistingErrorPositions() throws Exception {
        Catalog catalog = mock(Catalog.class);

        DropTableStatement statement = new DropTableStatement();
        statement.setTableName(identifier("students"));
        statement.setSourcePosition(50);

        assertNull(statement.execute(catalog));
        verify(catalog).dropTable("students");

        doThrow(new DBMSException("missing table", 7)).when(catalog).dropTable("students");

        DBMSException exception = assertThrows(DBMSException.class, () -> statement.execute(catalog));

        assertEquals("missing table", exception.getMessage());
        assertEquals(7, exception.getPosition());
    }

    @Test
    void insertIntoStatementDelegatesValueEvaluationAndRecordInsert() throws Exception {
        Catalog catalog = mock(Catalog.class);
        Table table = mock(Table.class);
        ValueList valueList = mock(ValueList.class);
        List<Column> columns = List.of(new Column("id", "INT"));
        List<DBMSDataType> values = List.of(MyInt.convtoDB_DT("1"));

        when(catalog.getTable("students")).thenReturn(table);
        when(table.getColumnList()).thenReturn(columns);
        when(valueList.evaluate(columns)).thenReturn(values);

        InsertIntoStatement statement = new InsertIntoStatement();
        statement.setTableName(identifier("students"));
        statement.setValueList(valueList);

        assertNull(statement.execute(catalog));
        verify(table).addRecord(values);
    }

    @Test
    void insertIntoStatementAddsSourcePositionToErrorsWithoutOne() throws Exception {
        Catalog catalog = mock(Catalog.class);
        when(catalog.getTable("students")).thenThrow(new DBMSException("missing table"));

        InsertIntoStatement statement = new InsertIntoStatement();
        statement.setTableName(identifier("students"));
        statement.setValueList(mock(ValueList.class));
        statement.setSourcePosition(61);

        DBMSException exception = assertThrows(DBMSException.class, () -> statement.execute(catalog));

        assertEquals("missing table", exception.getMessage());
        assertEquals(61, exception.getPosition());
    }

    @Test
    void selectStatementBuildsRowsAndHeadersForExplicitColumns() throws Exception {
        Catalog catalog = mock(Catalog.class);
        Table table = mock(Table.class);
        TableIterator iterator = mock(TableIterator.class);
        SelectedColumnList selectedColumnList = mock(SelectedColumnList.class);
        WhereClause whereClause = mock(WhereClause.class);
        Record firstRecord = new Record();
        Record secondRecord = new Record();

        when(catalog.getTable("students")).thenReturn(table);
        when(table.iterator()).thenReturn(iterator);
        when(iterator.hasNext()).thenReturn(true, true, false);
        when(iterator.next()).thenReturn(firstRecord, secondRecord);
        when(whereClause.evaluate(firstRecord)).thenReturn(true);
        when(whereClause.evaluate(secondRecord)).thenReturn(false);
        when(selectedColumnList.evaluate(firstRecord, table)).thenReturn(List.of(MyString.convtoDB_DT("alice")));
        when(selectedColumnList.getColumns()).thenReturn(List.of(columnMention("name")));

        SelectStatement statement = new SelectStatement();
        statement.setTableName(identifier("students"));
        statement.setSelectedColumnList(selectedColumnList);
        statement.setWhereClause(whereClause);

        QueryResultBlock result = statement.execute(catalog);

        assertEquals(List.of("name"), result.getColumns());
        assertEquals(List.of(List.of("alice")), result.getRows());
    }

    @Test
    void selectStatementBuildsSelectAllHeadersWhenColumnListIsNull() throws Exception {
        Catalog catalog = mock(Catalog.class);
        Table table = mock(Table.class);
        TableIterator iterator = mock(TableIterator.class);
        SelectedColumnList selectedColumnList = mock(SelectedColumnList.class);
        Record record = new Record();

        when(catalog.getTable("students")).thenReturn(table);
        when(table.iterator()).thenReturn(iterator);
        when(table.getColumnList()).thenReturn(List.of(new Column("id", "INT"), new Column("name", "STRING")));
        when(iterator.hasNext()).thenReturn(true, false);
        when(iterator.next()).thenReturn(record);
        when(selectedColumnList.evaluate(record, table)).thenReturn(List.of(MyInt.convtoDB_DT("1"), MyString.convtoDB_DT("alice")));
        when(selectedColumnList.getColumns()).thenReturn(null);

        SelectStatement statement = new SelectStatement();
        statement.setTableName(identifier("students"));
        statement.setSelectedColumnList(selectedColumnList);
        statement.setWhereClause(null);

        QueryResultBlock result = statement.execute(catalog);

        assertEquals(List.of("id", "name"), result.getColumns());
        assertEquals(List.of(List.of("1", "alice")), result.getRows());
    }

    @Test
    void selectStatementAddsSourcePositionToErrorsWithoutOne() throws Exception {
        Catalog catalog = mock(Catalog.class);
        when(catalog.getTable("students")).thenThrow(new DBMSException("missing table"));

        SelectStatement statement = new SelectStatement();
        statement.setTableName(identifier("students"));
        statement.setSelectedColumnList(mock(SelectedColumnList.class));
        statement.setSourcePosition(72);

        DBMSException exception = assertThrows(DBMSException.class, () -> statement.execute(catalog));

        assertEquals("missing table", exception.getMessage());
        assertEquals(72, exception.getPosition());
    }

    private static Identifier identifier(String name) {
        Identifier identifier = new Identifier();
        identifier.setName(name);
        return identifier;
    }

    private static DataType dataType(String type) {
        DataType dataType = new DataType();
        dataType.setDataType(type);
        return dataType;
    }

    private static Operator operator(String symbol) {
        Operator operator = new Operator();
        operator.setSymbol(symbol);
        return operator;
    }

    private static NumericLiteral numericLiteral(int literalValue) {
        NumericLiteral literal = new NumericLiteral();
        literal.setValue(literalValue);
        return literal;
    }

    private static StringLiteral stringLiteral(String literalValue) {
        StringLiteral literal = new StringLiteral();
        literal.setValue(literalValue);
        return literal;
    }

    private static Value value(SEMANTIC.AST_NODES.LEAF_NODES.Literal literal) {
        Value value = new Value();
        value.setLiteral(literal);
        return value;
    }

    private static ColumnMention columnMention(String name) {
        ColumnMention columnMention = new ColumnMention();
        columnMention.setColumnName(identifier(name));
        return columnMention;
    }

    private static ColumnDefinition columnDefinition(String name, String type) {
        ColumnDefinition columnDefinition = new ColumnDefinition();
        columnDefinition.setColumnName(identifier(name));
        columnDefinition.setDataType(dataType(type));
        return columnDefinition;
    }

    private static class TestStatement extends Statement {
        private final QueryResultBlock result;

        private TestStatement(QueryResultBlock result) {
            this.result = result;
        }

        @Override
        public QueryResultBlock execute(Catalog catalog) {
            return result;
        }
    }
}
