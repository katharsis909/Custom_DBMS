package indexing.bplustree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BPlusTreeDiskStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsOneLeafNodePage() throws Exception {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);
        tree.insert(10, "row-a");
        tree.insert(20, "row-b");

        BPlusTree.NodeSnapshot<Integer, String> leaf = tree.snapshotForDisk().nodes.get(0);
        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        store.writeNode(leaf);
        BPlusTree.NodeSnapshot<Integer, String> loaded = store.readNode(leaf.pageId);

        assertTrue(loaded.leaf);
        assertEquals(leaf.pageId, loaded.pageId);
        assertEquals(List.of(10, 20), loaded.keys);
        assertEquals(List.of(List.of("row-a"), List.of("row-b")), loaded.values);
    }

    @Test
    void savesAndLoadsWholeTree() throws Exception {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);
        for (int i = 1; i <= 12; i++) {
            tree.insert(i, "v" + i);
        }
        tree.insert(7, "v7-duplicate");

        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        store.save(tree);
        BPlusTree<Integer, String> loaded = store.load();

        assertTrue(Files.exists(tempDir.resolve("bptree_page_0.dat")));
        assertEquals(tree.keysInOrder(), loaded.keysInOrder());
        assertEquals(List.of("v7", "v7-duplicate"), loaded.search(7));
        assertEquals(
                List.of(
                        Map.entry(4, "v4"),
                        Map.entry(5, "v5"),
                        Map.entry(6, "v6"),
                        Map.entry(7, "v7"),
                        Map.entry(7, "v7-duplicate"),
                        Map.entry(8, "v8")
                ),
                loaded.searchRange(4, 8)
        );
        assertEquals(tree.getDistinctKeyCount(), loaded.getDistinctKeyCount());
        assertEquals(tree.getValueCount(), loaded.getValueCount());
    }

    @Test
    void searchesPersistedPagesWithoutLoadingWholeTree() throws Exception {
        List<BPlusTree.NodeSnapshot<Integer, String>> pages = List.of(
                BPlusTree.NodeSnapshot.internal(1, -1, List.of(10, 20), List.of(2, 3, 4)),
                BPlusTree.NodeSnapshot.leaf(
                        2,
                        1,
                        3,
                        List.of(1, 5, 9),
                        List.of(List.of("v1"), List.of("v5"), List.of("v9"))
                ),
                BPlusTree.NodeSnapshot.leaf(
                        3,
                        1,
                        4,
                        List.of(10, 12, 18),
                        List.of(List.of("v10"), List.of("v12-a", "v12-b"), List.of("v18"))
                ),
                BPlusTree.NodeSnapshot.leaf(
                        4,
                        1,
                        -1,
                        List.of(20, 25, 30),
                        List.of(List.of("v20"), List.of("v25"), List.of("v30"))
                )
        );
        writePersistedTree(pages, 1, 2, 9, 10);

        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        assertEquals(List.of("v1"), store.search(1));
        assertEquals(List.of("v10"), store.search(10));
        assertEquals(List.of("v12-a", "v12-b"), store.search(12));
        assertEquals(List.of("v20"), store.search(20));
        assertEquals(List.of("v30"), store.search(30));
        assertTrue(store.search(19).isEmpty());
        assertTrue(store.search(99).isEmpty());
    }

    @Test
    void searchesPersistedLeafRoot() throws Exception {
        List<BPlusTree.NodeSnapshot<Integer, String>> pages = List.of(
                BPlusTree.NodeSnapshot.leaf(
                        1,
                        -1,
                        -1,
                        List.of(3, 6),
                        List.of(List.of("v3"), List.of("v6"))
                )
        );
        writePersistedTree(pages, 1, 1, 2, 2);

        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        assertEquals(List.of("v3"), store.search(3));
        assertTrue(store.search(4).isEmpty());
    }

    @Test
    void rangeScansPersistedPages() throws Exception {
        BPlusTree<Integer, String> tree = new BPlusTree<>(4);
        for (int i = 1; i <= 10; i++) {
            tree.insert(i, "v" + i);
        }
        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );
        store.save(tree);

        assertEquals(List.of("v3", "v4", "v5", "v6"), store.searchRange(3, 6));
    }


    @Test
    void insertsIntoPersistedLeafWithoutSplit() throws Exception {
        writePersistedTree(
                List.of(BPlusTree.NodeSnapshot.leaf(
                        1,
                        -1,
                        -1,
                        List.of(3, 9),
                        List.of(List.of("v3"), List.of("v9"))
                )),
                1,
                1,
                2,
                2
        );
        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        store.insert(6, "v6");

        assertEquals(List.of("v3"), store.search(3));
        assertEquals(List.of("v6"), store.search(6));
        assertEquals(List.of("v9"), store.search(9));
        assertEquals(List.of(3, 6, 9), store.load().keysInOrder());
    }

    @Test
    void insertsDuplicateIntoPersistedLeafBucket() throws Exception {
        writePersistedTree(
                List.of(BPlusTree.NodeSnapshot.leaf(
                        1,
                        -1,
                        -1,
                        List.of(7),
                        List.of(List.of("v7-a"))
                )),
                1,
                1,
                1,
                1
        );
        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        store.insert(7, "v7-b");

        BPlusTree<Integer, String> loaded = store.load();
        assertEquals(List.of("v7-a", "v7-b"), store.search(7));
        assertEquals(1, loaded.getDistinctKeyCount());
        assertEquals(2, loaded.getValueCount());
    }

    @Test
    void insertsIntoPersistedLeafAndCreatesNewRootOnSplit() throws Exception {
        writePersistedTree(
                List.of(BPlusTree.NodeSnapshot.leaf(
                        1,
                        -1,
                        -1,
                        List.of(3, 6, 9),
                        List.of(List.of("v3"), List.of("v6"), List.of("v9"))
                )),
                1,
                1,
                3,
                3
        );
        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        store.insert(12, "v12");

        assertEquals(List.of("v3"), store.search(3));
        assertEquals(List.of("v6"), store.search(6));
        assertEquals(List.of("v9"), store.search(9));
        assertEquals(List.of("v12"), store.search(12));
        assertEquals(List.of(3, 6, 9, 12), store.load().keysInOrder());
    }

    @Test
    void insertsManyKeysIntoPersistedTreeAcrossCascadingSplits() throws Exception {
        writePersistedTree(
                List.of(BPlusTree.NodeSnapshot.leaf(
                        1,
                        -1,
                        -1,
                        List.<Integer>of(),
                        List.<List<String>>of()
                )),
                1,
                1,
                0,
                0
        );
        BPlusTreeDiskStore<Integer, String> store = new BPlusTreeDiskStore<>(
                tempDir,
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );

        for (int i = 1; i <= 40; i++) {
            store.insert(i, "v" + i);
        }
        store.insert(17, "v17-duplicate");

        BPlusTree<Integer, String> loaded = store.load();
        assertEquals(List.of("v1"), store.search(1));
        assertEquals(List.of("v17", "v17-duplicate"), store.search(17));
        assertEquals(List.of("v40"), store.search(40));
        assertTrue(store.search(41).isEmpty());
        assertEquals(40, loaded.getDistinctKeyCount());
        assertEquals(41, loaded.getValueCount());
        assertEquals(
                java.util.stream.IntStream.rangeClosed(1, 40).boxed().toList(),
                loaded.keysInOrder()
        );
    }

    private void writePersistedTree(
            List<BPlusTree.NodeSnapshot<Integer, String>> pages,
            int rootPageId,
            int firstLeafPageId,
            int distinctKeyCount,
            int valueCount
    ) throws Exception {
        BPlusTreePageCodec<Integer, String> codec = new BPlusTreePageCodec<>(
                BPlusTreeSerializers.integers(),
                BPlusTreeSerializers.strings()
        );
        BPlusTree.TreeSnapshot<Integer, String> snapshot = new BPlusTree.TreeSnapshot<>(
                4,
                rootPageId,
                firstLeafPageId,
                distinctKeyCount,
                valueCount,
                pages
        );

        Files.write(tempDir.resolve("bptree_page_0.dat"), codec.writeMetadataPage(snapshot));
        for (BPlusTree.NodeSnapshot<Integer, String> page : pages) {
            Files.write(tempDir.resolve("bptree_page_" + page.pageId + ".dat"), codec.writeNodePage(page));
        }
    }
}
