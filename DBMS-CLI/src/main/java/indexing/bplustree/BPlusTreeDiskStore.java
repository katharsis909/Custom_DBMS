package indexing.bplustree;

import STRUCTURE.DBMSException;
import disk_persistence.Page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class BPlusTreeDiskStore<K extends Comparable<K>, V> {
    private static final int METADATA_PAGE_ID = 0;

    private final Path indexDirectory;
    private final BPlusTreePageCodec<K, V> codec;

    public BPlusTreeDiskStore(
            Path indexDirectory,
            BPlusTreeValueSerializer<K> keySerializer,
            BPlusTreeValueSerializer<V> valueSerializer
    ) {
        this.indexDirectory = indexDirectory;
        this.codec = new BPlusTreePageCodec<>(keySerializer, valueSerializer);
    }

    public void save(BPlusTree<K, V> tree) throws DBMSException {
        BPlusTree.TreeSnapshot<K, V> snapshot = tree.snapshotForDisk();
        try {
            Files.createDirectories(indexDirectory);
            deleteExistingIndexPages();
            Files.write(pagePath(METADATA_PAGE_ID), codec.writeMetadataPage(snapshot));
            for (BPlusTree.NodeSnapshot<K, V> node : snapshot.nodes) {
                writeNode(node);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new DBMSException("Could not save B+ tree index at " + indexDirectory, e);
        }
    }

    public BPlusTree<K, V> load() throws DBMSException {
        try {
            List<BPlusTree.NodeSnapshot<K, V>> nodes = new ArrayList<>();
            for (int pageId : nodePageIds()) {
                nodes.add(readNode(pageId));
            }
            byte[] metadataBytes = readPage(METADATA_PAGE_ID);
            BPlusTree.TreeSnapshot<K, V> snapshot = codec.readMetadataPage(metadataBytes, nodes);
            return BPlusTree.fromDiskSnapshot(snapshot);
        } catch (IOException | IllegalArgumentException e) {
            throw new DBMSException("Could not load B+ tree index at " + indexDirectory, e);
        }
    }

    public List<V> search(K key) throws DBMSException {
        try {
            BPlusTreePageCodec.MetadataHeader metadata = codec.readMetadataHeader(readPage(METADATA_PAGE_ID));
            int pageId = metadata.rootPageId;

            while (true) {
                BPlusTree.NodeSnapshot<K, V> page = readNode(pageId);
                if (page.leaf) {
                    return valuesForKey(page, key);
                }
                pageId = childPageForKey(page, key);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new DBMSException("Could not search B+ tree index at " + indexDirectory, e);
        }
    }

    public boolean delete(K key, V value) throws DBMSException {
        try {
            DeleteState state = readDeleteState();
            BPlusTree.NodeSnapshot<K, V> leaf = findLeafPage(key, state.rootPageId);
            int keyIndex = keyIndexInLeaf(leaf, key);
            if (keyIndex < 0) {
                return false;
            }

            List<List<V>> values = copyValueBuckets(leaf.values);
            List<V> bucket = new ArrayList<>(values.get(keyIndex));
            if (!bucket.remove(value)) {
                return false;
            }

            state.valueCount--;
            if (!bucket.isEmpty()) {
                values.set(keyIndex, bucket);
                writeNode(BPlusTree.NodeSnapshot.leaf(
                        leaf.pageId,
                        leaf.parentPageId,
                        leaf.nextLeafPageId,
                        new ArrayList<>(leaf.keys),
                        values
                ));
                if (keyIndex == 0) {
                    refreshAncestors(leaf.parentPageId);
                }
                writeMetadata(state);
                return true;
            }

            List<K> keys = new ArrayList<>(leaf.keys);
            keys.remove(keyIndex);
            values.remove(keyIndex);
            state.distinctKeyCount--;
            rebalanceLeafAfterDelete(
                    BPlusTree.NodeSnapshot.leaf(leaf.pageId, leaf.parentPageId, leaf.nextLeafPageId, keys, values),
                    state
            );
            writeMetadata(state);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            throw new DBMSException("Could not delete from B+ tree index at " + indexDirectory, e);
        }
    }

    public boolean delete(K key) throws DBMSException {
        try {
            DeleteState state = readDeleteState();
            BPlusTree.NodeSnapshot<K, V> leaf = findLeafPage(key, state.rootPageId);
            int keyIndex = keyIndexInLeaf(leaf, key);
            if (keyIndex < 0) {
                return false;
            }

            List<K> keys = new ArrayList<>(leaf.keys);
            List<List<V>> values = copyValueBuckets(leaf.values);
            state.valueCount -= values.get(keyIndex).size();
            state.distinctKeyCount--;
            keys.remove(keyIndex);
            values.remove(keyIndex);
            rebalanceLeafAfterDelete(
                    BPlusTree.NodeSnapshot.leaf(leaf.pageId, leaf.parentPageId, leaf.nextLeafPageId, keys, values),
                    state
            );
            writeMetadata(state);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            throw new DBMSException("Could not delete from B+ tree index at " + indexDirectory, e);
        }
    }

    void writeNode(BPlusTree.NodeSnapshot<K, V> node) throws IOException {
        Files.createDirectories(indexDirectory);
        Files.write(pagePath(node.pageId), codec.writeNodePage(node));
    }

    BPlusTree.NodeSnapshot<K, V> readNode(int pageId) throws IOException {
        return codec.readNodePage(readPage(pageId));
    }

    private List<Integer> nodePageIds() throws IOException {
        if (!Files.exists(indexDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(indexDirectory)) {
            return paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("bptree_page_") && name.endsWith(".dat"))
                    .map(name -> Integer.parseInt(name.substring(12, name.length() - 4)))
                    .filter(pageId -> pageId != METADATA_PAGE_ID)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private void deleteExistingIndexPages() throws IOException {
        try (Stream<Path> paths = Files.list(indexDirectory)) {
            List<Path> indexPages = paths
                    .filter(path -> path.getFileName().toString().startsWith("bptree_page_"))
                    .filter(path -> path.getFileName().toString().endsWith(".dat"))
                    .toList();
            for (Path indexPage : indexPages) {
                Files.delete(indexPage);
            }
        }
    }

    private byte[] readPage(int pageId) throws IOException {
        byte[] bytes = Files.readAllBytes(pagePath(pageId));
        if (bytes.length != Page.PAGE_SIZE) {
            throw new IllegalArgumentException("B+ tree page must be exactly " + Page.PAGE_SIZE + " bytes.");
        }
        return bytes;
    }

    private int childPageForKey(BPlusTree.NodeSnapshot<K, V> internalPage, K key) {
        int childIndex = 0;
        while (childIndex < internalPage.keys.size()
                && key.compareTo(internalPage.keys.get(childIndex)) >= 0) {
            childIndex++;
        }
        return internalPage.childPageIds.get(childIndex);
    }

    private List<V> valuesForKey(BPlusTree.NodeSnapshot<K, V> leafPage, K key) {
        int low = 0;
        int high = leafPage.keys.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comparison = key.compareTo(leafPage.keys.get(mid));
            if (comparison == 0) {
                return new ArrayList<>(leafPage.values.get(mid));
            }
            if (comparison < 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return List.of();
    }

    private Path pagePath(int pageId) {
        return indexDirectory.resolve("bptree_page_" + pageId + ".dat");
    }

    private DeleteState readDeleteState() throws IOException {
        BPlusTreePageCodec.MetadataHeader metadata = codec.readMetadataHeader(readPage(METADATA_PAGE_ID));
        return new DeleteState(
                metadata.branchingFactor,
                metadata.rootPageId,
                metadata.firstLeafPageId,
                metadata.distinctKeyCount,
                metadata.valueCount,
                metadata.nodeCount
        );
    }

    private void writeMetadata(DeleteState state) throws IOException {
        Files.write(pagePath(METADATA_PAGE_ID), codec.writeMetadataHeader(
                state.branchingFactor,
                state.rootPageId,
                state.firstLeafPageId,
                state.distinctKeyCount,
                state.valueCount,
                state.nodeCount
        ));
    }

    private BPlusTree.NodeSnapshot<K, V> findLeafPage(K key, int rootPageId) throws IOException {
        BPlusTree.NodeSnapshot<K, V> page = readNode(rootPageId);
        while (!page.leaf) {
            page = readNode(childPageForKey(page, key));
        }
        return page;
    }

    private void rebalanceLeafAfterDelete(BPlusTree.NodeSnapshot<K, V> leaf, DeleteState state) throws IOException {
        if (leaf.pageId == state.rootPageId) {
            writeNode(leaf);
            state.firstLeafPageId = leaf.pageId;
            return;
        }

        if (leaf.keys.size() >= minLeafKeys(state)) {
            writeNode(leaf);
            refreshAncestors(leaf.parentPageId);
            return;
        }

        BPlusTree.NodeSnapshot<K, V> parent = readNode(leaf.parentPageId);
        int leafIndex = childIndex(parent, leaf.pageId);
        BPlusTree.NodeSnapshot<K, V> left = leafIndex > 0
                ? readNode(parent.childPageIds.get(leafIndex - 1))
                : null;
        BPlusTree.NodeSnapshot<K, V> right = leafIndex < parent.childPageIds.size() - 1
                ? readNode(parent.childPageIds.get(leafIndex + 1))
                : null;

        if (left != null && left.keys.size() > minLeafKeys(state)) {
            List<K> leftKeys = new ArrayList<>(left.keys);
            List<List<V>> leftValues = copyValueBuckets(left.values);
            List<K> leafKeys = new ArrayList<>(leaf.keys);
            List<List<V>> leafValues = copyValueBuckets(leaf.values);

            leafKeys.add(0, leftKeys.remove(leftKeys.size() - 1));
            leafValues.add(0, leftValues.remove(leftValues.size() - 1));

            writeNode(BPlusTree.NodeSnapshot.leaf(left.pageId, left.parentPageId, left.nextLeafPageId, leftKeys, leftValues));
            writeNode(BPlusTree.NodeSnapshot.leaf(leaf.pageId, leaf.parentPageId, leaf.nextLeafPageId, leafKeys, leafValues));
            writeRebuiltInternal(parent);
            refreshAncestors(parent.parentPageId);
            return;
        }

        if (right != null && right.keys.size() > minLeafKeys(state)) {
            List<K> rightKeys = new ArrayList<>(right.keys);
            List<List<V>> rightValues = copyValueBuckets(right.values);
            List<K> leafKeys = new ArrayList<>(leaf.keys);
            List<List<V>> leafValues = copyValueBuckets(leaf.values);

            leafKeys.add(rightKeys.remove(0));
            leafValues.add(rightValues.remove(0));

            writeNode(BPlusTree.NodeSnapshot.leaf(leaf.pageId, leaf.parentPageId, leaf.nextLeafPageId, leafKeys, leafValues));
            writeNode(BPlusTree.NodeSnapshot.leaf(right.pageId, right.parentPageId, right.nextLeafPageId, rightKeys, rightValues));
            writeRebuiltInternal(parent);
            refreshAncestors(parent.parentPageId);
            return;
        }

        if (left != null) {
            List<K> mergedKeys = new ArrayList<>(left.keys);
            List<List<V>> mergedValues = copyValueBuckets(left.values);
            mergedKeys.addAll(leaf.keys);
            mergedValues.addAll(copyValueBuckets(leaf.values));

            writeNode(BPlusTree.NodeSnapshot.leaf(
                    left.pageId,
                    left.parentPageId,
                    leaf.nextLeafPageId,
                    mergedKeys,
                    mergedValues
            ));
            deleteNodePage(leaf.pageId, state);
            removeChildFromParent(parent, leafIndex, state);
            return;
        }

        if (right != null) {
            List<K> mergedKeys = new ArrayList<>(leaf.keys);
            List<List<V>> mergedValues = copyValueBuckets(leaf.values);
            mergedKeys.addAll(right.keys);
            mergedValues.addAll(copyValueBuckets(right.values));

            writeNode(BPlusTree.NodeSnapshot.leaf(
                    leaf.pageId,
                    leaf.parentPageId,
                    right.nextLeafPageId,
                    mergedKeys,
                    mergedValues
            ));
            deleteNodePage(right.pageId, state);
            removeChildFromParent(parent, leafIndex + 1, state);
        }
    }

    private void removeChildFromParent(
            BPlusTree.NodeSnapshot<K, V> parent,
            int childIndexToRemove,
            DeleteState state
    ) throws IOException {
        List<Integer> childPageIds = new ArrayList<>(parent.childPageIds);
        childPageIds.remove(childIndexToRemove);
        BPlusTree.NodeSnapshot<K, V> updatedParent = BPlusTree.NodeSnapshot.internal(
                parent.pageId,
                parent.parentPageId,
                rebuildKeysForChildren(childPageIds),
                childPageIds
        );
        rebalanceInternalAfterChildRemoval(updatedParent, state);
    }

    private void rebalanceInternalAfterChildRemoval(
            BPlusTree.NodeSnapshot<K, V> internal,
            DeleteState state
    ) throws IOException {
        if (internal.pageId == state.rootPageId) {
            if (internal.childPageIds.size() == 1) {
                int childPageId = internal.childPageIds.get(0);
                BPlusTree.NodeSnapshot<K, V> child = readNode(childPageId);
                writeNode(withParent(child, -1));
                deleteNodePage(internal.pageId, state);
                state.rootPageId = childPageId;
                state.firstLeafPageId = leftmostLeafPageId(childPageId);
                return;
            }
            writeNode(BPlusTree.NodeSnapshot.internal(
                    internal.pageId,
                    internal.parentPageId,
                    rebuildKeysForChildren(internal.childPageIds),
                    new ArrayList<>(internal.childPageIds)
            ));
            return;
        }

        if (internal.childPageIds.size() >= minInternalChildren(state)) {
            writeNode(BPlusTree.NodeSnapshot.internal(
                    internal.pageId,
                    internal.parentPageId,
                    rebuildKeysForChildren(internal.childPageIds),
                    new ArrayList<>(internal.childPageIds)
            ));
            refreshAncestors(internal.parentPageId);
            return;
        }

        BPlusTree.NodeSnapshot<K, V> parent = readNode(internal.parentPageId);
        int internalIndex = childIndex(parent, internal.pageId);
        BPlusTree.NodeSnapshot<K, V> left = internalIndex > 0
                ? readNode(parent.childPageIds.get(internalIndex - 1))
                : null;
        BPlusTree.NodeSnapshot<K, V> right = internalIndex < parent.childPageIds.size() - 1
                ? readNode(parent.childPageIds.get(internalIndex + 1))
                : null;

        if (left != null && left.childPageIds.size() > minInternalChildren(state)) {
            List<Integer> leftChildren = new ArrayList<>(left.childPageIds);
            List<Integer> internalChildren = new ArrayList<>(internal.childPageIds);
            int borrowedChildPageId = leftChildren.remove(leftChildren.size() - 1);
            internalChildren.add(0, borrowedChildPageId);
            writeNode(withParent(readNode(borrowedChildPageId), internal.pageId));
            writeInternalWithChildren(left, leftChildren);
            writeInternalWithChildren(internal, internalChildren);
            writeRebuiltInternal(parent);
            refreshAncestors(parent.parentPageId);
            return;
        }

        if (right != null && right.childPageIds.size() > minInternalChildren(state)) {
            List<Integer> rightChildren = new ArrayList<>(right.childPageIds);
            List<Integer> internalChildren = new ArrayList<>(internal.childPageIds);
            int borrowedChildPageId = rightChildren.remove(0);
            internalChildren.add(borrowedChildPageId);
            writeNode(withParent(readNode(borrowedChildPageId), internal.pageId));
            writeInternalWithChildren(internal, internalChildren);
            writeInternalWithChildren(right, rightChildren);
            writeRebuiltInternal(parent);
            refreshAncestors(parent.parentPageId);
            return;
        }

        if (left != null) {
            List<Integer> mergedChildren = new ArrayList<>(left.childPageIds);
            mergedChildren.addAll(internal.childPageIds);
            for (Integer childPageId : internal.childPageIds) {
                writeNode(withParent(readNode(childPageId), left.pageId));
            }
            writeInternalWithChildren(left, mergedChildren);
            deleteNodePage(internal.pageId, state);
            removeChildFromParent(parent, internalIndex, state);
            return;
        }

        if (right != null) {
            List<Integer> mergedChildren = new ArrayList<>(internal.childPageIds);
            mergedChildren.addAll(right.childPageIds);
            for (Integer childPageId : right.childPageIds) {
                writeNode(withParent(readNode(childPageId), internal.pageId));
            }
            writeInternalWithChildren(internal, mergedChildren);
            deleteNodePage(right.pageId, state);
            removeChildFromParent(parent, internalIndex + 1, state);
        }
    }

    private void refreshAncestors(int parentPageId) throws IOException {
        int currentPageId = parentPageId;
        while (currentPageId != -1) {
            BPlusTree.NodeSnapshot<K, V> internal = readNode(currentPageId);
            writeRebuiltInternal(internal);
            currentPageId = internal.parentPageId;
        }
    }

    private void writeRebuiltInternal(BPlusTree.NodeSnapshot<K, V> internal) throws IOException {
        writeInternalWithChildren(internal, internal.childPageIds);
    }

    private void writeInternalWithChildren(
            BPlusTree.NodeSnapshot<K, V> internal,
            List<Integer> childPageIds
    ) throws IOException {
        writeNode(BPlusTree.NodeSnapshot.internal(
                internal.pageId,
                internal.parentPageId,
                rebuildKeysForChildren(childPageIds),
                new ArrayList<>(childPageIds)
        ));
    }

    private List<K> rebuildKeysForChildren(List<Integer> childPageIds) throws IOException {
        List<K> keys = new ArrayList<>();
        for (int i = 1; i < childPageIds.size(); i++) {
            keys.add(firstKey(childPageIds.get(i)));
        }
        return keys;
    }

    private K firstKey(int pageId) throws IOException {
        BPlusTree.NodeSnapshot<K, V> page = readNode(pageId);
        if (page.leaf) {
            if (page.keys.isEmpty()) {
                throw new IllegalArgumentException("Cannot route through an empty B+ tree leaf page.");
            }
            return page.keys.get(0);
        }
        return firstKey(page.childPageIds.get(0));
    }

    private BPlusTree.NodeSnapshot<K, V> withParent(BPlusTree.NodeSnapshot<K, V> node, int parentPageId) {
        if (node.leaf) {
            return BPlusTree.NodeSnapshot.leaf(
                    node.pageId,
                    parentPageId,
                    node.nextLeafPageId,
                    new ArrayList<>(node.keys),
                    copyValueBuckets(node.values)
            );
        }
        return BPlusTree.NodeSnapshot.internal(
                node.pageId,
                parentPageId,
                new ArrayList<>(node.keys),
                new ArrayList<>(node.childPageIds)
        );
    }

    private int leftmostLeafPageId(int pageId) throws IOException {
        BPlusTree.NodeSnapshot<K, V> page = readNode(pageId);
        while (!page.leaf) {
            page = readNode(page.childPageIds.get(0));
        }
        return page.pageId;
    }

    private void deleteNodePage(int pageId, DeleteState state) throws IOException {
        Files.deleteIfExists(pagePath(pageId));
        state.nodeCount--;
    }

    private int keyIndexInLeaf(BPlusTree.NodeSnapshot<K, V> leafPage, K key) {
        int low = 0;
        int high = leafPage.keys.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comparison = key.compareTo(leafPage.keys.get(mid));
            if (comparison == 0) {
                return mid;
            }
            if (comparison < 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return -1;
    }

    private int childIndex(BPlusTree.NodeSnapshot<K, V> parent, int childPageId) {
        int index = parent.childPageIds.indexOf(childPageId);
        if (index < 0) {
            throw new IllegalArgumentException("B+ tree parent does not reference child page " + childPageId + ".");
        }
        return index;
    }

    private int minLeafKeys(DeleteState state) {
        return Math.max(1, (int) Math.ceil((state.branchingFactor - 1) / 2.0));
    }

    private int minInternalChildren(DeleteState state) {
        return (int) Math.ceil(state.branchingFactor / 2.0);
    }

    private List<List<V>> copyValueBuckets(List<List<V>> values) {
        List<List<V>> copiedBuckets = new ArrayList<>();
        for (List<V> bucket : values) {
            copiedBuckets.add(new ArrayList<>(bucket));
        }
        return copiedBuckets;
    }

    private final class DeleteState {
        final int branchingFactor;
        int rootPageId;
        int firstLeafPageId;
        int distinctKeyCount;
        int valueCount;
        int nodeCount;

        DeleteState(
                int branchingFactor,
                int rootPageId,
                int firstLeafPageId,
                int distinctKeyCount,
                int valueCount,
                int nodeCount
        ) {
            this.branchingFactor = branchingFactor;
            this.rootPageId = rootPageId;
            this.firstLeafPageId = firstLeafPageId;
            this.distinctKeyCount = distinctKeyCount;
            this.valueCount = valueCount;
            this.nodeCount = nodeCount;
        }
    }
}
