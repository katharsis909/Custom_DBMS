package benchmark;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.ConditionList;
import SEMANTIC.AST_NODES.CreateTableStatement;
import SEMANTIC.AST_NODES.DataType;
import SEMANTIC.AST_NODES.InsertIntoStatement;
import SEMANTIC.AST_NODES.Operator;
import SEMANTIC.AST_NODES.SelectStatement;
import SEMANTIC.AST_NODES.SelectedColumnList;
import SEMANTIC.AST_NODES.UnaryCondition;
import SEMANTIC.AST_NODES.Value;
import SEMANTIC.AST_NODES.ValueList;
import SEMANTIC.AST_NODES.WhereClause;
import SEMANTIC.AST_NODES.ColumnDefinition;
import SEMANTIC.AST_NODES.ColumnDefinitionList;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.LEAF_NODES.NumericLiteral;
import SEMANTIC.AST_NODES.LEAF_NODES.StringLiteral;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSException;
import STRUCTURE.MyInt;
import STRUCTURE.MyString;
import STRUCTURE.Table;
import dbmscli.result.QueryResultBlock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class StatementBenchmark {

    @Benchmark
    public void selectAll(SelectState state, Blackhole blackhole) throws DBMSException {
        QueryResultBlock result = state.selectAll.execute(state.catalog);
        blackhole.consume(result.getRows().size());
    }

    @Benchmark
    public void selectSingleColumn(SelectState state, Blackhole blackhole) throws DBMSException {
        QueryResultBlock result = state.selectSingleColumn.execute(state.catalog);
        blackhole.consume(result.getRows().size());
    }

    @Benchmark
    public void selectWhereSingleMatch(SelectState state, Blackhole blackhole) throws DBMSException {
        QueryResultBlock result = state.selectWhereSingleMatch.execute(state.catalog);
        blackhole.consume(result.getRows().size());
    }

    @Benchmark
    public void selectWhereNoMatch(SelectState state, Blackhole blackhole) throws DBMSException {
        QueryResultBlock result = state.selectWhereNoMatch.execute(state.catalog);
        blackhole.consume(result.getRows().size());
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void insert100Rows(InsertState state, Blackhole blackhole) throws DBMSException {
        for (InsertIntoStatement statement : state.insertStatements) {
            statement.execute(state.catalog);
        }
        blackhole.consume(state.insertStatements.size());
    }

    @State(Scope.Thread)
    public static class SelectState {
        @Param({"10000", "100000"})
        public int rowCount;

        private Catalog catalog;
        private String tableName;
        private Path tableDir;
        private SelectStatement selectAll;
        private SelectStatement selectSingleColumn;
        private SelectStatement selectWhereSingleMatch;
        private SelectStatement selectWhereNoMatch;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            tableName = uniqueTableName("bench_select_" + rowCount);
            tableDir = Path.of("data", tableName);
            deleteRecursively(tableDir);

            catalog = new Catalog();
            createStudentsTable(catalog, tableName);
            preloadRows(catalog.getTable(tableName), rowCount);

            selectAll = buildSelect(tableName, true, null, null);
            selectSingleColumn = buildSelect(tableName, false, List.of("name"), null);
            selectWhereSingleMatch = buildSelect(tableName, true, null, equalityCondition("id", rowCount / 2));
            selectWhereNoMatch = buildSelect(tableName, true, null, equalityCondition("id", rowCount + 1));
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            deleteRecursively(tableDir);
        }
    }

    @State(Scope.Thread)
    public static class InsertState {
        private Catalog catalog;
        private String tableName;
        private Path tableDir;
        private List<InsertIntoStatement> insertStatements;

        @Setup(Level.Invocation)
        public void setup() throws Exception {
            tableName = uniqueTableName("bench_insert");
            tableDir = Path.of("data", tableName);
            deleteRecursively(tableDir);

            catalog = new Catalog();
            createStudentsTable(catalog, tableName);
            insertStatements = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                insertStatements.add(buildInsert(tableName, i));
            }
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws IOException {
            deleteRecursively(tableDir);
        }
    }

    private static void createStudentsTable(Catalog catalog, String tableName) throws DBMSException {
        CreateTableStatement create = new CreateTableStatement();
        create.setTableName(identifier(tableName));
        create.setColumns(new ColumnDefinitionList(List.of(
                columnDefinition("id", "INT"),
                columnDefinition("name", "STRING")
        )));
        create.execute(catalog);
    }

    private static void preloadRows(Table table, int rowCount) throws DBMSException {
        for (int i = 1; i <= rowCount; i++) {
            table.addRecord(List.of(
                    MyInt.convtoDB_DT(Integer.toString(i)),
                    MyString.convtoDB_DT(paddedName(i))
            ));
        }
    }

    private static InsertIntoStatement buildInsert(String tableName, int id) {
        InsertIntoStatement insert = new InsertIntoStatement();
        insert.setTableName(identifier(tableName));

        Value idValue = new Value();
        idValue.setLiteral(numericLiteral(id));

        Value nameValue = new Value();
        nameValue.setLiteral(stringLiteral(paddedName(id)));

        ValueList values = new ValueList();
        values.setValues(List.of(idValue, nameValue));
        insert.setValueList(values);
        return insert;
    }

    private static SelectStatement buildSelect(String tableName, boolean selectAllColumns, List<String> columns, WhereClause whereClause) {
        SelectStatement select = new SelectStatement();
        select.setTableName(identifier(tableName));
        select.setSelectedColumnList(selectedColumns(selectAllColumns, columns));
        select.setWhereClause(whereClause);
        return select;
    }

    private static SelectedColumnList selectedColumns(boolean selectAllColumns, List<String> columns) {
        SelectedColumnList selected = new SelectedColumnList();
        selected.setSelectAll(selectAllColumns);
        if (!selectAllColumns) {
            List<ColumnMention> mentions = new ArrayList<>();
            for (String column : columns) {
                ColumnMention mention = new ColumnMention();
                mention.setColumnName(identifier(column));
                mentions.add(mention);
            }
            selected.setColumns(mentions);
        }
        return selected;
    }

    private static WhereClause equalityCondition(String columnName, int expectedValue) {
        UnaryCondition condition = new UnaryCondition();
        condition.setColumnName(identifier(columnName));
        condition.setOperator(operator("="));
        condition.setValue(numericLiteral(expectedValue));

        ConditionList conditions = new ConditionList(List.of(condition));
        WhereClause whereClause = new WhereClause();
        whereClause.setConditions(conditions);
        return whereClause;
    }

    private static ColumnDefinition columnDefinition(String name, String type) {
        ColumnDefinition definition = new ColumnDefinition();
        definition.setColumnName(identifier(name));

        DataType dataType = new DataType();
        dataType.setDataType(type);
        definition.setDataType(dataType);
        return definition;
    }

    private static Identifier identifier(String name) {
        Identifier identifier = new Identifier();
        identifier.setName(name);
        return identifier;
    }

    private static NumericLiteral numericLiteral(int value) {
        NumericLiteral literal = new NumericLiteral();
        literal.setValue(value);
        return literal;
    }

    private static StringLiteral stringLiteral(String value) {
        StringLiteral literal = new StringLiteral();
        literal.setValue(value);
        return literal;
    }

    private static Operator operator(String symbol) {
        Operator operator = new Operator();
        operator.setSymbol(symbol);
        return operator;
    }

    private static String paddedName(int value) {
        return String.format("n%014d", value);
    }

    private static String uniqueTableName(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            }
            throw ex;
        }
    }
}
