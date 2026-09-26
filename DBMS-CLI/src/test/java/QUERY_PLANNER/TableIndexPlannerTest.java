package QUERY_PLANNER;

import SEMANTIC.AST_NODES.ColumnMention;
import SEMANTIC.AST_NODES.DataType;
import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import SEMANTIC.AST_NODES.OrderByItem;
import STRUCTURE.Column;
import STRUCTURE.IndexDefinition;
import STRUCTURE.table.utility.TableIndexManager;
import STRUCTURE.table.utility.TableSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TableIndexPlannerTest {

    private TableSchema schema;
    private TableIndexManager indexManager;
    private TableIndexPlanner planner;

    @BeforeEach
    void setUp() throws Exception {
        schema = new TableSchema("test_table", List.of(
                new Column("id", "INT", true),
                new Column("age", "INT"),
                new Column("name", "STRING"),
                new Column("score", "INT")
        ));
        indexManager = mock(TableIndexManager.class);
        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id"))));
        planner = new TableIndexPlanner(schema, indexManager);
    }

    @Test
    void findsExactForwardMatchForAscIndex() {
        IndexDefinition idx = new IndexDefinition("age_name_idx", List.of("age", "name"), List.of(true, true));
        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id")), idx));

        List<OrderByItem> orderBy = List.of(orderBy("age", true), orderBy("name", true));
        OrderIndexMatch match = planner.findBestOrderIndexMatch(orderBy);

        assertEquals(2, match.prefixLength);
        assertTrue(match.scanAscending);
        assertTrue(match.completeCovered);
        assertEquals("age_name_idx", match.index.indexName);
    }

    @Test
    void findsExactReverseMatchForAscIndex() {
        IndexDefinition idx = new IndexDefinition("age_name_idx", List.of("age", "name"), List.of(true, true));
        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id")), idx));

        List<OrderByItem> orderBy = List.of(orderBy("age", false), orderBy("name", false));
        OrderIndexMatch match = planner.findBestOrderIndexMatch(orderBy);

        assertEquals(2, match.prefixLength);
        assertFalse(match.scanAscending);
        assertTrue(match.completeCovered);
        assertEquals("age_name_idx", match.index.indexName);
    }

    @Test
    void findsExactForwardMatchForMixedDirectionIndex() {
        IndexDefinition idx = new IndexDefinition("mixed_idx", List.of("age", "name"), List.of(true, false));
        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id")), idx));

        List<OrderByItem> orderBy = List.of(orderBy("age", true), orderBy("name", false));
        OrderIndexMatch match = planner.findBestOrderIndexMatch(orderBy);

        assertEquals(2, match.prefixLength);
        assertTrue(match.scanAscending);
        assertTrue(match.completeCovered);
        assertEquals("mixed_idx", match.index.indexName);
    }

    @Test
    void findsExactReverseMatchForMixedDirectionIndex() {
        IndexDefinition idx = new IndexDefinition("mixed_idx", List.of("age", "name"), List.of(true, false));
        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id")), idx));

        // ORDER BY age DESC, name ASC is the exact reverse of (age ASC, name DESC)
        List<OrderByItem> orderBy = List.of(orderBy("age", false), orderBy("name", true));
        OrderIndexMatch match = planner.findBestOrderIndexMatch(orderBy);

        assertEquals(2, match.prefixLength);
        assertFalse(match.scanAscending);
        assertTrue(match.completeCovered);
        assertEquals("mixed_idx", match.index.indexName);
    }

    @Test
    void partialMatchWhenSecondColumnDirectionMismatches() {
        IndexDefinition idx = new IndexDefinition("mixed_idx", List.of("age", "name"), List.of(true, false));
        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id")), idx));

        // ORDER BY age ASC, name ASC -> only 'age' matches forward (prefixLength = 1)
        List<OrderByItem> orderBy = List.of(orderBy("age", true), orderBy("name", true));
        OrderIndexMatch match = planner.findBestOrderIndexMatch(orderBy);

        assertEquals(1, match.prefixLength);
        assertTrue(match.scanAscending);
        assertFalse(match.completeCovered);
    }

    @Test
    void selectsBestIndexAmongMultipleCandidateIndexes() {
        IndexDefinition singleIdx = new IndexDefinition("age_idx", List.of("age"), List.of(true));
        IndexDefinition multiIdx = new IndexDefinition("mixed_idx", List.of("age", "name"), List.of(true, false));

        when(indexManager.candidateIndexes(schema.getPrimaryKeyColumns()))
                .thenReturn(List.of(IndexDefinition.primary(List.of("id")), singleIdx, multiIdx));

        List<OrderByItem> orderBy = List.of(orderBy("age", true), orderBy("name", false));
        OrderIndexMatch match = planner.findBestOrderIndexMatch(orderBy);

        assertEquals(2, match.prefixLength);
        assertTrue(match.scanAscending);
        assertTrue(match.completeCovered);
        assertEquals("mixed_idx", match.index.indexName);
    }

    private static OrderByItem orderBy(String columnName, boolean ascending) {
        ColumnMention column = new ColumnMention();
        Identifier identifier = new Identifier();
        identifier.setName(columnName);
        column.setColumnName(identifier);

        OrderByItem item = new OrderByItem();
        item.setColumn(column);
        item.setAscending(ascending);
        return item;
    }
}
