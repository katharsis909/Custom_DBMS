package dbmscli;

import dbmscli.result.QueryResultBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderByTest {

    @Test
    void selectOrdersBySingleColumnDescending() throws Exception {
        String table = uniqueTableName("order_single");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, name STRING, score INT);");
            engine.execute("INSERT INTO " + table + " (1, 'ada', 30);");
            engine.execute("INSERT INTO " + table + " (2, 'grace', 50);");
            engine.execute("INSERT INTO " + table + " (3, 'linus', 40);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT name, score FROM " + table + " ORDER BY score DESC;");

            assertEquals(List.of(List.of("grace", "50"), List.of("linus", "40"), List.of("ada", "30")),
                    result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void selectOrdersByMultipleColumns() throws Exception {
        String table = uniqueTableName("order_multi");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + table + " (1, 'hardware', 20);");
            engine.execute("INSERT INTO " + table + " (2, 'book', 10);");
            engine.execute("INSERT INTO " + table + " (3, 'hardware', 50);");
            engine.execute("INSERT INTO " + table + " (4, 'book', 30);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT category, amount FROM " + table + " ORDER BY category ASC, amount DESC;");

            assertEquals(List.of(
                    List.of("book", "30"),
                    List.of("book", "10"),
                    List.of("hardware", "50"),
                    List.of("hardware", "20")
            ), result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void selectCanUseIndexedColumnForOrderByWithWhereFilter() throws Exception {
        String table = uniqueTableName("order_index");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, name STRING, score INT);");
            engine.execute("CREATE INDEX score_idx_" + System.nanoTime() + " ON " + table + " (score);");
            engine.execute("INSERT INTO " + table + " (1, 'ada', 30);");
            engine.execute("INSERT INTO " + table + " (2, 'grace', 50);");
            engine.execute("INSERT INTO " + table + " (3, 'linus', 40);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT name, score FROM " + table + " WHERE score >= 30 AND score <= 50 ORDER BY score ASC;");

            assertEquals(List.of(List.of("ada", "30"), List.of("linus", "40"), List.of("grace", "50")),
                    result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void selectCanUseMixedDirectionIndexForOrderBy() throws Exception {
        String table = uniqueTableName("order_mixed_idx");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, age INT, name STRING);");
            engine.execute("CREATE INDEX age_name_idx_" + System.nanoTime() + " ON " + table + " (age ASC, name DESC);");
            engine.execute("INSERT INTO " + table + " (1, 20, 'bob');");
            engine.execute("INSERT INTO " + table + " (2, 20, 'charlie');");
            engine.execute("INSERT INTO " + table + " (3, 10, 'alice');");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT age, name FROM " + table + " ORDER BY age ASC, name DESC;");

            assertEquals(List.of(
                    List.of("10", "alice"),
                    List.of("20", "charlie"),
                    List.of("20", "bob")
            ), result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void selectCanUseReverseScanOnMixedDirectionIndexForOrderBy() throws Exception {
        String table = uniqueTableName("order_mixed_rev_idx");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, age INT, name STRING);");
            engine.execute("CREATE INDEX age_name_idx_" + System.nanoTime() + " ON " + table + " (age ASC, name DESC);");
            engine.execute("INSERT INTO " + table + " (1, 20, 'bob');");
            engine.execute("INSERT INTO " + table + " (2, 20, 'charlie');");
            engine.execute("INSERT INTO " + table + " (3, 10, 'alice');");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT age, name FROM " + table + " ORDER BY age DESC, name ASC;");

            assertEquals(List.of(
                    List.of("20", "bob"),
                    List.of("20", "charlie"),
                    List.of("10", "alice")
            ), result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void joinOrdersByQualifiedAliasColumns() throws Exception {
        String authors = uniqueTableName("order_authors");
        String books = uniqueTableName("order_books");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + authors + " (id INT PRIMARY KEY, name STRING);");
            engine.execute("CREATE TABLE " + books + " (id INT PRIMARY KEY, author_id INT, title STRING);");
            engine.execute("INSERT INTO " + authors + " (1, 'ada');");
            engine.execute("INSERT INTO " + authors + " (2, 'grace');");
            engine.execute("INSERT INTO " + books + " (100, 1, 'zeta');");
            engine.execute("INSERT INTO " + books + " (101, 1, 'alpha');");
            engine.execute("INSERT INTO " + books + " (102, 2, 'compiler');");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT a.name, b.title FROM " + authors + " AS a"
                            + " JOIN " + books + " AS b ON a.id = b.author_id"
                            + " ORDER BY a.name ASC, b.title DESC;");

            assertEquals(List.of(
                    List.of("ada", "zeta"),
                    List.of("ada", "alpha"),
                    List.of("grace", "compiler")
            ), result.getRows());
        } finally {
            deleteRecursively(tableDir(books));
            deleteRecursively(tableDir(authors));
        }
    }

    @Test
    void groupedSelectOrdersByGroupedColumn() throws Exception {
        String table = uniqueTableName("order_group");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + table + " (1, 'hardware', 20);");
            engine.execute("INSERT INTO " + table + " (2, 'book', 10);");
            engine.execute("INSERT INTO " + table + " (3, 'hardware', 50);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT category, COUNT(*) FROM " + table + " GROUP BY category ORDER BY category DESC;");

            assertEquals(List.of(List.of("hardware", "2"), List.of("book", "1")), result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void groupedSelectOrdersByAggregateFunction() throws Exception {
        String table = uniqueTableName("order_agg_fn");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + table + " (1, 'hardware', 20);");
            engine.execute("INSERT INTO " + table + " (2, 'book', 10);");
            engine.execute("INSERT INTO " + table + " (3, 'hardware', 50);");
            engine.execute("INSERT INTO " + table + " (4, 'hardware', 10);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT category, COUNT(*) FROM " + table + " GROUP BY category ORDER BY COUNT(*) DESC;");

            assertEquals(List.of(List.of("hardware", "3"), List.of("book", "1")), result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
        }
    }

    @Test
    void groupedSelectOrdersByAggregateAlias() throws Exception {
        String table = uniqueTableName("order_agg_alias");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, category STRING, amount INT);");
            engine.execute("INSERT INTO " + table + " (1, 'hardware', 20);");
            engine.execute("INSERT INTO " + table + " (2, 'book', 100);");
            engine.execute("INSERT INTO " + table + " (3, 'hardware', 50);");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT category, SUM(amount) AS total FROM " + table + " GROUP BY category ORDER BY total ASC;");

            assertEquals(List.of(List.of("hardware", "70"), List.of("book", "100")), result.getRows());
        } finally {
            deleteRecursively(tableDir(table));
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
