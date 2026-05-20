package indexing.bplustree;

import disk_persistence.Page;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class BPlusTreePageCodec<K extends Comparable<K>, V> {
    private static final int NODE_MAGIC = 0x42504E31;
    private static final int METADATA_MAGIC = 0x42505431;
    private static final int VERSION = 1;
    private static final int LEAF_PAGE = 1;
    private static final int INTERNAL_PAGE = 2;

    private final BPlusTreeValueSerializer<K> keySerializer;
    private final BPlusTreeValueSerializer<V> valueSerializer;

    BPlusTreePageCodec(
            BPlusTreeValueSerializer<K> keySerializer,
            BPlusTreeValueSerializer<V> valueSerializer
    ) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    byte[] writeMetadataPage(BPlusTree.TreeSnapshot<K, V> snapshot) {
        return writeMetadataHeader(new MetadataHeader(
                snapshot.branchingFactor,
                snapshot.rootPageId,
                snapshot.firstLeafPageId,
                snapshot.distinctKeyCount,
                snapshot.valueCount,
                snapshot.nodes.size()
        ));
    }

    byte[] writeMetadataHeader(MetadataHeader header) {
        ByteBuffer buffer = emptyPage();
        buffer.putInt(METADATA_MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt(header.branchingFactor);
        buffer.putInt(header.rootPageId);
        buffer.putInt(header.firstLeafPageId);
        buffer.putInt(header.distinctKeyCount);
        buffer.putInt(header.valueCount);
        buffer.putInt(header.nodeCount);
        return buffer.array();
    }

    BPlusTree.TreeSnapshot<K, V> readMetadataPage(byte[] pageBytes, List<BPlusTree.NodeSnapshot<K, V>> nodes) {
        MetadataHeader header = readMetadataHeader(pageBytes);
        require(header.nodeCount == nodes.size(), "B+ tree node count does not match metadata.");

        return new BPlusTree.TreeSnapshot<>(
                header.branchingFactor,
                header.rootPageId,
                header.firstLeafPageId,
                header.distinctKeyCount,
                header.valueCount,
                nodes
        );
    }

    MetadataHeader readMetadataHeader(byte[] pageBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(pageBytes);
        require(buffer.getInt() == METADATA_MAGIC, "Not a B+ tree metadata page.");
        require(buffer.getInt() == VERSION, "Unsupported B+ tree metadata version.");

        int branchingFactor = buffer.getInt();
        int rootPageId = buffer.getInt();
        int firstLeafPageId = buffer.getInt();
        int distinctKeyCount = buffer.getInt();
        int valueCount = buffer.getInt();
        int nodeCount = buffer.getInt();

        return new MetadataHeader(
                branchingFactor,
                rootPageId,
                firstLeafPageId,
                distinctKeyCount,
                valueCount,
                nodeCount
        );
    }

    byte[] writeNodePage(BPlusTree.NodeSnapshot<K, V> node) {
        ByteBuffer buffer = emptyPage();
        buffer.putInt(NODE_MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt(node.pageId);
        buffer.putInt(node.leaf ? LEAF_PAGE : INTERNAL_PAGE);
        buffer.putInt(node.parentPageId);
        buffer.putInt(node.keys.size());

        if (node.leaf) {
            buffer.putInt(node.nextLeafPageId);
            for (int i = 0; i < node.keys.size(); i++) {
                putBytes(buffer, keySerializer.serialize(node.keys.get(i)));
                List<V> bucket = node.values.get(i);
                buffer.putInt(bucket.size());
                for (V value : bucket) {
                    putBytes(buffer, valueSerializer.serialize(value));
                }
            }
        } else {
            buffer.putInt(node.childPageIds.size());
            for (Integer childPageId : node.childPageIds) {
                buffer.putInt(childPageId);
            }
            for (K key : node.keys) {
                putBytes(buffer, keySerializer.serialize(key));
            }
        }

        return buffer.array();
    }

    BPlusTree.NodeSnapshot<K, V> readNodePage(byte[] pageBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(pageBytes);
        require(buffer.getInt() == NODE_MAGIC, "Not a B+ tree node page.");
        require(buffer.getInt() == VERSION, "Unsupported B+ tree node page version.");

        int pageId = buffer.getInt();
        int pageType = buffer.getInt();
        int parentPageId = buffer.getInt();
        int keyCount = buffer.getInt();
        require(keyCount >= 0, "Negative key count in B+ tree node page.");

        if (pageType == LEAF_PAGE) {
            int nextLeafPageId = buffer.getInt();
            List<K> keys = new ArrayList<>();
            List<List<V>> values = new ArrayList<>();
            for (int i = 0; i < keyCount; i++) {
                keys.add(keySerializer.deserialize(getBytes(buffer)));
                int bucketSize = buffer.getInt();
                require(bucketSize >= 0, "Negative value bucket size in B+ tree leaf page.");
                List<V> bucket = new ArrayList<>();
                for (int j = 0; j < bucketSize; j++) {
                    bucket.add(valueSerializer.deserialize(getBytes(buffer)));
                }
                values.add(bucket);
            }
            return BPlusTree.NodeSnapshot.leaf(pageId, parentPageId, nextLeafPageId, keys, values);
        }

        require(pageType == INTERNAL_PAGE, "Unknown B+ tree node page type.");
        int childCount = buffer.getInt();
        require(childCount == keyCount + 1, "Internal B+ tree page has invalid child count.");
        List<Integer> childPageIds = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            childPageIds.add(buffer.getInt());
        }

        List<K> keys = new ArrayList<>();
        for (int i = 0; i < keyCount; i++) {
            keys.add(keySerializer.deserialize(getBytes(buffer)));
        }
        return BPlusTree.NodeSnapshot.internal(pageId, parentPageId, keys, childPageIds);
    }

    private ByteBuffer emptyPage() {
        return ByteBuffer.allocate(Page.PAGE_SIZE);
    }

    private void putBytes(ByteBuffer buffer, byte[] bytes) {
        require(buffer.remaining() >= Integer.BYTES + bytes.length, "B+ tree node page is full.");
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    private byte[] getBytes(ByteBuffer buffer) {
        int length = buffer.getInt();
        require(length >= 0 && length <= buffer.remaining(), "Invalid byte field in B+ tree page.");
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static final class MetadataHeader {
        final int branchingFactor;
        final int rootPageId;
        final int firstLeafPageId;
        final int distinctKeyCount;
        final int valueCount;
        final int nodeCount;

        MetadataHeader(
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
