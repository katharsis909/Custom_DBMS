package dbmscli;

import STRUCTURE.DBMSException;
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

class GroupByAliasTest {

    @Test
    void joinSupportsTableAliasesAndAliasQualifiedColumns() throws Exception {
        String authors = uniqueTableName("alias_authors");
        String books = uniqueTableName("alias_books");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + authors + " (id INT PRIMARY KEY, name STRING);");
            engine.execute("CREATE TABLE " + books + " (id INT PRIMARY KEY, author_id INT, title STRING);");
            engine.execute("INSERT INTO " + authors + " (1, 'ada');");
            engine.execute("INSERT INTO " + books + " (10, 1, 'indexes');");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT a.name, b.title FROM " + authors + " AS a"
                            + " JOIN " + books + " b ON a.id = b.author_id;");

            assertEquals(List.of("a.name", "b.title"), result.getColumns());
            assertEquals(List.of(List.of("ada", "indexes")), result.getRows());
        } finally {
            deleteRecursively(tableDir(books));
            deleteRecursively(tableDir(authors));
        }
    }

    @Test
    void groupBySupportsAggregateAliasesAndDefaultAggregateNames() throws Exception {
        String sales = uniqueTableName("sales");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + sales + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + sales + " (1, 'book', 10);");
            engine.execute("INSERT INTO " + sales + " (2, 'book', 15);");
            engine.execute("INSERT INTO " + sales + " (3, 'pen', 3);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT category, COUNT(*) AS total, SUM(amount) FROM " + sales + " GROUP BY category;");

            assertEquals(List.of("category", "total", "agg2"), result.getColumns());
            assertEquals(List.of(List.of("book", "2", "25"), List.of("pen", "1", "3")), result.getRows());
        } finally {
            deleteRecursively(tableDir(sales));
        }
    }

    @Test
    void groupByRejectsColumnsThatAreNotGroupedOrAggregated() throws Exception {
        String sales = uniqueTableName("sales_reject");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + sales + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + sales + " (1, 'book', 10);");

            DBMSException exception = assertThrows(
                    DBMSException.class,
                    () -> engine.execute("SELECT category, amount, COUNT(*) FROM " + sales + " GROUP BY category;")
            );

            assertEquals("Column 'amount' must appear in GROUP BY or be used inside an aggregate function.", exception.getMessage());
        } finally {
            deleteRecursively(tableDir(sales));
        }
    }

    @Test
    void groupBySupportsHavingWithAggregatePredicates() throws Exception {
        String sales = uniqueTableName("sales_having");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + sales + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + sales + " (1, 'book', 10);");
            engine.execute("INSERT INTO " + sales + " (2, 'book', 15);");
            engine.execute("INSERT INTO " + sales + " (3, 'pen', 3);");

            QueryResultBlock countResult = onlyBlock(engine,
                    "SELECT category, COUNT(*) AS total FROM " + sales
                            + " GROUP BY category HAVING COUNT(*) > 1;");
            QueryResultBlock sumResult = onlyBlock(engine,
                    "SELECT category, SUM(amount) AS total_amount FROM " + sales
                            + " GROUP BY category HAVING SUM(amount) >= 20;");

            assertEquals(List.of(List.of("book", "2")), countResult.getRows());
            assertEquals(List.of(List.of("book", "25")), sumResult.getRows());
        } finally {
            deleteRecursively(tableDir(sales));
        }
    }

    private static QueryResultBlock onlyBlock(DbmsCliEngine engine, String sql) throws Exception {
        var result = engine.executeStructured(sql);
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
