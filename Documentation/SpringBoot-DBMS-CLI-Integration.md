# Spring Boot Integration for MiniSQL DBMS CLI

## Feature Summary
This project wraps the MiniSQL engine inside a Spring Boot application so SQL can be executed through HTTP endpoints using a controller-service design.

The execution pipeline is still:
`Lexer -> ParserContext -> StatementListParser -> AST -> Catalog/Table`.

The important change is that `Catalog` and `Table` are now backed by on-disk persistence.

## Essential Changes

### DBMS CLI side
- `Catalog` now auto-loads tables from `data/` on startup
- `Table` now owns schema plus `PageManager`
- `SELECT` now streams records through `TableIterator`
- `CREATE TABLE` writes `schema.txt`
- `DROP TABLE` deletes the table directory
- `INSERT` now exposes a physical row reference internally as `(pageId, rowOffset)`

### Spring Boot side
- `DbmsCliEngine` now works against a persistence-backed catalog
- `SqlExecutionService` still returns structured `results`
- `/api/sql/reset` now means reload catalog from disk
- the static frontend remains table-aware, but the visible copy was simplified

## Persistence Integration

The persistence package lives under:
- `DBMS-CLI/src/main/java/disk_persistence/`

Main classes:
- `Page`
- `PageManager`
- `RowPointer`
- `RowSerializer`
- `TableIterator`

Full storage documentation:
- [Disk Persistence Architecture](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/Disk-Persistence-Architecture.md)
- [disk_persistence Package](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/README.md)

## Disk Layout

```text
data/
  <tableName>/
    schema.txt
    page_0.dat
    page_1.dat
    ...
```

Notes:
- `schema.txt` stores the ordered schema used by row serialization
- each `page_<id>.dat` file is one fixed 4 KB page
- inserts are append-only into the last page
- insert-time row references are available for future index structures

## Statement Integration

### CREATE TABLE
- validates table name uniqueness in catalog
- persists schema to `schema.txt`
- creates a storage-backed `Table`
- registers it in memory

### INSERT INTO
- validates values against schema
- builds a logical `Record`
- serializes the row
- inserts and flushes it through `PageManager`
- produces a `RowPointer` internally for future index use

### SELECT
- scans with `TableIterator`
- loads page bytes through `PageManager`
- deserializes rows through `RowSerializer`
- applies `WHERE` and projection
- returns structured output blocks to the web layer

### DROP TABLE
- removes the table from catalog
- deletes the table directory from disk

## API Endpoints

### Execute SQL
- Method: `POST`
- Path: `/api/sql/execute`

Example body:
```json
{
  "sql": "CREATE TABLE students (id INT, name STRING);"
}
```

Success response example:
```json
{
  "success": true,
  "output": "| id | name |\n| 1  | Ava  |",
  "error": null,
  "results": [
    {
      "message": null,
      "columns": ["id", "name"],
      "rows": [["1", "Ava"]]
    }
  ]
}
```

Error response example:
```json
{
  "success": false,
  "output": null,
  "error": "Error at line 1, column 35 (position 34): Inserted value - 78 with data type STRING, which does not matches the data type INT of column - marks",
  "results": null
}
```

### Reload Catalog
- Method: `POST`
- Path: `/api/sql/reset`
- reloads catalog state from persisted storage

Example response:
```json
{
  "success": true,
  "output": "Catalog reloaded from disk",
  "error": null
}
```

## Browser Workbench

The UI served at `/` provides:
- SQL editor textarea
- Run and Reload Catalog actions
- red error output
- HTML tables for structured table results

The copy in the workbench was intentionally simplified so the page stays focused on execution rather than explanation.

## Build and Run

From repository root:

```bash
mvn -pl dbms-spring-boot -am spring-boot:run
```

Default port:
- `8080`

## Current Limitations
- append-only persistence
- current page flushed on each insert
- no deleted-row reuse yet
- no compaction
- no buffer pool beyond the current page
- no indexing or WAL
- no pagination in the frontend yet
- only equality operator (`=`) is implemented for `WHERE`
