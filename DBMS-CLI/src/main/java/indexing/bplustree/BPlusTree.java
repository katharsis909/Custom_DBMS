package indexing.bplustree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory B+ tree intended to back future disk-based indexes.
 * Duplicate keys are supported so a single index key can reference many rows.
 * (any use case of duplicate keys allowed?????????????????????????????????????????)
 *
 * The structure follows the usual B+ tree layout:
 * - internal nodes only route lookups
 * - leaf nodes hold the actual values
 * - leaves are linked left-to-right for fast ordered scans
 */
public class BPlusTree<K extends Comparable<K>, V> {
    private final int branchingFactor;
    //what is this?????????????????????????????????????????
    private final int maxKeysPerNode;
    private final int minLeafKeys;
    private final int minInternalChildren;

    private Node root;
    private LeafNode firstLeaf;
    private int distinctKeyCount;
    private int valueCount;

    public BPlusTree(int branchingFactor) {
        if (branchingFactor < 3) {
            throw new IllegalArgumentException("Branching factor must be at least 3.");
        }

        // With branching factor m, an internal node can have at most m children
        // and therefore at most m - 1 separator keys.
        this.branchingFactor = branchingFactor;
        this.maxKeysPerNode = branchingFactor - 1;
        // Minimum occupancy thresholds are used during delete rebalancing.
        this.minLeafKeys = Math.max(1, (int) Math.ceil(maxKeysPerNode / 2.0));
        this.minInternalChildren = (int) Math.ceil(branchingFactor / 2.0);
        LeafNode rootLeaf = new LeafNode();
        this.root = rootLeaf;
        this.firstLeaf = rootLeaf;
    }

    public void insert(K key, V value) {
        LeafNode leaf = findLeaf(key);
        int keyIndex = leaf.findKeyIndex(key);

        if (keyIndex >= 0) {
            // Duplicate keys share the same key slot and append into its bucket.
            leaf.values.get(keyIndex).add(value);
            valueCount++;
            return;
        }

        int insertionPoint = leaf.insertionPoint(key);
        leaf.keys.add(insertionPoint, key);
        List<V> bucket = new ArrayList<>();
        bucket.add(value);
        leaf.values.add(insertionPoint, bucket);

        distinctKeyCount++;
        valueCount++;

        if (leaf.keys.size() > maxKeysPerNode) {
            // Overflow is handled bottom-up: split the leaf, then propagate the
            // new right sibling into the parent.
            splitLeaf(leaf);
            //how is the above working????????????????????????????????????????????)
        } else {
            // Even without a split, inserting at position 0 may change the
            // separator key seen by ancestors.
            updateParentKeysAfterFirstKeyChange(leaf);
        }
    }

    public List<V> search(K key) {
        LeafNode leaf = findLeaf(key);
        int keyIndex = leaf.findKeyIndex(key);
        if (keyIndex < 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(leaf.values.get(keyIndex));
    }

    public boolean containsKey(K key) {
        LeafNode leaf = findLeaf(key);
        return leaf.findKeyIndex(key) >= 0;
    }

    public List<Map.Entry<K, V>> searchRange(K fromInclusive, K toInclusive) {
        if (fromInclusive.compareTo(toInclusive) > 0) {
            return Collections.emptyList();
        }

        // Range scans start at the first relevant leaf, then walk the linked
        // leaf chain instead of repeatedly re-traversing from the root.
        List<Map.Entry<K, V>> results = new ArrayList<>();
        LeafNode leaf = findLeaf(fromInclusive);

        while (leaf != null) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                K key = leaf.keys.get(i);
                if (key.compareTo(toInclusive) > 0) {
                    return results;
                }
                if (key.compareTo(fromInclusive) < 0) {
                    continue;
                }
                for (V value : leaf.values.get(i)) {
                    results.add(Map.entry(key, value));
                }
            }
            leaf = leaf.next;
        }

