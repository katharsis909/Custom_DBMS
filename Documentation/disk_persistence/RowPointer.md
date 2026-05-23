# RowPointer

## Responsibility

`RowPointer` is a small value object that identifies a persisted row by its physical location.

It contains:
- `pageId`
- `rowOffset`

## Why It Exists
The storage layer already knows the page id and row offset at insert time. `RowPointer` packages that information into one object so B+ tree indexes can point back to persisted rows.

## Current Use
`INSERT` receives a `RowPointer` from `PageManager.insertRow(...)` and stores it in the primary-key B+ tree plus any secondary B+ tree indexes. Indexed `SELECT` uses the pointer to fetch candidate records directly from page storage.

## Stability Note
In the current append-only design, `(pageId, rowOffset)` is a practical physical reference.

If the system later adds compaction or row relocation, this pointer may need extra indirection or update logic.
