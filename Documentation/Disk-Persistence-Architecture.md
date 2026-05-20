# Disk Persistence Architecture

## Purpose

This document explains the persistence feature top-down.

The original MiniSQL project stored all table rows only in memory. After this change, tables are persisted on disk and automatically reloaded when the engine starts again.

The project also now has a persisted B+ tree index format. The index is not yet wired into SQL planning, but its pages can be saved, searched, loaded, and deleted from directly.

## High-Level Flow

```text
SQL
-> Lexer
-> Parser
-> AST Statement
-> Catalog
-> Table
-> disk_persistence
-> Disk
```

The parser and AST design remain the same. The major change is below `Table`.

## What Changed

### CLI changes
- The CLI still reads SQL, parses it, and executes it through the same statement pipeline.
- The `Catalog` created in the CLI now auto-loads tables from `data/`.
- Restarting the CLI no longer loses tables and rows.
- Errors still use the shared line-aware formatter.

### Spring Boot changes
- The Spring service still calls the same engine entrypoint.
- The engine now works against a persistence-backed `Catalog` instead of a memory-only one.
- `/api/sql/reset` now reloads the in-process catalog from disk instead of behaving like a wipe.
- Structured `results` output still works the same for the frontend.

### Core engine changes
- `Table` no longer uses an in-memory `List<Record>` as the physical source of truth.
- `Table` now owns a `PageManager`.
- `SELECT` now streams rows using `TableIterator`.
- `CREATE TABLE` persists schema.
- `DROP TABLE` removes the on-disk table directory.
- `INSERT` now returns a physical row reference internally as `(pageId, rowOffset)` for index structures.
- The B+ tree disk store can persist index pages and perform exact-key search/delete without loading the whole tree.

## Storage Layout

Each table is stored under:

```text
data/
  <tableName>/
    schema.txt
    page_0.dat
    page_1.dat
    ...
```

### `schema.txt`
Stores the ordered schema for the table.

Example:

```text
name STRING
age INT
marks INT
```

### `page_<id>.dat`
Each page file stores one fixed-size 4 KB page.

## Index Storage Layout

B+ tree indexes use fixed-size page files too, but they are separate from table row pages.

An index directory stores:

```text
bptree_page_0.dat
bptree_page_1.dat
bptree_page_2.dat
...
```

Page `0` is the B+ tree metadata page. It stores the branching factor, root page id, first leaf page id, distinct key count, total value count, and node count.

Other pages are B+ tree nodes:
- leaf pages store sorted keys and value buckets
- internal pages store separator keys and child page ids

Detailed index documentation:
- [B+ Tree Persistence](indexing/BPlusTreePersistence.md)

## Package Structure

```text
DBMS-CLI/src/main/java/disk_persistence/
  Page.java
  PageManager.java
  RowPointer.java
  RowSerializer.java
  TableIterator.java

DBMS-CLI/src/main/java/indexing/bplustree/
  BPlusTree.java
  BPlusTreeDiskStore.java
  BPlusTreePageCodec.java
  BPlusTreeSerializers.java
  BPlusTreeValueSerializer.java
```

Subdocuments:
- [disk_persistence Package Overview](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/README.md)
- [Page](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/Page.md)
- [PageManager](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/PageManager.md)
- [RowPointer](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/RowPointer.md)
- [RowSerializer](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/RowSerializer.md)
- [TableIterator](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/TableIterator.md)
- [B+ Tree Persistence](indexing/BPlusTreePersistence.md)

## Statement-Level Behavior

### CREATE TABLE
```text
CreateTableStatement
-> Catalog.addTable(...)
-> new Table(tableName, schema)
-> write schema.txt
-> create/load PageManager
```

### INSERT INTO
```text
InsertIntoStatement
-> Table.addRecord(...)
-> build Record
-> RowSerializer.serialize(...)
-> PageManager.insertRow(...)
-> RowPointer(pageId, rowOffset)
-> flush current page to disk
```

### SELECT
```text
SelectStatement
-> Table.iterator()
-> TableIterator
-> PageManager.loadPage(...)
-> Page.getRow(...)
-> RowSerializer.deserialize(...)
-> WHERE
-> projection
```

### DROP TABLE
```text
DropTableStatement
-> Catalog.dropTable(...)
-> remove table from memory
-> delete data/<tableName>/
```

## Design Choices

### Why keep the parser and AST unchanged?
This keeps the persistence work isolated to the storage boundary. SQL syntax and most AST logic do not need to know whether rows come from memory or disk.

### Why store schema separately?
Page files store rows compactly and do not repeat column names. `schema.txt` gives the ordered column definition needed for serialization and deserialization.

### Why iterator-based SELECT?
This avoids loading every row into memory before filtering. It also matches the long-term direction of page-by-page scans.

### Why add RowPointer now?
The page id and row offset are already known at insert time. Exposing them now creates a clean future seam for B-tree leaf references without forcing index implementation yet.

## Current Limitations
- append-only inserts
- current page flushed on each insert
- no delete reuse yet
- no compaction
- B+ tree persistence exists, but SQL execution does not use indexes yet
- direct B+ tree page-level insert is not implemented yet
- no crash recovery
- no buffer pool beyond the current page
- no pagination in the frontend yet

## Recommended Next Steps
1. Support deleted-row skipping using the row deleted flag.
2. Add free-space reuse or compaction.
3. Add better page discovery/caching inside `PageManager`.
4. Implement direct page-level B+ tree insert.
5. Use `RowPointer` indexes in `WHERE` planning for exact-match predicates.
6. Separate “reload catalog” and “wipe persisted data” as distinct admin operations.
