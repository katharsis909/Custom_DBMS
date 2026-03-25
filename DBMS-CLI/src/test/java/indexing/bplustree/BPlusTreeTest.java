package indexing.bplustree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BPlusTreeTest {
    @Test
    void insertAndPointLookupSupportDuplicateKeys() {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);

        tree.insert(10, "row-1");
        tree.insert(5, "row-2");
        tree.insert(10, "row-3");
        tree.insert(20, "row-4");

        assertEquals(List.of("row-1", "row-3"), tree.search(10));
        assertEquals(List.of("row-2"), tree.search(5));
        assertTrue(tree.search(99).isEmpty());
        assertEquals(3, tree.getDistinctKeyCount());
        assertEquals(4, tree.getValueCount());
    }

    @Test
    void rangeScanReturnsSortedKeyValuePairsAcrossLeafSplits() {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);
        for (int i = 1; i <= 12; i++) {
            tree.insert(i, "v" + i);
        }

        List<Map.Entry<Integer, String>> scan = tree.searchRange(4, 8);

        assertEquals(
                List.of(
                        Map.entry(4, "v4"),
                        Map.entry(5, "v5"),
                        Map.entry(6, "v6"),
                        Map.entry(7, "v7"),
                        Map.entry(8, "v8")
                ),
                scan
        );
        assertIterableEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), tree.keysInOrder());
    }

    @Test
    void deleteSpecificValueLeavesOtherDuplicatesIntact() {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);

        tree.insert(7, "a");
        tree.insert(7, "b");
        tree.insert(7, "c");

        assertTrue(tree.delete(7, "b"));
        assertEquals(List.of("a", "c"), tree.search(7));
        assertEquals(1, tree.getDistinctKeyCount());
        assertEquals(2, tree.getValueCount());
    }

    @Test
    void deleteKeyRebalancesAndKeepsSearchWorking() {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);
        for (int i = 1; i <= 15; i++) {
            tree.insert(i, "v" + i);
        }

        assertTrue(tree.delete(1));
        assertTrue(tree.delete(2));
        assertTrue(tree.delete(3));
        assertTrue(tree.delete(4));
        assertTrue(tree.delete(5));

        assertFalse(tree.containsKey(3));
        assertEquals(List.of("v6"), tree.search(6));
        assertEquals(List.of("v15"), tree.search(15));
        assertIterableEquals(List.of(6, 7, 8, 9, 10, 11, 12, 13, 14, 15), tree.keysInOrder());
    }

    @Test
    void deletingEverythingLeavesTheTreeEmpty() {
        BPlusTree<Integer, String> tree = new BPlusTree<>(3);
        tree.insert(1, "v1");
        tree.insert(2, "v2");
        tree.insert(3, "v3");

        assertTrue(tree.delete(1));
        assertTrue(tree.delete(2));
        assertTrue(tree.delete(3));

        assertTrue(tree.isEmpty());
        assertTrue(tree.keysInOrder().isEmpty());
        assertTrue(tree.search(1).isEmpty());
    }
}
