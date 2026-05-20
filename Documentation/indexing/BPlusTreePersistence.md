# B+ Tree Persistence

## Purpose

The `indexing.bplustree` package stores a B+ tree index on disk.

The persisted B+ tree is separate from table row storage:

```text
table page files store rows
B+ tree page files store sorted keys and row references
```

The intended value stored in a leaf is usually `RowPointer`, meaning:

```text
index key -> RowPointer(pageId, rowOffset)
```

That lets a future query path find matching rows without scanning every table page.

## Package Structure

```text
DBMS-CLI/src/main/java/indexing/bplustree/
  BPlusTree.java
  BPlusTreeDiskStore.java
  BPlusTreePageCodec.java
  BPlusTreeSerializers.java
  BPlusTreeValueSerializer.java
```

### `BPlusTree`

This is the in-memory B+ tree implementation.

It still matters because:
- it defines the tree rules used by snapshots
- it supports duplicate keys
- it provides `snapshotForDisk()`
- it provides `fromDiskSnapshot(...)` for whole-tree reload tests and compatibility

However, persisted search and delete do not need to use the in-memory tree as the active structure.

### `BPlusTreeDiskStore`

This is the disk-facing API.

Responsibilities:
- save an in-memory B+ tree snapshot to disk
- load all disk pages back into an in-memory tree when explicitly requested
- search persisted pages directly from disk
- delete persisted entries directly from disk
- read and write individual B+ tree pages

Important distinction:

```text
load()
  reads every B+ tree page and rebuilds an in-memory tree

search(key)
  reads metadata, then only the pages on the root-to-leaf path

delete(key, value) / delete(key)
  reads and rewrites only the affected path/siblings/parents needed for rebalancing
```

### `BPlusTreePageCodec`

This class converts B+ tree metadata and node snapshots to fixed-size page bytes and back.

It owns the binary page format.

### `BPlusTreeSerializers`

This class provides serializers for common key/value payloads:
- `Integer`
- `String`
- `RowPointer`

### `BPlusTreeValueSerializer`

This interface lets the index store different key and value types as bytes.

## Disk Layout

B+ tree pages are stored in an index directory as:

```text
bptree_page_0.dat
bptree_page_1.dat
bptree_page_2.dat
...
```

Every B+ tree page is exactly `Page.PAGE_SIZE`, currently 4 KB.

### Page 0: Metadata Page

Page `0` is reserved for metadata.

It stores:

```text
metadata magic
version
branchingFactor
rootPageId
firstLeafPageId
distinctKeyCount
valueCount
nodeCount
```

Meaning:
- `branchingFactor`: maximum number of children in an internal node
- `rootPageId`: where search starts
- `firstLeafPageId`: first leaf in sorted order
- `distinctKeyCount`: number of unique keys
- `valueCount`: total values, including duplicates
- `nodeCount`: number of node pages excluding metadata page `0`

### Node Pages

Every non-metadata page is either:
- a leaf page
- an internal page

Common node header:

```text
node magic
version
pageId
pageType
parentPageId
keyCount
```

## Leaf Page Format

A leaf page stores actual index entries.

Conceptually:

```text
pageType = LEAF
pageId
parentPageId
nextLeafPageId
keys: [10, 20, 30]
values:
  10 -> [rowPointerA]
  20 -> [rowPointerB, rowPointerC]
  30 -> [rowPointerD]
```

The value list is a bucket because duplicate keys are allowed.

Example:

```text
age = 20 -> row 1
age = 20 -> row 5
age = 20 -> row 9
```

All three row references live under the same key slot.

## Internal Page Format

An internal page stores routing information only.

Conceptually:

```text
pageType = INTERNAL
pageId
parentPageId
keys: [10, 20]
children: [page 2, page 3, page 4]
```

This implementation uses this separator-key rule:

```text
keys[i] = first key contained by children[i + 1]
```

So with:

```text
children[0] starts at 1
children[1] starts at 10
children[2] starts at 20
```

the internal keys are:

```text
[10, 20]
```

## Exact Search

Exact search is implemented directly on persisted pages.

Flow:

```text
read metadata page
pageId = rootPageId

while page is internal:
  read page
  choose child page using separator keys
  pageId = childPageId

read leaf page
binary search leaf keys
return matching value bucket, or empty list
```

Example:

