package indexing.bplustree;

import STRUCTURE.DBMSException;
import disk_persistence.Page;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public List<V> searchRange(K fromInclusive, K toInclusive) throws DBMSException {
        if (fromInclusive.compareTo(toInclusive) > 0) {
            return List.of();
        }
        List<V> values = new ArrayList<>();
        for (Map.Entry<K, V> entry : load().searchRange(fromInclusive, toInclusive)) {
            values.add(entry.getValue());
        }
        return values;
    }

    public List<V> valuesInOrder(boolean ascending) throws DBMSException {
        List<V> values = new ArrayList<>();
        List<Map.Entry<K, V>> entries = load().entriesInOrder();
        if (!ascending) {
            entries = new ArrayList<>(entries);
            java.util.Collections.reverse(entries);
        }
        for (Map.Entry<K, V> entry : entries) {
            values.add(entry.getValue());
        }
        return values;
    }

    public void insert(K key, V value) throws DBMSException {
        try {
            BPlusTreePageCodec.MetadataHeader metadata = codec.readMetadataHeader(readPage(METADATA_PAGE_ID));
            PageAllocator pageAllocator = new PageAllocator(nextAvailablePageId());
            List<BPlusTree.NodeSnapshot<K, V>> parentPath = new ArrayList<>();
            BPlusTree.NodeSnapshot<K, V> leaf = findLeafPage(key, metadata.rootPageId, parentPath);

            InsertedLeaf<K, V> insertedLeaf = insertIntoLeaf(leaf, key, value);
            int rootPageId = metadata.rootPageId;

            if (insertedLeaf.leaf.keys.size() <= maxKeysPerNode(metadata.branchingFactor)) {
                writeNode(insertedLeaf.leaf);
            } else {
                SplitLeaf<K, V> splitLeaf = splitLeaf(insertedLeaf.leaf, pageAllocator);
                writeNode(splitLeaf.left);
                writeNode(splitLeaf.right);
                rootPageId = propagateSplit(
                        splitLeaf.left.pageId,
                        splitLeaf.right,
                        parentPath,
                        metadata.branchingFactor,
                        pageAllocator,
                        metadata.rootPageId
                );
            }

            refreshInternalSeparatorKeys();
            writeMetadata(new BPlusTreePageCodec.MetadataHeader(
                    metadata.branchingFactor,
                    rootPageId,
                    metadata.firstLeafPageId,
                    metadata.distinctKeyCount + (insertedLeaf.newKey ? 1 : 0),
                    metadata.valueCount + 1,
                    nodePageIds().size()
            ));
        } catch (IOException | IllegalArgumentException e) {
            throw new DBMSException("Could not insert into B+ tree index at " + indexDirectory, e);
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

    private BPlusTree.NodeSnapshot<K, V> findLeafPage(
            K key,
            int rootPageId,
            List<BPlusTree.NodeSnapshot<K, V>> parentPath
    ) throws IOException {
        BPlusTree.NodeSnapshot<K, V> page = readNode(rootPageId);
        while (!page.leaf) {
            parentPath.add(page);
            page = readNode(childPageForKey(page, key));
        }
        return page;
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

    private InsertedLeaf<K, V> insertIntoLeaf(BPlusTree.NodeSnapshot<K, V> leaf, K key, V value) {
        List<K> keys = new ArrayList<>(leaf.keys);
        List<List<V>> values = copyValueBuckets(leaf.values);
        int existingIndex = keyIndex(keys, key);

        if (existingIndex >= 0) {
            values.get(existingIndex).add(value);
            return new InsertedLeaf<>(
                    BPlusTree.NodeSnapshot.leaf(
                            leaf.pageId,
                            leaf.parentPageId,
                            leaf.nextLeafPageId,
                            keys,
                            values
                    ),
                    false
            );
        }

        int insertionPoint = insertionPoint(keys, key);
        keys.add(insertionPoint, key);
        values.add(insertionPoint, new ArrayList<>(List.of(value)));
        return new InsertedLeaf<>(
                BPlusTree.NodeSnapshot.leaf(
                        leaf.pageId,
                        leaf.parentPageId,
                        leaf.nextLeafPageId,
                        keys,
                        values
                ),
                true
        );
    }

    private SplitLeaf<K, V> splitLeaf(BPlusTree.NodeSnapshot<K, V> leaf, PageAllocator pageAllocator) {
        int splitIndex = leaf.keys.size() / 2;
        int rightPageId = pageAllocator.next();

        BPlusTree.NodeSnapshot<K, V> left = BPlusTree.NodeSnapshot.leaf(
                leaf.pageId,
                leaf.parentPageId,
                rightPageId,
                new ArrayList<>(leaf.keys.subList(0, splitIndex)),
                copyValueBuckets(leaf.values.subList(0, splitIndex))
        );
        BPlusTree.NodeSnapshot<K, V> right = BPlusTree.NodeSnapshot.leaf(
                rightPageId,
                leaf.parentPageId,
                leaf.nextLeafPageId,
                new ArrayList<>(leaf.keys.subList(splitIndex, leaf.keys.size())),
                copyValueBuckets(leaf.values.subList(splitIndex, leaf.values.size()))
        );
        return new SplitLeaf<>(left, right);
    }

    private int propagateSplit(
            int leftPageId,
            BPlusTree.NodeSnapshot<K, V> rightNode,
            List<BPlusTree.NodeSnapshot<K, V>> parentPath,
            int branchingFactor,
            PageAllocator pageAllocator,
            int currentRootPageId
    ) throws IOException {
        if (parentPath.isEmpty()) {
            int newRootPageId = pageAllocator.next();
            BPlusTree.NodeSnapshot<K, V> leftWithParent = withParentPageId(readNode(leftPageId), newRootPageId);
            BPlusTree.NodeSnapshot<K, V> rightWithParent = withParentPageId(rightNode, newRootPageId);
            writeNode(leftWithParent);
            writeNode(rightWithParent);

            BPlusTree.NodeSnapshot<K, V> newRoot = internalFromChildren(
                    newRootPageId,
                    -1,
                    List.of(leftWithParent.pageId, rightWithParent.pageId)
            );
            writeNode(newRoot);
            return newRootPageId;
        }

        BPlusTree.NodeSnapshot<K, V> parent = parentPath.remove(parentPath.size() - 1);
        List<Integer> childPageIds = new ArrayList<>(parent.childPageIds);
        int leftChildIndex = childPageIds.indexOf(leftPageId);
        if (leftChildIndex < 0) {
            throw new IllegalArgumentException("Split child page is missing from its parent.");
        }

        BPlusTree.NodeSnapshot<K, V> rightWithParent = withParentPageId(rightNode, parent.pageId);
        writeNode(rightWithParent);
        childPageIds.add(leftChildIndex + 1, rightWithParent.pageId);

        BPlusTree.NodeSnapshot<K, V> updatedParent = internalFromChildren(
                parent.pageId,
                parent.parentPageId,
                childPageIds
        );

        if (updatedParent.childPageIds.size() <= branchingFactor) {
            writeNode(updatedParent);
            return currentRootPageId;
        }

        SplitInternal<K, V> splitInternal = splitInternal(updatedParent, pageAllocator);
        writeNode(splitInternal.left);
        writeNode(splitInternal.right);
        updateChildrenParentPageIds(splitInternal.right.childPageIds, splitInternal.right.pageId);
        return propagateSplit(
                splitInternal.left.pageId,
                splitInternal.right,
                parentPath,
                branchingFactor,
                pageAllocator,
                currentRootPageId
        );
    }

    private SplitInternal<K, V> splitInternal(
            BPlusTree.NodeSnapshot<K, V> internal,
            PageAllocator pageAllocator
    ) throws IOException {
        int splitIndex = internal.childPageIds.size() / 2;
        int rightPageId = pageAllocator.next();
        List<Integer> leftChildren = new ArrayList<>(internal.childPageIds.subList(0, splitIndex));
        List<Integer> rightChildren = new ArrayList<>(internal.childPageIds.subList(splitIndex, internal.childPageIds.size()));

        BPlusTree.NodeSnapshot<K, V> left = internalFromChildren(
                internal.pageId,
                internal.parentPageId,
                leftChildren
        );
        BPlusTree.NodeSnapshot<K, V> right = internalFromChildren(
                rightPageId,
                internal.parentPageId,
                rightChildren
        );
        return new SplitInternal<>(left, right);
    }

    private BPlusTree.NodeSnapshot<K, V> internalFromChildren(
            int pageId,
            int parentPageId,
            List<Integer> childPageIds
    ) throws IOException {
        List<K> keys = new ArrayList<>();
        for (int i = 1; i < childPageIds.size(); i++) {
            keys.add(firstKey(childPageIds.get(i)));
        }
        return BPlusTree.NodeSnapshot.internal(pageId, parentPageId, keys, new ArrayList<>(childPageIds));
    }

    private K firstKey(int pageId) throws IOException {
        BPlusTree.NodeSnapshot<K, V> page = readNode(pageId);
        if (page.leaf) {
            return page.keys.get(0);
        }
        return firstKey(page.childPageIds.get(0));
    }

    private void refreshInternalSeparatorKeys() throws IOException {
        List<Integer> pageIds = nodePageIds();
        for (int i = pageIds.size() - 1; i >= 0; i--) {
            BPlusTree.NodeSnapshot<K, V> page = readNode(pageIds.get(i));
            if (!page.leaf) {
                writeNode(internalFromChildren(page.pageId, page.parentPageId, page.childPageIds));
            }
        }
    }

    private void updateChildrenParentPageIds(List<Integer> childPageIds, int parentPageId) throws IOException {
        for (Integer childPageId : childPageIds) {
            writeNode(withParentPageId(readNode(childPageId), parentPageId));
        }
    }

    private BPlusTree.NodeSnapshot<K, V> withParentPageId(
            BPlusTree.NodeSnapshot<K, V> page,
            int parentPageId
    ) {
        if (page.leaf) {
            return BPlusTree.NodeSnapshot.leaf(
                    page.pageId,
                    parentPageId,
                    page.nextLeafPageId,
                    new ArrayList<>(page.keys),
                    copyValueBuckets(page.values)
            );
        }
        return BPlusTree.NodeSnapshot.internal(
                page.pageId,
                parentPageId,
                new ArrayList<>(page.keys),
                new ArrayList<>(page.childPageIds)
        );
    }

    private int keyIndex(List<K> keys, K key) {
        int low = 0;
        int high = keys.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comparison = key.compareTo(keys.get(mid));
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

    private int insertionPoint(List<K> keys, K key) {
        int index = 0;
        while (index < keys.size() && key.compareTo(keys.get(index)) > 0) {
            index++;
        }
        return index;
    }

    private int maxKeysPerNode(int branchingFactor) {
        return branchingFactor - 1;
    }

    private int nextAvailablePageId() throws IOException {
        int nextPageId = METADATA_PAGE_ID + 1;
        for (Integer pageId : nodePageIds()) {
            nextPageId = Math.max(nextPageId, pageId + 1);
        }
        return nextPageId;
    }

    private void writeMetadata(BPlusTreePageCodec.MetadataHeader metadata) throws IOException {
        Files.write(pagePath(METADATA_PAGE_ID), codec.writeMetadataHeader(metadata));
    }

    private List<List<V>> copyValueBuckets(List<List<V>> values) {
        List<List<V>> copiedBuckets = new ArrayList<>();
        for (List<V> bucket : values) {
            copiedBuckets.add(new ArrayList<>(bucket));
        }
        return copiedBuckets;
    }

    private Path pagePath(int pageId) {
        return indexDirectory.resolve("bptree_page_" + pageId + ".dat");
    }

    private static final class PageAllocator {
        private int nextPageId;

        private PageAllocator(int nextPageId) {
            this.nextPageId = nextPageId;
        }

        private int next() {
            int pageId = nextPageId;
            nextPageId++;
            return pageId;
        }
    }

    private static final class InsertedLeaf<K extends Comparable<K>, V> {
        private final BPlusTree.NodeSnapshot<K, V> leaf;
        private final boolean newKey;

        private InsertedLeaf(BPlusTree.NodeSnapshot<K, V> leaf, boolean newKey) {
            this.leaf = leaf;
            this.newKey = newKey;
        }
    }

    private static final class SplitLeaf<K extends Comparable<K>, V> {
        private final BPlusTree.NodeSnapshot<K, V> left;
        private final BPlusTree.NodeSnapshot<K, V> right;

        private SplitLeaf(BPlusTree.NodeSnapshot<K, V> left, BPlusTree.NodeSnapshot<K, V> right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class SplitInternal<K extends Comparable<K>, V> {
        private final BPlusTree.NodeSnapshot<K, V> left;
        private final BPlusTree.NodeSnapshot<K, V> right;

        private SplitInternal(BPlusTree.NodeSnapshot<K, V> left, BPlusTree.NodeSnapshot<K, V> right) {
            this.left = left;
            this.right = right;
        }
    }
}
