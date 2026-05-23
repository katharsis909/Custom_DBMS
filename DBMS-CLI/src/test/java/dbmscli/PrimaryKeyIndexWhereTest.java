package dbmscli;

import dbmscli.result.ExecutionResult;
import dbmscli.result.QueryResultBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrimaryKeyIndexWhereTest {

    @Test
    void selectsWithPrimaryKeyEqualityAndInequalityWhereClauses() throws Exception {
        String tableName = uniqueTableName("pk_where");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + tableName + " (id INT PRIMARY KEY, age INT, name STRING);");
            engine.execute("INSERT INTO " + tableName + " (1, 12, 'ada');");
            engine.execute("INSERT INTO " + tableName + " (2, 20, 'grace');");
            engine.execute("INSERT INTO " + tableName + " (3, 35, 'linus');");

            QueryResultBlock equality = onlyBlock(engine.executeStructured(
                    "SELECT name FROM " + tableName + " WHERE id = 2;"
            ));
            QueryResultBlock range = onlyBlock(engine.executeStructured(
                    "SELECT name FROM " + tableName + " WHERE id > 1 AND id < 3;"
            ));

            assertEquals(List.of(List.of("grace")), equality.getRows());
            assertEquals(List.of(List.of("grace")), range.getRows());
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void createIndexSupportsRangeWhereClauses() throws Exception {
        String tableName = uniqueTableName("idx_where");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + tableName + " (id INT PRIMARY KEY, age INT, name STRING);");
            engine.execute("INSERT INTO " + tableName + " (1, 12, 'ada');");
            engine.execute("INSERT INTO " + tableName + " (2, 20, 'grace');");
            engine.execute("INSERT INTO " + tableName + " (3, 35, 'linus');");
            engine.execute("CREATE INDEX age_idx_" + System.nanoTime() + " ON " + tableName + " (age);");

            QueryResultBlock result = onlyBlock(engine.executeStructured(
                    "SELECT name FROM " + tableName + " WHERE age > 10 AND age < 30;"
            ));

            assertEquals(List.of(List.of("ada"), List.of("grace")), result.getRows());
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void createIndexReportsMissingColumnsAndDuplicateIndexNames() throws Exception {
        String tableName = uniqueTableName("idx_errors");
        String indexName = "idx_errors_" + System.nanoTime();
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + tableName + " (id INT PRIMARY KEY, age INT);");
            engine.execute("CREATE INDEX " + indexName + " ON " + tableName + " (age);");

            STRUCTURE.DBMSException duplicate = assertThrows(
                    STRUCTURE.DBMSException.class,
                    () -> engine.execute("CREATE INDEX " + indexName + " ON " + tableName + " (age);")
            );
            STRUCTURE.DBMSException missingColumn = assertThrows(
                    STRUCTURE.DBMSException.class,
                    () -> engine.execute("CREATE INDEX " + indexName + "_missing ON " + tableName + " (missing);")
            );

            assertEquals("Index '" + indexName + "' already exists.", duplicate.getMessage());
            assertEquals("Column 'missing' does not exist.", missingColumn.getMessage());
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    private static QueryResultBlock onlyBlock(ExecutionResult result) {
        assertEquals(1, result.getBlocks().size());
        return result.getBlocks().get(0);
    }

    private static String uniqueTableName(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private static Path tableDir(String tableName) {
        return Path.of("data", tableName);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
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
