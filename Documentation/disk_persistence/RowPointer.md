# RowPointer

## Responsibility

`RowPointer` is a small value object that identifies a persisted row by its physical location.

It contains:
- `pageId`
- `rowOffset`

## Why It Exists
The storage layer already knows the page id and row offset at insert time. `RowPointer` packages that information into one object so it can later be used by index structures such as a B-tree.

## Current Use
The current SQL execution flow does not consume `RowPointer` yet.

The B+ tree persistence layer can already use `RowPointer` as a leaf value:

```text
index key -> RowPointer(pageId, rowOffset)
```

This is the physical reference an index needs in order to find a row after locating a key.

## Equality

`RowPointer` uses value equality.

Two pointers are equal when both fields match:

```text
same pageId
same rowOffset
```

This matters for persisted index delete. A `RowPointer` read back from disk is a new Java object, so delete must compare the stored physical location rather than object identity.

## Stability Note
In the current append-only design, `(pageId, rowOffset)` is a practical physical reference.

If the system later adds compaction or row relocation, this pointer may need extra indirection or update logic.
