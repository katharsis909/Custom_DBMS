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
- `INSERT INTO`
- `SELECT ... FROM ... [WHERE ... AND ...]`
- `DROP TABLE`

Current engine capabilities:
- disk-backed table persistence under `data/<tableName>/`
- persisted schema in `schema.txt`
- fixed-size `4096` byte page files
- streaming table scans through a page iterator
- physical row references available at insert time as `(pageId, rowOffset)`
- shared line-aware error formatting for CLI and Spring Boot
- structured query results for the web layer

## Essential Changes

### CLI
- catalog startup now reloads persisted tables from disk
- restart no longer loses tables and inserted rows
- `SELECT` now reads through persisted page scans instead of an in-memory record list
- inserts now expose a physical row reference internally for future index work
- line-aware errors continue to work as before

### Spring Boot
- the engine behind the controller/service is now persistence-backed
- `/api/sql/reset` reloads the in-process catalog from disk
- `results` payload still returns structured table blocks for the frontend
- the browser workbench was simplified to reduce noisy UI copy

## Persistence Overview

Each table is stored as:

```text
data/
  <tableName>/
    schema.txt
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
- inserts now have a minimal future index seam through `RowPointer`
- persistence is currently append-only
- there is no delete reuse, compaction, indexing, or crash recovery yet
- this project still favors educational clarity over production DB features
