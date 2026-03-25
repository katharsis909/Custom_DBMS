# PageManager

## Responsibility

`PageManager` coordinates page storage for one table.

It owns:
- the table directory
- the current writable page
- page flushing and page loading
- physical row reference creation at insert time

## Current Design
- only the current page is kept in memory
- all older pages live on disk
- inserts are append-only into the last page
- the current page is flushed on every insert

## Main Operations
- `insertRow(byte[] row)`
- `loadPage(int pageId)`
- `flushCurrentPage()`
- `getCurrentPageId()`

## Insert Return Value
`insertRow(...)` now returns a `RowPointer`.

That object contains:
- `pageId`
- `rowOffset`

This gives higher layers a ready-made physical reference for future B-tree or other secondary index structures.

## Disk Contract
The manager writes files under:

```text
data/<tableName>/page_<id>.dat
```

Each file contains exactly one 4096-byte page.

## Why It Exists
`Table` should not know how to choose page files, load raw bytes, or manage the last writable page. `PageManager` isolates those storage concerns.

## Current Limitations
- no multi-page cache
- no prefetching
- no compaction
- no free-space index
- page discovery is still directory-scan based
