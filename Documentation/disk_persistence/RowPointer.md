# RowPointer

## Responsibility

`RowPointer` is a small value object that identifies a persisted row by its physical location.

It contains:
- `pageId`
- `rowOffset`

## Why It Exists
The storage layer already knows the page id and row offset at insert time. `RowPointer` packages that information into one object so it can later be used by index structures such as a B-tree.

## Current Use
The current SQL execution flow does not consume `RowPointer` yet. It is exposed now as a minimal future seam for indexing work.

## Stability Note
In the current append-only design, `(pageId, rowOffset)` is a practical physical reference.

If the system later adds compaction or row relocation, this pointer may need extra indirection or update logic.
