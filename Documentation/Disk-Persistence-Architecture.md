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
- `CREATE TABLE` records foreign-key metadata and validates the referenced table/column exists.
- `DROP TABLE` removes the on-disk table directory.
- `INSERT` returns a physical row reference internally as `(pageId, rowOffset)` and uses it to maintain B+ tree indexes.

## Storage Layout

Each table is stored under:

```text
data/
  <tableName>/
    schema.txt
    indexes.txt
    primary_key_index/
      bptree_page_0.dat
      ...
    indexes/
      <indexName>/
        bptree_page_0.dat
        ...
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
-> persist foreign-key metadata when present
-> create/load PageManager
```

### INSERT INTO
```text
InsertIntoStatement
-> Table.addRecord(...)
-> build Record
-> validate foreign-key references against parent tables when FK metadata exists
-> validate primary-key values are non-empty and unique through B+ tree lookup
-> RowSerializer.serialize(...)
-> PageManager.insertRow(...)
-> RowPointer(pageId, rowOffset)
-> insert primary-key value into B+ tree index
-> insert secondary-index values into their B+ tree indexes
-> flush current page to disk
```

### SELECT
```text
SelectStatement
-> ask Table for indexed records when WHERE can use a primary-key or secondary B+ tree
-> otherwise Table.iterator()
-> TableIterator / RowPointer
-> PageManager.loadPage(...)
-> Page.getRow(...) or Page.getRowByOffset(...)
-> RowSerializer.deserialize(...)
-> JOIN combinations when multiple tables are present
-> WHERE recheck
-> ORDER BY using an index-backed order path for simple single-table cases, or an in-memory sort after filtering/joining
-> projection
```

### JOIN
```text
SelectStatement
-> collect base table plus all JOIN tables
-> optimizer orders table scans using row counts and whether join columns are indexed
-> build combined records with qualified keys such as table.column
-> aliases add equivalent keys such as alias.column
-> apply every JOIN equality condition
-> apply WHERE and projection
```

### GROUP BY
```text
SelectStatement
-> validate SELECT contains only grouped columns and aggregate expressions
-> choose grouping strategy
-> currently hash grouped records by GROUP BY key
-> compute COUNT/SUM/AVG/MIN/MAX
-> apply HAVING aggregate predicates when present
-> default unaliased aggregate headers to agg1, agg2, ...
-> apply ORDER BY to grouped output when grouped columns are referenced
```

### ORDER BY
```text
SelectStatement
-> parse ORDER BY column [ASC|DESC] entries
-> for single-table, single-column ORDER BY, ask Table for B+ tree ordered records when the column has a primary-key or secondary index
-> compare the estimated indexed-order cost with WHERE/filter/sort cost
-> for multi-column or joined ordering, sort the filtered or joined rows with the ORDER BY comparator
-> qualified table.column and alias.column names are supported
```

### DROP TABLE
```text
DropTableStatement
-> Catalog.dropTable(...)
-> remove table from memory
-> delete data/<tableName>/
   (schema, pages, primary-key index, secondary indexes, and index metadata)
```

## Design Choices

### Why keep the parser and AST unchanged?
This keeps the persistence work isolated to the storage boundary. SQL syntax and most AST logic do not need to know whether rows come from memory or disk.

### Why store schema separately?
Page files store rows compactly and do not repeat column names. `schema.txt` gives the ordered column definition needed for serialization and deserialization.

### Why iterator-based SELECT?
This avoids loading every row into memory before filtering. It also matches the long-term direction of page-by-page scans.

### How are RowPointers used by indexes?
The page id and row offset are already known at insert time. B+ tree leaf values store `RowPointer` objects so indexed SELECT can fetch candidate rows directly from page storage before rechecking the full WHERE clause.

### Current range-scan implementation note
Right now `BPlusTreeDiskStore.searchRange(...)` loads the persisted B+ tree into memory and then scans the leaf entries. That means range scans currently import the tree/leaf contents instead of walking only the needed on-disk leaf pages. This is intentionally simple for now and can be improved later by following the leaf-page chain directly from disk.

The same limitation applies to the current ORDER BY index path: ordered scans load the persisted B+ tree and then walk leaf entries in memory. A later version can stream the needed leaf pages directly from disk.

### GROUP BY index note
GROUP BY currently uses hash grouping. The query code has a strategy hook where B+ tree grouping can be added later, including composite-index grouping when the GROUP BY column order matches the composite index order.

### Concurrency note
`DbmsCliEngine.execute...` is synchronized, and catalog/table mutation paths are synchronized so local concurrent calls do not interleave catalog updates, inserts, or index maintenance. This is still coarse-grained locking, not transaction isolation.

### Foreign-key integrity note
Foreign-key grammar and metadata are supported now. `CREATE TABLE` validates that the referenced table and column exist, and `INSERT` validates that each child value exists in the referenced parent column before writing the row. Delete-time referential actions such as restrict/cascade/set-null are not implemented yet.

## Current Limitations
- append-only inserts
- current page flushed on each insert
- no delete reuse yet
- no compaction
- range scans currently load the persisted B+ tree before scanning leaf entries
- ORDER BY index scans currently load the persisted B+ tree before scanning ordered leaf entries
- GROUP BY does not yet stream groups directly from B+ tree indexes or composite indexes
- foreign keys do not yet enforce delete-time referential actions
- pagination is intentionally deferred
- no crash recovery
- no buffer pool beyond the current page
- no pagination in the frontend yet

## Recommended Next Steps
1. Support deleted-row skipping using the row deleted flag.
2. Add free-space reuse or compaction.
3. Add better page discovery/caching inside `PageManager`.
4. Optimize range scans to walk B+ tree leaf pages directly from disk.
5. Separate “reload catalog” and “wipe persisted data” as distinct admin operations.
