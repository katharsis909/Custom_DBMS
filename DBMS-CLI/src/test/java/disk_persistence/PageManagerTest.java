package disk_persistence;

import STRUCTURE.DBMSException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageManagerTest {

    @Test
    void insertRowFlushesPageAndReturnsPhysicalPointer() throws Exception {
        String tableName = uniqueTableName("pm_insert");
        try {
            PageManager manager = new PageManager(tableName);
            RowPointer pointer = manager.insertRow(new byte[]{1, 2, 3});

            assertEquals(0, pointer.getPageId());
            assertEquals(Page.PAGE_SIZE - 3, pointer.getRowOffset());
            assertTrue(Files.exists(tableDir(tableName).resolve("page_0.dat")));
            assertArrayEquals(new byte[]{1, 2, 3}, manager.loadPage(0).getRow(0));
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void insertRowCreatesNewPageWhenCurrentPageIsFull() throws Exception {
        String tableName = uniqueTableName("pm_rotate");
        try {
            PageManager manager = new PageManager(tableName);
            RowPointer first = manager.insertRow(new byte[4080]);
            RowPointer second = manager.insertRow(new byte[]{9});

            assertEquals(0, first.getPageId());
            assertEquals(1, second.getPageId());
            assertEquals(1, manager.getCurrentPageId());
            assertNotNull(manager.getCurrentPage());
            assertTrue(Files.exists(tableDir(tableName).resolve("page_0.dat")));
            assertTrue(Files.exists(tableDir(tableName).resolve("page_1.dat")));
        } finally {
            deleteRecursively(tableDir(tableName));
        }
    }

    @Test
    void constructorLoadsHighestExistingPageAndIgnoresUnrelatedFiles() throws Exception {
        String tableName = uniqueTableName("pm_existing");
        Path tableDir = tableDir(tableName);
        try {
            Files.createDirectories(tableDir);
            writePage(tableDir.resolve("page_0.dat"), new Page(0));
            writePage(tableDir.resolve("page_5.dat"), new Page(5));
            Files.writeString(tableDir.resolve("page_future.tmp"), "ignore me");

            PageManager manager = new PageManager(tableName);
            assertEquals(5, manager.getCurrentPageId());
            assertEquals(5, manager.getCurrentPage().getPageId());
            assertEquals(5, manager.loadPage(5).getPageId());
        } finally {
            deleteRecursively(tableDir);
        }
    }

    @Test
    void constructorRejectsCorruptLastPageFile() throws Exception {
        String tableName = uniqueTableName("pm_corrupt");
        Path tableDir = tableDir(tableName);
        try {
            Files.createDirectories(tableDir);
            Files.write(tableDir.resolve("page_0.dat"), new byte[12]);

            DBMSException exception = assertThrows(DBMSException.class, () -> new PageManager(tableName));

            assertEquals("Corrupt page file: data/" + tableName + "/page_0.dat", exception.getMessage());
        } finally {
            deleteRecursively(tableDir);
        }
    }

    private static void writePage(Path path, Page page) throws IOException {
        Files.write(path, page.getData());
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