        return results;
    }

    public List<Map.Entry<K, V>> entriesInOrder() {
        List<Map.Entry<K, V>> results = new ArrayList<>();
        LeafNode leaf = firstLeaf;
        while (leaf != null) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                K key = leaf.keys.get(i);
                for (V value : leaf.values.get(i)) {
                    results.add(Map.entry(key, value));
                }
            }
            leaf = leaf.next;
        }
        return results;
    }

    public boolean delete(K key) {
        LeafNode leaf = findLeaf(key);
        int keyIndex = leaf.findKeyIndex(key);
        if (keyIndex < 0) {
            return false;
        }

        valueCount -= leaf.values.get(keyIndex).size();
        distinctKeyCount--;
        leaf.keys.remove(keyIndex);
        leaf.values.remove(keyIndex);

        rebalanceAfterLeafDeletion(leaf);
        return true;
    }

    public boolean delete(K key, V value) {
        LeafNode leaf = findLeaf(key);
        int keyIndex = leaf.findKeyIndex(key);
        if (keyIndex < 0) {
            return false;
        }

        List<V> bucket = leaf.values.get(keyIndex);
        boolean removed = bucket.remove(value);
        if (!removed) {
            return false;
        }

        valueCount--;
        if (bucket.isEmpty()) {
            leaf.keys.remove(keyIndex);
            leaf.values.remove(keyIndex);
            distinctKeyCount--;
            rebalanceAfterLeafDeletion(leaf);
        } else if (keyIndex == 0) {
            updateParentKeysAfterFirstKeyChange(leaf);
        }

        return true;
    }

    public int getDistinctKeyCount() {
        return distinctKeyCount;
    }

    public int getValueCount() {
        return valueCount;
    }

    public boolean isEmpty() {
        return valueCount == 0;
    }

    public List<K> keysInOrder() {
        List<K> keys = new ArrayList<>();
        LeafNode leaf = firstLeaf;
        while (leaf != null) {
            keys.addAll(leaf.keys);
            leaf = leaf.next;
        }
        return keys;
    }

    private LeafNode findLeaf(K key) {
        Node current = root;
        while (!current.isLeaf()) {
            InternalNode internal = (InternalNode) current;
            current = internal.childForKey(key);
        }
        return (LeafNode) current;
    }

    private void splitLeaf(LeafNode leaf) {
        int splitIndex = leaf.keys.size() / 2;

        LeafNode rightLeaf = new LeafNode();
        // The right sibling takes the upper half of keys and value buckets.
        rightLeaf.keys.addAll(leaf.keys.subList(splitIndex, leaf.keys.size()));
        rightLeaf.values.addAll(leaf.values.subList(splitIndex, leaf.values.size()));

        leaf.keys.subList(splitIndex, leaf.keys.size()).clear();
        leaf.values.subList(splitIndex, leaf.values.size()).clear();

        // Leaf nodes stay linked in sorted order so range scans remain linear.
        rightLeaf.next = leaf.next;
        leaf.next = rightLeaf;

        rightLeaf.parent = leaf.parent;

        if (firstLeaf == leaf && rightLeaf.keys.get(0).compareTo(firstLeaf.keys.get(0)) < 0) {
            firstLeaf = rightLeaf;
        }

        insertRightSiblingIntoParent(leaf, rightLeaf);
    }

    private void splitInternal(InternalNode node) {
        int splitIndex = node.children.size() / 2;

        InternalNode rightNode = new InternalNode();
        // Internal nodes are split by children; separator keys are recomputed
        // from child first-keys rather than copied directly.
        rightNode.children.addAll(node.children.subList(splitIndex, node.children.size()));
        for (Node child : rightNode.children) {
            child.parent = rightNode;
        }

        node.children.subList(splitIndex, node.children.size()).clear();
        node.rebuildKeys();
        rightNode.rebuildKeys();
        rightNode.parent = node.parent;

        insertRightSiblingIntoParent(node, rightNode);
    }

    private void insertRightSiblingIntoParent(Node leftNode, Node rightNode) {
        if (leftNode.parent == null) {
            // Splitting the root grows the tree height by one level.
            InternalNode newRoot = new InternalNode();
            newRoot.children.add(leftNode);
            newRoot.children.add(rightNode);
            leftNode.parent = newRoot;
            rightNode.parent = newRoot;
            newRoot.rebuildKeys();
            root = newRoot;
            refreshFirstLeaf();
            return;
        }

        InternalNode parent = leftNode.parent;
        int leftIndex = parent.children.indexOf(leftNode);
        // Parent child order remains sorted because the new node was produced as
        // the immediate right sibling of leftNode.
        parent.children.add(leftIndex + 1, rightNode);
        rightNode.parent = parent;
        parent.rebuildKeys();

        if (parent.children.size() > branchingFactor) {
            splitInternal(parent);
        } else {
            updateParentKeysAfterFirstKeyChange(parent);
        }
    }

    private void rebalanceAfterLeafDeletion(LeafNode leaf) {
        if (leaf == root) {
            if (leaf.keys.isEmpty()) {
                firstLeaf = leaf;
            }
            return;
        }

        if (leaf.keys.size() >= minLeafKeys) {
            updateParentKeysAfterFirstKeyChange(leaf);
            return;
        }

        InternalNode parent = leaf.parent;
        int index = parent.children.indexOf(leaf);
        LeafNode leftSibling = index > 0 ? (LeafNode) parent.children.get(index - 1) : null;
        LeafNode rightSibling = index < parent.children.size() - 1 ? (LeafNode) parent.children.get(index + 1) : null;

        if (leftSibling != null && leftSibling.keys.size() > minLeafKeys) {
            // Borrow the greatest key from the left sibling so sorted order is preserved.
            int borrowedKeyIndex = leftSibling.keys.size() - 1;
            leaf.keys.add(0, leftSibling.keys.remove(borrowedKeyIndex));
            leaf.values.add(0, leftSibling.values.remove(borrowedKeyIndex));
            parent.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(leaf);
            return;
        }

        if (rightSibling != null && rightSibling.keys.size() > minLeafKeys) {
            // Borrow the smallest key from the right sibling for the same reason.
            leaf.keys.add(rightSibling.keys.remove(0));
            leaf.values.add(rightSibling.values.remove(0));
            parent.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(rightSibling);
            return;
        }

        if (leftSibling != null) {
            // If neither sibling can lend, merge into the left sibling and
            // delete the underfull leaf from the parent.
            leftSibling.keys.addAll(leaf.keys);
            leftSibling.values.addAll(leaf.values);
            leftSibling.next = leaf.next;
            parent.children.remove(index);
            parent.rebuildKeys();
            rebalanceAfterInternalDeletion(parent);
        } else if (rightSibling != null) {
            // Symmetric case: merge the right sibling into the current leaf.
            leaf.keys.addAll(rightSibling.keys);
            leaf.values.addAll(rightSibling.values);
            leaf.next = rightSibling.next;
            parent.children.remove(index + 1);
            parent.rebuildKeys();
            rebalanceAfterInternalDeletion(parent);
        }

        refreshFirstLeaf();
    }

    private void rebalanceAfterInternalDeletion(InternalNode node) {
        if (node == root) {
            if (node.children.size() == 1) {
                // A root with a single child collapses so the tree shrinks in height.
                root = node.children.get(0);
                root.parent = null;
            }
            refreshFirstLeaf();
            return;
        }

        if (node.children.size() >= minInternalChildren) {
            node.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(node);
            return;
        }

        InternalNode parent = node.parent;
        int index = parent.children.indexOf(node);
        InternalNode leftSibling = index > 0 ? (InternalNode) parent.children.get(index - 1) : null;
        InternalNode rightSibling = index < parent.children.size() - 1 ? (InternalNode) parent.children.get(index + 1) : null;

        if (leftSibling != null && leftSibling.children.size() > minInternalChildren) {
            // Borrow the rightmost child from the left sibling.
            Node borrowedChild = leftSibling.children.remove(leftSibling.children.size() - 1);
            borrowedChild.parent = node;
            node.children.add(0, borrowedChild);
            leftSibling.rebuildKeys();
            node.rebuildKeys();
            parent.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(node);
            return;
        }

        if (rightSibling != null && rightSibling.children.size() > minInternalChildren) {
            // Borrow the leftmost child from the right sibling.
            Node borrowedChild = rightSibling.children.remove(0);
            borrowedChild.parent = node;
            node.children.add(borrowedChild);
            rightSibling.rebuildKeys();
            node.rebuildKeys();
            parent.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(rightSibling);
            return;
        }

        if (leftSibling != null) {
            // Merge the underfull node into its left sibling, updating parent
            // links on every adopted child.
            for (Node child : node.children) {
                child.parent = leftSibling;
            }
            leftSibling.children.addAll(node.children);
            leftSibling.rebuildKeys();
            parent.children.remove(index);
            parent.rebuildKeys();
            rebalanceAfterInternalDeletion(parent);
        } else if (rightSibling != null) {
            // Symmetric merge into the current node when only a right sibling exists.
            for (Node child : rightSibling.children) {
                child.parent = node;
            }
            node.children.addAll(rightSibling.children);
            node.rebuildKeys();
            parent.children.remove(index + 1);
            parent.rebuildKeys();
            rebalanceAfterInternalDeletion(parent);
        }
    }

    private void updateParentKeysAfterFirstKeyChange(Node node) {
        InternalNode parent = node.parent;
        if (parent == null) {
            return;
        }

        // Internal separator keys are derived from child boundaries, so any
        // first-key change in a subtree can ripple upward.
        parent.rebuildKeys();
        updateParentKeysAfterFirstKeyChange(parent);
    }

    private void refreshFirstLeaf() {
        Node current = root;
        while (!current.isLeaf()) {
            current = ((InternalNode) current).children.get(0);
        }
        firstLeaf = (LeafNode) current;
    }

    TreeSnapshot<K, V> snapshotForDisk() {
        Map<Node, Integer> pageIds = new IdentityHashMap<>();
        List<Node> queue = new ArrayList<>();
        List<Node> orderedNodes = new ArrayList<>();

        pageIds.put(root, 1);
        root.pageId = 1;
        queue.add(root);

        int nextPageId = 2;
        for (int i = 0; i < queue.size(); i++) {
            Node node = queue.get(i);
            orderedNodes.add(node);
            if (!node.isLeaf()) {
                InternalNode internal = (InternalNode) node;
                for (Node child : internal.children) {
                    if (!pageIds.containsKey(child)) {
                        pageIds.put(child, nextPageId);
                        child.pageId = nextPageId;
                        nextPageId++;
                        queue.add(child);
                    }
                }
            }
        }

        List<NodeSnapshot<K, V>> nodeSnapshots = new ArrayList<>();
        for (Node node : orderedNodes) {
            int parentPageId = node.parent == null ? -1 : pageIds.get(node.parent);
            if (node.isLeaf()) {
                LeafNode leaf = (LeafNode) node;
                int nextLeafPageId = leaf.next == null ? -1 : pageIds.get(leaf.next);
                nodeSnapshots.add(NodeSnapshot.leaf(
                        pageIds.get(node),
                        parentPageId,
                        nextLeafPageId,
                        new ArrayList<>(leaf.keys),
                        copyValueBuckets(leaf.values)
                ));
            } else {
                InternalNode internal = (InternalNode) node;
                List<Integer> childPageIds = new ArrayList<>();
                for (Node child : internal.children) {
                    childPageIds.add(pageIds.get(child));
                }
                nodeSnapshots.add(NodeSnapshot.internal(
                        pageIds.get(node),
                        parentPageId,
                        new ArrayList<>(internal.keys),
                        childPageIds
                ));
            }
        }

        return new TreeSnapshot<>(
                branchingFactor,
                root.pageId,
                firstLeaf.pageId,
                distinctKeyCount,
                valueCount,
                nodeSnapshots
        );
    }

    static <K extends Comparable<K>, V> BPlusTree<K, V> fromDiskSnapshot(TreeSnapshot<K, V> snapshot) {
        BPlusTree<K, V> tree = new BPlusTree<>(snapshot.branchingFactor);
        Map<Integer, BPlusTree<K, V>.Node> nodesByPageId = new HashMap<>();

        for (NodeSnapshot<K, V> nodeSnapshot : snapshot.nodes) {
            BPlusTree<K, V>.Node node = nodeSnapshot.leaf ? tree.new LeafNode() : tree.new InternalNode();
            node.pageId = nodeSnapshot.pageId;
            nodesByPageId.put(nodeSnapshot.pageId, node);
        }

        for (NodeSnapshot<K, V> nodeSnapshot : snapshot.nodes) {
            BPlusTree<K, V>.Node node = nodesByPageId.get(nodeSnapshot.pageId);
            if (nodeSnapshot.leaf) {
                BPlusTree<K, V>.LeafNode leaf = (BPlusTree<K, V>.LeafNode) node;
                leaf.keys.addAll(nodeSnapshot.keys);
                leaf.values.addAll(copyValueBuckets(nodeSnapshot.values));
                if (nodeSnapshot.nextLeafPageId != -1) {
                    leaf.next = (BPlusTree<K, V>.LeafNode) nodesByPageId.get(nodeSnapshot.nextLeafPageId);
                }
            } else {
                BPlusTree<K, V>.InternalNode internal = (BPlusTree<K, V>.InternalNode) node;
                internal.keys.addAll(nodeSnapshot.keys);
                for (Integer childPageId : nodeSnapshot.childPageIds) {
                    BPlusTree<K, V>.Node child = nodesByPageId.get(childPageId);
                    child.parent = internal;
                    internal.children.add(child);
                }
            }
        }

        tree.root = nodesByPageId.get(snapshot.rootPageId);
        tree.root.parent = null;
        tree.firstLeaf = (BPlusTree<K, V>.LeafNode) nodesByPageId.get(snapshot.firstLeafPageId);
        tree.distinctKeyCount = snapshot.distinctKeyCount;
        tree.valueCount = snapshot.valueCount;
        return tree;
    }

    private static <V> List<List<V>> copyValueBuckets(List<List<V>> values) {
        List<List<V>> copiedBuckets = new ArrayList<>();
        for (List<V> bucket : values) {
            copiedBuckets.add(new ArrayList<>(bucket));
        }
        return copiedBuckets;
    }

    static final class TreeSnapshot<K extends Comparable<K>, V> {
        final int branchingFactor;
        final int rootPageId;
        final int firstLeafPageId;
        final int distinctKeyCount;
        final int valueCount;
        final List<NodeSnapshot<K, V>> nodes;

        TreeSnapshot(
                int branchingFactor,
                int rootPageId,
                int firstLeafPageId,
                int distinctKeyCount,
                int valueCount,
                List<NodeSnapshot<K, V>> nodes
        ) {
            this.branchingFactor = branchingFactor;
            this.rootPageId = rootPageId;
            this.firstLeafPageId = firstLeafPageId;
            this.distinctKeyCount = distinctKeyCount;
            this.valueCount = valueCount;
            this.nodes = nodes;
        }
    }

    static final class NodeSnapshot<K extends Comparable<K>, V> {
        final int pageId;
        final int parentPageId;
        final int nextLeafPageId;
        final boolean leaf;
        final List<K> keys;
        final List<List<V>> values;
        final List<Integer> childPageIds;

        private NodeSnapshot(
                int pageId,
                int parentPageId,
                int nextLeafPageId,
                boolean leaf,
                List<K> keys,
                List<List<V>> values,
                List<Integer> childPageIds
        ) {
            this.pageId = pageId;
            this.parentPageId = parentPageId;
            this.nextLeafPageId = nextLeafPageId;
            this.leaf = leaf;
            this.keys = keys;
            this.values = values;
            this.childPageIds = childPageIds;
        }

        static <K extends Comparable<K>, V> NodeSnapshot<K, V> leaf(
                int pageId,
                int parentPageId,
                int nextLeafPageId,
                List<K> keys,
                List<List<V>> values
        ) {
            return new NodeSnapshot<>(pageId, parentPageId, nextLeafPageId, true, keys, values, List.of());
        }

        static <K extends Comparable<K>, V> NodeSnapshot<K, V> internal(
                int pageId,
                int parentPageId,
                List<K> keys,
                List<Integer> childPageIds
        ) {
            return new NodeSnapshot<>(pageId, parentPageId, -1, false, keys, List.of(), childPageIds);
        }
    }

    private abstract class Node {
        int pageId = -1;
        InternalNode parent;

        abstract boolean isLeaf();

        // firstKey() is the boundary value used by ancestors to rebuild their
        // separator keys.
        abstract K firstKey();
    }

    private final class InternalNode extends Node {
        private final List<K> keys = new ArrayList<>();
        private final List<Node> children = new ArrayList<>();

        @Override
        boolean isLeaf() {
            return false;
        }

        @Override
        K firstKey() {
            return children.get(0).firstKey();
        }

        Node childForKey(K key) {
            int childIndex = 0;
            // keys[i] stores the first key that belongs to children[i + 1].
            while (childIndex < keys.size() && key.compareTo(keys.get(childIndex)) >= 0) {
                childIndex++;
            }
            return children.get(childIndex);
        }

        void rebuildKeys() {
            keys.clear();
            // For n children there are n - 1 separator keys. Each separator is
            // the first key of the child to its right.
            for (int i = 1; i < children.size(); i++) {
                keys.add(children.get(i).firstKey());
            }
        }
    }

    private final class LeafNode extends Node {
        private final List<K> keys = new ArrayList<>();
        private final List<List<V>> values = new ArrayList<>();
        private LeafNode next;

        @Override
        boolean isLeaf() {
            return true;
        }

        @Override
        K firstKey() {
            return keys.get(0);
        }

        int findKeyIndex(K key) {
            int low = 0;
            int high = keys.size() - 1;

            // Binary search keeps point lookups within one leaf efficient.
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

        int insertionPoint(K key) {
            int index = 0;
            // Returns the first position whose key is >= the incoming key.
            while (index < keys.size() && key.compareTo(keys.get(index)) > 0) {
                index++;
            }
            return index;
        }
    }
}