```text
root page:
  keys: [10, 20]
  children: [2, 3, 4]

search(12)
  12 >= 10, so move right once
  12 < 20, stop at child index 1
  read page 3
  find 12 in leaf page 3
```

Search does not load the whole B+ tree.

## Delete

Delete is also implemented directly on persisted pages.

Two public forms exist:

```java
delete(key, value)
delete(key)
```

### `delete(key, value)`

This removes one value from a duplicate bucket.

Example before:

```text
20 -> [rowA, rowB]
```

Delete:

```text
delete(20, rowB)
```

After:

```text
20 -> [rowA]
```

If the bucket still has values, the key remains in the leaf.

If the bucket becomes empty, the key is removed and the leaf may need rebalancing.

### `delete(key)`

This removes the whole key bucket.

Example before:

```text
20 -> [rowA, rowB]
```

Delete:

```text
delete(20)
```

After:

```text
20 is gone
```

## Delete Rebalancing

After a key disappears from a leaf, that leaf may have too few keys.

Minimum leaf keys:

```text
ceil((branchingFactor - 1) / 2)
```

For `branchingFactor = 4`:

```text
max keys per node = 3
min leaf keys = 2
```

The delete algorithm handles this in order:

1. If the page is the root, write it back.
2. If the leaf still has enough keys, write it and refresh parent separator keys.
3. Try to borrow one key/value bucket from the left sibling.
4. Try to borrow one key/value bucket from the right sibling.
5. Merge with the left sibling if possible.
6. Otherwise merge with the right sibling.
7. Remove the deleted child page from the parent.
8. Rebalance the parent internal page if it now has too few children.

## Internal Page Rebalancing

When a child page is removed from an internal page, the internal page may have too few children.

Minimum internal children:

```text
ceil(branchingFactor / 2)
```

For `branchingFactor = 4`:

```text
min internal children = 2
```

The internal rebalance algorithm handles this in order:

1. If the internal page is the root and has one child, shrink the root.
2. If it still has enough children, rewrite it with refreshed separator keys.
3. Try to borrow one child from the left sibling.
4. Try to borrow one child from the right sibling.
5. Merge with the left sibling if possible.
6. Otherwise merge with the right sibling.
7. Continue upward if the parent now has too few children.

## Root Shrink

If the root is an internal page and deletion leaves it with only one child:

```text
old root
  only child page 7
```

the tree shrinks:

```text
rootPageId = 7
page 7 parentPageId = -1
```

This avoids keeping an unnecessary tree level.

## Parent Separator Refresh

Because internal keys are based on child first keys:

```text
keys[i] = first key of children[i + 1]
```

deleting the first key in a child can affect ancestors.

Example:

```text
parent keys: [10, 20]
child page keys: [10, 12, 18]
```

After deleting `10`:

```text
child page keys: [12, 18]
parent keys must become: [12, 20]
```

The disk store refreshes separator keys upward when this kind of boundary changes.

## RowPointer Equality

`RowPointer` has value equality:

```text
same pageId and same rowOffset => equal
```

This matters because a `RowPointer` read from disk is a new Java object.

Without value equality:

```java
delete(20, new RowPointer(9, 4))
```

would fail unless it was the exact same object instance that was originally inserted.

## Current Scope

Implemented:
- page codec for metadata, leaf pages, and internal pages
- full-tree save/load compatibility
- direct persisted exact search
- direct persisted delete by key/value
- direct persisted whole-key delete
- leaf borrow/merge
- internal borrow/merge
- root shrink
- metadata count updates
- `RowPointer` value equality

Not implemented yet:
- direct page-level insert
- range scan directly from persisted pages
- free page reuse after merges
- crash recovery or journaling
- buffer pool or page cache
- integration with SQL `WHERE` planning

## Testing

Main test file:

```text
DBMS-CLI/src/test/java/indexing/bplustree/BPlusTreeDiskStoreTest.java
```

Covered behaviors:
- write/read one leaf node page
- save/load whole tree compatibility
- direct persisted search through an internal root
- direct persisted search when the root is a leaf
- delete one value from a duplicate bucket
- missing-value delete returns `false`
- whole-key delete keeps search correct after rebalancing
- deleting many keys can shrink the persisted root

Focused command:

```bash
mvn -q -Dtest=indexing.bplustree.BPlusTreeDiskStoreTest test
```

Full CLI test command:

```bash
mvn -q test
```
