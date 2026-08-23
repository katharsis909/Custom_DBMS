# Custom DBMS (MiniSQL) + Spring Boot Wrapper

This repository contains a custom MiniSQL DBMS engine and a Spring Boot API wrapper.

## Project Structure

- `DBMS-CLI/`
  - core MiniSQL engine
  - lexer, parser, AST evaluation, catalog/table/record model
  - disk persistence layer under `DBMS-CLI/src/main/java/disk_persistence`
  - BlueJ-compatible source tree under `DBMS-CLI/src/main/java`
- `dbms-spring-boot/`
  - REST API wrapper around the DBMS engine
  - static browser workbench served at `/`
- `Documentation/`
  - architecture and integration notes
  - testing notes in `Documentation/Testing.md`
- `pom.xml`
  - parent Maven multi-module build file

## Features

Current SQL support:
- `CREATE TABLE`
- primary keys in `CREATE TABLE`, either inline (`id INT PRIMARY KEY`) or composite (`PRIMARY KEY (id, name)`)
- foreign-key metadata in `CREATE TABLE`, either inline (`child_id INT REFERENCES parent(id)`) or table-level (`FOREIGN KEY (child_id) REFERENCES parent(id)`)
- `CREATE INDEX index_name ON table_name (column_name[, column_name ...])`
- `INSERT INTO`
- `SELECT ... FROM ... [JOIN ... ON ...]* [WHERE ... AND ...]` with `=`, `!=`, `<`, `<=`, `>`, `>=`
- table aliases in SELECT/JOIN, using either `AS alias` or a bare alias, and qualified references like `alias.column`
- `GROUP BY` and `HAVING` with `COUNT`, `SUM`, `AVG`, `MIN`, and `MAX`; aggregate aliases use `AS`, otherwise default headers are `agg1`, `agg2`, ...
- `ORDER BY` with one or more columns, including qualified table/alias columns, and optional `ASC`/`DESC`
- `DROP TABLE`

Current engine capabilities:
- disk-backed table persistence under `data/<tableName>/`
- persisted schema in `schema.txt`
- fixed-size `4096` byte page files
- streaming table scans through a page iterator
- inserts capture each row's physical location as `(pageId, rowOffset)`, and B+ tree index entries store that `RowPointer` so indexed lookups can fetch the persisted row directly
- primary-key uniqueness and non-empty validation backed by a persisted B+ tree index
- inserts update the primary-key B+ tree and any secondary B+ tree indexes created with `CREATE INDEX`
- WHERE planning can use primary-key and secondary B+ tree indexes for equality, left-prefix composite lookups, and bounded range predicates
- ORDER BY planning can use a primary-key or secondary B+ tree index for a single-table ordering path when the ORDER BY columns match a left prefix of the index and share one direction; otherwise rows are sorted after filtering/joining
- multi-table JOIN execution supports multiple `JOIN ... ON left = right` clauses with qualified column names
- grouped SELECT allows only grouped columns plus aggregate expressions
- shared line-aware error formatting for CLI and Spring Boot
- structured query results for the web layer

Implementation note:
- when adding a new feature, also review whether it changes or weakens existing features, including cases where an older feature previously handled all of its valid cases but now only works for some of them, and update tests/docs accordingly

## Persistence Overview

Each table is stored as:

```text
data/
  <tableName>/
    schema.txt
    primary_key_index/
      bptree_page_0.dat
      ...
    indexes/
      <indexName>/
        bptree_page_0.dat
        ...
    indexes.txt
    page_0.dat
    page_1.dat
    ...
```

Top-down persistence documentation:
- [Disk Persistence Architecture](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/Disk-Persistence-Architecture.md)
- [disk_persistence Package](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/disk_persistence/README.md)

Testing documentation:
- [Testing](/Users/megha_shah/Documents/Ren_Proj/DBMS/Documentation/Testing.md)

## Statement Benchmark Snapshot

JMH benchmarks were run for statement execution in the CLI engine.

Measured shapes:
- `insert100Rows`
- `selectAll`
- `selectSingleColumn`
- `selectWhereSingleMatch`
- `selectWhereNoMatch`

Observed averages from the current benchmark run:

| Benchmark | Rows | Avg time |
| --- | ---: | ---: |
| `insert100Rows` | 100 inserts per invocation | `0.057 ms/op` |
| `selectAll` | 10,000 | `4.324 ms/op` |
| `selectAll` | 100,000 | `52.248 ms/op` |
| `selectSingleColumn` | 10,000 | `3.811 ms/op` |
| `selectSingleColumn` | 100,000 | `44.121 ms/op` |
| `selectWhereNoMatch` | 10,000 | `3.496 ms/op` |
| `selectWhereNoMatch` | 100,000 | `36.951 ms/op` |
| `selectWhereSingleMatch` | 10,000 | `3.354 ms/op` |
| `selectWhereSingleMatch` | 100,000 | `36.371 ms/op` |

Benchmark command:

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=benchmark.StatementBenchmarkRunner
```

Benchmark note:
- these numbers came from the current Maven-based non-forked JMH runner, so they are useful for local comparison inside this project, but not yet a rigorous cross-database benchmark

## Spring Boot API

Base path: `/api/sql`

### Execute SQL
- `POST /api/sql/execute`

Body:

```json
{
  "sql": "CREATE TABLE students (id INT, name STRING);"
}
```

Successful responses include:
- `output` for rendered text
- `results` for structured blocks with `columns` and `rows`

### Reload Catalog
- `POST /api/sql/reset`
- reloads catalog state from the persisted `data/` directory
- does not delete persisted tables

## Browser Workbench

The Spring Boot app serves a static frontend at `/` with:
- editor-style SQL input
- red error rendering
- HTML table rendering for `SELECT`
- labeled output blocks for table and message results

Pagination is intentionally deferred for later implementation.

## Build and Run

From repository root:

```bash
mvn -pl dbms-spring-boot -am spring-boot:run
```

Default server port: `8080`

## BlueJ Usage

Open this folder in BlueJ:
- `DBMS-CLI/src/main/java`

## Notes

- persisted table data is stored locally under `data/`
- `SELECT` now streams rows from persisted pages
- `CREATE TABLE` initializes the primary-key B+ tree when a primary key exists
- foreign-key references are validated at CREATE TABLE time and enforced during INSERT; delete/update referential actions are intentionally deferred
- `INSERT` maintains the primary-key B+ tree and all secondary B+ tree indexes
- `DROP TABLE` deletes the table directory, so schema, pages, primary-key index, and secondary indexes are removed together
- current range scans load the persisted B+ tree into memory before scanning leaf entries; this is intentionally simple for now and can later become a direct leaf-page walk
- current ORDER BY index scans also load the persisted B+ tree before walking ordered leaf entries; this keeps the first implementation simple and can later become a direct on-disk leaf scan
- GROUP BY currently uses hash grouping; the optimizer has an index-detection hook, but direct B+ tree grouping is still a later optimization
- persistence is currently append-only
- pagination is intentionally deferred for later implementation
- there is no delete reuse, compaction, or crash recovery yet
- this project still favors educational clarity over production DB features
