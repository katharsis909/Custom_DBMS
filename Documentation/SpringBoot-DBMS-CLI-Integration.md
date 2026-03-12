# Spring Boot Integration for MiniSQL DBMS CLI

## Feature Summary
This feature wraps the existing MiniSQL CLI engine into a Spring Boot application so SQL can be executed through HTTP endpoints using a controller-service design.

The original parser/evaluator pipeline is reused as-is:
`Lexer -> ParserContext -> StatementListParser -> AST evaluate(Catalog)`.

## What Was Added

The repository now uses two Maven modules:
- `DBMS-CLI` (engine module)
- `dbms-spring-boot` (web/API module)

Core DBMS integration pieces:
- `DBMS-CLI/src/main/java/dbmscli/DbmsCliEngine.java`
- `execute(String sql)`
  - parses and evaluates SQL using the existing compiler-style pipeline
  - returns the rendered text form of the structured execution result
- `executeStructured(String sql)`
  - returns structured result blocks for the web layer
  - exposes table results as columns plus row values instead of raw printed text
- `reset()`
  - reinitializes the in-memory `Catalog`
- `DBMS-CLI/src/main/java/dbmscli/SourceDocument.java`
  - maps flat source positions to line and column
- `DBMS-CLI/src/main/java/dbmscli/SqlErrorFormatter.java`
  - formats parse and runtime errors consistently for both CLI and Spring Boot
- `dbms-spring-boot/src/main/java/com/example/dbmsspringboot/service/SqlExecutionService.java`
  - validates input, delegates to the engine, and maps structured result blocks into API DTOs
- `dbms-spring-boot/src/main/resources/static/`
  - serves the browser workbench frontend with editor-style input, red error rendering, and HTML tables for `SELECT`

## API Endpoints

### Execute SQL
- Method: `POST`
- Path: `/api/sql/execute`
- Body:
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

### Reset in-memory catalog
- Method: `POST`
- Path: `/api/sql/reset`
- Body: none

Response example:
```json
{
  "success": true,
  "output": "Catalog reset",
  "error": null
}
```

## Execution Behavior Notes
- If incoming SQL does not end with `;`, service appends one.
- Multiple SQL statements in one request are supported if semicolon-terminated.
- Catalog is in-memory and process-local.
- All API errors are returned through `SqlResponse`.
- Successful execution returns `results` blocks for structured rendering.
- `SELECT` output is exposed in both forms:
  - `output` as rendered text
  - `results` as structured columns and rows
- Error messages are formatted through the shared DBMS-side formatter, so CLI and browser errors use the same line-aware text.

## Supported Statements (Current)
- `CREATE TABLE`
- `INSERT INTO`
- `SELECT ... FROM ... [WHERE ... AND ...]`
- `DROP TABLE`

## Quick Test Statements
Use with `/api/sql/execute`:

1. `CREATE TABLE students (id INT, name STRING);`
2. `INSERT INTO students (1, 'Ava');`
3. `INSERT INTO students (2, 'Noah');`
4. `SELECT * FROM students;`
5. `SELECT name FROM students WHERE id = 1;`
6. `SELECT * FROM students WHERE id = 2 AND name = 'Noah';`
7. `DROP TABLE students;`

## Build and Run
From repository root:

```bash
mvn -pl dbms-spring-boot -am spring-boot:run
```

Default port:
- `8080`

## Current Limitations
- No persistence (data is lost on restart).
- Concurrency model is synchronized at engine level (`DbmsCliEngine` methods are synchronized).
- Pagination is not implemented yet for table results.
- Only equality operator (`=`) is currently implemented for WHERE conditions.
