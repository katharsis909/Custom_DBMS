package STRUCTURE;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablePrimaryKeyTest {

    @Test
    void rejectsDuplicatePrimaryKeyValuesUsingPersistedIndex() throws Exception {
        String tableName = uniqueTableName("pk_duplicate");
        try {
            Table table = new Table(tableName, List.of(
                    new Column("id", "INT", true),
                    new Column("name", "STRING")
            ));

            table.addRecord(List.of(MyInt.convtoDB_DT("1"), MyString.convtoDB_DT("alice")));

            DBMSException exception = assertThrows(
                    DBMSException.class,
                    () -> table.addRecord(List.of(MyInt.convtoDB_DT("1"), MyString.convtoDB_DT("bob")))
            );

            assertEquals("Duplicate primary key value.", exception.getMessage());
            assertTrue(Files.exists(tableDir(tableName).resolve("primary_key_index").resolve("bptree_page_0.dat")));
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void rejectsEmptyPrimaryKeyValues() throws Exception {
        String tableName = uniqueTableName("pk_empty");
        try {
            Table table = new Table(tableName, List.of(new Column("id", "STRING", true)));

            DBMSException exception = assertThrows(
                    DBMSException.class,
                    () -> table.addRecord(List.of(MyString.convtoDB_DT("")))
            );

            assertEquals("Primary key column 'id' must be non-null and non-empty.", exception.getMessage());
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void rejectsNullPrimaryKeyValues() throws Exception {
        String tableName = uniqueTableName("pk_null");
        try {
            Table table = new Table(tableName, List.of(new Column("id", "STRING", true)));

            DBMSException exception = assertThrows(
                    DBMSException.class,
                    () -> table.addRecord(Arrays.asList((DBMSDataType) null))
            );

            assertEquals("Primary key column 'id' must be non-null and non-empty.", exception.getMessage());
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void enforcesCompositePrimaryKeyAcrossConfiguredColumns() throws Exception {
        String tableName = uniqueTableName("pk_composite");
        try {
            Table table = new Table(tableName, List.of(
                    new Column("tenant_id", "INT", true),
                    new Column("user_id", "INT", true),
                    new Column("name", "STRING")
            ));

            table.addRecord(List.of(MyInt.convtoDB_DT("1"), MyInt.convtoDB_DT("1"), MyString.convtoDB_DT("alice")));
            table.addRecord(List.of(MyInt.convtoDB_DT("1"), MyInt.convtoDB_DT("2"), MyString.convtoDB_DT("bob")));

            DBMSException exception = assertThrows(
                    DBMSException.class,
                    () -> table.addRecord(List.of(MyInt.convtoDB_DT("1"), MyInt.convtoDB_DT("1"), MyString.convtoDB_DT("carol")))
            );

            assertEquals("Duplicate primary key value.", exception.getMessage());
        } finally {
            deleteRecursively(tableDir(tableName));
        }
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
