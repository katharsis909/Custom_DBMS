package indexing.bplustree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * In-memory B+ tree intended to back future disk-based indexes.
 * Duplicate keys are supported so a single index key can reference many rows.
 */
public class BPlusTree<K extends Comparable<K>, V> {
    private final int branchingFactor;
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

        this.branchingFactor = branchingFactor;
        this.maxKeysPerNode = branchingFactor - 1;
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
            splitLeaf(leaf);
        } else {
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
        rightLeaf.keys.addAll(leaf.keys.subList(splitIndex, leaf.keys.size()));
        rightLeaf.values.addAll(leaf.values.subList(splitIndex, leaf.values.size()));

        leaf.keys.subList(splitIndex, leaf.keys.size()).clear();
        leaf.values.subList(splitIndex, leaf.values.size()).clear();

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
            int borrowedKeyIndex = leftSibling.keys.size() - 1;
            leaf.keys.add(0, leftSibling.keys.remove(borrowedKeyIndex));
            leaf.values.add(0, leftSibling.values.remove(borrowedKeyIndex));
            parent.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(leaf);
            return;
        }

        if (rightSibling != null && rightSibling.keys.size() > minLeafKeys) {
            leaf.keys.add(rightSibling.keys.remove(0));
            leaf.values.add(rightSibling.values.remove(0));
            parent.rebuildKeys();
            updateParentKeysAfterFirstKeyChange(rightSibling);
            return;
        }

        if (leftSibling != null) {
            leftSibling.keys.addAll(leaf.keys);
            leftSibling.values.addAll(leaf.values);
            leftSibling.next = leaf.next;
            parent.children.remove(index);
            parent.rebuildKeys();
            rebalanceAfterInternalDeletion(parent);
        } else if (rightSibling != null) {
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
            for (Node child : node.children) {
                child.parent = leftSibling;
            }
            leftSibling.children.addAll(node.children);
            leftSibling.rebuildKeys();
            parent.children.remove(index);
            parent.rebuildKeys();
            rebalanceAfterInternalDeletion(parent);
        } else if (rightSibling != null) {
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

    private abstract class Node {
        InternalNode parent;

        abstract boolean isLeaf();

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
            while (childIndex < keys.size() && key.compareTo(keys.get(childIndex)) >= 0) {
                childIndex++;
            }
            return children.get(childIndex);
        }

        void rebuildKeys() {
            keys.clear();
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
            while (index < keys.size() && key.compareTo(keys.get(index)) > 0) {
                index++;
            }
            return index;
        }
    }
}
