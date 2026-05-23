# disk_persistence Package

## Purpose

The `disk_persistence` package is the storage engine layer of the MiniSQL project.

It is responsible for:
- storing table rows in page files
- converting logical records to bytes and back
- managing the last writable page of a table
- exposing physical row references used by B+ tree indexes
- scanning rows page-by-page for query execution

## Package Role in Architecture

```text
Catalog
-> Table
-> disk_persistence
-> Disk
```

The package sits below the logical table abstraction and above the filesystem.

## Classes
- [Page](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/Page.md)
- [PageManager](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/PageManager.md)
- [RowPointer](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/RowPointer.md)
- [RowSerializer](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/RowSerializer.md)
- [TableIterator](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/TableIterator.md)

## Package Workflow

### Insert path
```text
Record
-> RowSerializer.serialize()
-> PageManager.insertRow()
-> Page.insertRow()
-> RowPointer(pageId, rowOffset)
-> page_<id>.dat
```

### Select path
```text
TableIterator
-> PageManager.loadPage()
-> Page.getRow()
-> RowSerializer.deserialize()
-> Record
```

### Indexed lookup path
```text
B+ tree index
-> RowPointer(pageId, rowOffset)
-> PageManager.loadPage(pageId)
-> Page.getRowByOffset(rowOffset)
-> RowSerializer.deserialize()
-> Record
```

## Current Scope
This package currently supports append-only persistence, sequential scans, and physical row lookup for indexes. It does not yet implement deletion reuse, compaction, caching, or crash recovery.
