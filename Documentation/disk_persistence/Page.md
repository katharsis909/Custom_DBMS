# Page

## Responsibility

`Page` is the fixed-size 4 KB physical storage unit.

It stores:
- a page header at the front
- row slot offsets after the header
- row bytes from the back of the page
- free space in the middle

## Current Layout

```text
[ header ][ slot directory -> ][ free space ][ <- row data ]
```

## Key Fields
- `pageId`
- `rowCount`
- `freePtr`
- `dataPtr`

## Main Operations
- `hasSpace(int rowSize)`
- `insertRow(byte[] row)`
- `getRow(int slotIndex)`
- `getData()`
- `loadData(byte[] bytes)`

## Insert Return Value
`insertRow(...)` now returns the inserted row's starting offset inside the page.

That offset is the physical row location later paired with `pageId` to form a `RowPointer`.

## Why It Exists
Without `Page`, rows would either stay in memory or require ad hoc file writes. `Page` gives a predictable and compact physical unit that `PageManager` can persist directly.

## Important Notes
- row access is offset-based, not scan-based
- rows are appended from the back of the page
- slot offsets are 2 bytes each
- a page file is exactly one page object serialized as raw bytes

## Limitations
- no row deletion reuse yet
- no compaction
- row length is derived by adjacent slot offsets
