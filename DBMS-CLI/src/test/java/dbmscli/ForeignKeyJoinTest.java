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

class ForeignKeyJoinTest {

    @Test
    void foreignKeyInsertRequiresReferencedParentValue() throws Exception {
        String parent = uniqueTableName("fk_parent");
        String child = uniqueTableName("fk_child");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + parent + " (id INT PRIMARY KEY);");
            engine.execute("CREATE TABLE " + child + " (id INT PRIMARY KEY, parent_id INT REFERENCES " + parent + "(id));");

            DBMSException missingParent = assertThrows(
                    DBMSException.class,
                    () -> engine.execute("INSERT INTO " + child + " (1, 999);")
            );

            engine.execute("INSERT INTO " + parent + " (999);");
            engine.execute("INSERT INTO " + child + " (1, 999);");

            QueryResultBlock result = onlyBlock(engine, "SELECT parent_id FROM " + child + " WHERE id = 1;");
            assertEquals("Foreign key violation on column 'parent_id': value '999' does not exist in "
                    + parent + ".id.", missingParent.getMessage());
            assertEquals(List.of(List.of("999")), result.getRows());
        } finally {
            deleteRecursively(tableDir(child));
            deleteRecursively(tableDir(parent));
        }
    }

    @Test
    void foreignKeyCreateTableReportsMissingReferencedTable() throws Exception {
        String child = uniqueTableName("fk_missing");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();

            DBMSException exception = assertThrows(
                    DBMSException.class,
                    () -> engine.execute("CREATE TABLE " + child + " (id INT PRIMARY KEY, parent_id INT REFERENCES missing_parent(id));")
            );

            assertEquals("Table 'MISSING_PARENT' does not exist.", exception.getMessage());
        } finally {
            deleteRecursively(tableDir(child));
        }
    }

    @Test
    void selectSupportsMultipleJoinsWithQualifiedColumns() throws Exception {
        String authors = uniqueTableName("authors");
        String books = uniqueTableName("books");
        String genres = uniqueTableName("genres");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + authors + " (id INT PRIMARY KEY, name STRING);");
            engine.execute("CREATE TABLE " + genres + " (id INT PRIMARY KEY, label STRING);");
            engine.execute("CREATE TABLE " + books + " (id INT PRIMARY KEY, author_id INT, genre_id INT, title STRING);");
            engine.execute("CREATE INDEX books_author_idx_" + System.nanoTime() + " ON " + books + " (author_id);");
            engine.execute("CREATE INDEX books_genre_idx_" + System.nanoTime() + " ON " + books + " (genre_id);");

            engine.execute("INSERT INTO " + authors + " (1, 'ada');");
            engine.execute("INSERT INTO " + authors + " (2, 'grace');");
            engine.execute("INSERT INTO " + genres + " (10, 'database');");
            engine.execute("INSERT INTO " + genres + " (20, 'compiler');");
            engine.execute("INSERT INTO " + books + " (100, 1, 10, 'indexes');");
            engine.execute("INSERT INTO " + books + " (101, 2, 20, 'parsers');");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT " + authors + ".name, " + genres + ".label FROM " + authors
                            + " JOIN " + books + " ON " + authors + ".id = " + books + ".author_id"
                            + " JOIN " + genres + " ON " + books + ".genre_id = " + genres + ".id"
                            + " WHERE " + genres + ".id = 10;");

            assertEquals(List.of(authors + ".name", genres + ".label"), result.getColumns());
            assertEquals(List.of(List.of("ada", "database")), result.getRows());
        } finally {
            deleteRecursively(tableDir(books));
            deleteRecursively(tableDir(genres));
            deleteRecursively(tableDir(authors));
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
