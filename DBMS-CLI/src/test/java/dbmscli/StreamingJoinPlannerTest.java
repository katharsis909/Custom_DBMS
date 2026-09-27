package dbmscli;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.OrderByItem;
import SEMANTIC.AST_NODES.select.utility.JoinOrderPlanner;
import SEMANTIC.AST_NODES.select.utility.RecordSortEngine;
import SEMANTIC.AST_NODES.select.utility.SubGroupSortIterator;
import STRUCTURE.DBMSException;
import STRUCTURE.MyInt;
import STRUCTURE.Record;
import dbmscli.result.QueryResultBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingJoinPlannerTest {

    @Test
    void testQualifiedOrderByTablesLeftToRightContiguous() {
        JoinOrderPlanner planner = new JoinOrderPlanner();

        Set<String> contiguous = planner.qualifiedOrderByTables(List.of(
                orderByItem("t1", "a"),
                orderByItem("t1", "b"),
                orderByItem("t2", "c")
        ));
        assertTrue(contiguous.contains("t1"));
        assertTrue(contiguous.contains("t2"));

        Set<String> interrupted = planner.qualifiedOrderByTables(List.of(
                orderByItem("t1", "a"),
                orderByItem("t2", "c"),
                orderByItem("t1", "b")
        ));
        assertFalse(interrupted.contains("t1")); // Disqualified because t1 reappeared after pause
        assertTrue(interrupted.contains("t2"));
    }

    @Test
    void testSubGroupSortIteratorPreservesPrefixOrder() throws Exception {
        List<Record> records = new ArrayList<>();
        records.add(createRecord("t1.id", 10, "t2.val", 3));
        records.add(createRecord("t1.id", 10, "t2.val", 1));
        records.add(createRecord("t1.id", 20, "t2.val", 5));
        records.add(createRecord("t1.id", 20, "t2.val", 2));

        List<OrderByItem> orderItems = List.of(
                orderByItem("t1", "id"),
                orderByItem("t2", "val")
        );

        SubGroupSortIterator subGroupIter = new SubGroupSortIterator(records.iterator(), orderItems, 1, new RecordSortEngine());
        List<Record> result = new ArrayList<>();
        while (subGroupIter.hasNext()) {
            result.add(subGroupIter.next());
        }

        assertEquals(4, result.size());
        assertEquals(10, Integer.parseInt(result.get(0).getValue("t1.id").toString()));
        assertEquals(1, Integer.parseInt(result.get(0).getValue("t2.val").toString()));
        assertEquals(10, Integer.parseInt(result.get(1).getValue("t1.id").toString()));
        assertEquals(3, Integer.parseInt(result.get(1).getValue("t2.val").toString()));

        assertEquals(20, Integer.parseInt(result.get(2).getValue("t1.id").toString()));
        assertEquals(2, Integer.parseInt(result.get(2).getValue("t2.val").toString()));
        assertEquals(20, Integer.parseInt(result.get(3).getValue("t1.id").toString()));
        assertEquals(5, Integer.parseInt(result.get(3).getValue("t2.val").toString()));
    }

    @Test
    void testStreamingJoinExecutionWithIndex() throws Exception {
        String users = uniqueTableName("users_stream");
        String posts = uniqueTableName("posts_stream");
        try {
            DbmsCliEngine engine = new DbmsCliEngine();
            engine.execute("CREATE TABLE " + users + " (id INT PRIMARY KEY, name STRING);");
            engine.execute("CREATE TABLE " + posts + " (id INT PRIMARY KEY, user_id INT, title STRING);");
            engine.execute("CREATE INDEX idx_user_id ON " + posts + " (user_id);");

            engine.execute("INSERT INTO " + users + " (1, 'Alice');");
            engine.execute("INSERT INTO " + users + " (2, 'Bob');");

            engine.execute("INSERT INTO " + posts + " (10, 1, 'Post 1');");
            engine.execute("INSERT INTO " + posts + " (11, 1, 'Post 2');");
            engine.execute("INSERT INTO " + posts + " (12, 2, 'Post 3');");

            QueryResultBlock result = onlyBlock(engine,
                    "SELECT " + users + ".name, " + posts + ".title FROM " + users +
                            " JOIN " + posts + " ON " + users + ".id = " + posts + ".user_id " +
                            " ORDER BY " + users + ".id ASC, " + posts + ".id ASC;");

            assertEquals(3, result.getRows().size());
            assertEquals(List.of("Alice", "Post 1"), result.getRows().get(0));
            assertEquals(List.of("Alice", "Post 2"), result.getRows().get(1));
            assertEquals(List.of("Bob", "Post 3"), result.getRows().get(2));
        } finally {
            deleteRecursively(tableDir(posts));
            deleteRecursively(tableDir(users));
        }
    }

    private OrderByItem orderByItem(String table, String col) {
        Identifier id = new Identifier();
        id.setName(table + "." + col);
        ColumnMention cm = new ColumnMention();
        cm.setColumnName(id);
        OrderByItem item = new OrderByItem();
        item.setColumn(cm);
        item.setAscending(true);
        return item;
    }

    private Record createRecord(String col1, int val1, String col2, int val2) throws DBMSException {
        Record r = new Record();
        r.setValue(col1, MyInt.convtoDB_DT(String.valueOf(val1)));
        r.setValue(col2, MyInt.convtoDB_DT(String.valueOf(val2)));
        return r;
    }

    private String uniqueTableName(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private QueryResultBlock onlyBlock(DbmsCliEngine engine, String sql) throws Exception {
        List<QueryResultBlock> blocks = engine.executeStructured(sql).getBlocks();
        assertEquals(1, blocks.size());
        return blocks.get(0);
    }

    private Path tableDir(String tableName) {
        return Path.of("data", tableName);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
        }
    }
}
