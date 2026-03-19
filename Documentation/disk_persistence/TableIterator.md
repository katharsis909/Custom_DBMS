# TableIterator

## Responsibility

`TableIterator` streams records from a persisted table.

It reads one page at a time and returns one logical `Record` at a time.

## Main Operations
- `hasNext()`
- `next()`

## Workflow
```text
TableIterator
-> PageManager.loadPage(pageId)
-> Page.getRow(slotIndex)
-> RowSerializer.deserialize(...)
-> Record
```

## Why It Exists
Before persistence, `SELECT` could iterate an in-memory list. After persistence, query execution needs a streaming abstraction that does not first rebuild the whole table in memory.

## Current Behavior
- starts at page 0
- scans rows in slot order
- advances page-by-page until the last page id

## Limitations
- does not skip logically deleted rows yet
- sequential scan only
- no predicate pushdown or indexing
