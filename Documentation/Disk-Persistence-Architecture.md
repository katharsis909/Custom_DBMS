# Disk Persistence Architecture

## Purpose

This document explains the persistence feature top-down.

The original MiniSQL project stored all table rows only in memory. After this change, tables are persisted on disk and automatically reloaded when the engine starts again.

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
- `INSERT` now returns a physical row reference internally as `(pageId, rowOffset)` for future index structures.

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

## Package Structure

```text
DBMS-CLI/src/main/java/disk_persistence/
  Page.java
  PageManager.java
  RowPointer.java
  RowSerializer.java
  TableIterator.java
```

Subdocuments:
- [disk_persistence Package Overview](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/README.md)
- [Page](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/Page.md)
- [PageManager](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/PageManager.md)
- [RowPointer](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/RowPointer.md)
- [RowSerializer](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/RowSerializer.md)
- [TableIterator](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/TableIterator.md)

## Statement-Level Behavior

### CREATE TABLE
```text
CreateTableStatement
-> Catalog.addTable(...)
-> new Table(tableName, schema)
-> write schema.txt
-> initialize primary_key_index when a primary key exists
-> create/load PageManager
```

### INSERT INTO
```text
InsertIntoStatement
-> Table.addRecord(...)
-> build Record
-> validate primary-key values are non-empty and unique through B+ tree lookup
-> RowSerializer.serialize(...)
-> PageManager.insertRow(...)
-> RowPointer(pageId, rowOffset)
-> insert primary-key value into B+ tree index
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
- no indexing
- no crash recovery
- no buffer pool beyond the current page
- no pagination in the frontend yet

## Recommended Next Steps
1. Support deleted-row skipping using the row deleted flag.
2. Add free-space reuse or compaction.
3. Add better page discovery/caching inside `PageManager`.
4. Use `RowPointer` as the reference payload for index entries.
5. Separate “reload catalog” and “wipe persisted data” as distinct admin operations.
