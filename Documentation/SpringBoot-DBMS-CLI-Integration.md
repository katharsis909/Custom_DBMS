# Spring Boot Integration for MiniSQL DBMS CLI

## Feature Summary
This feature wraps the existing MiniSQL CLI engine into a Spring Boot application so SQL can be executed through HTTP endpoints using a controller-service design.

The original parser/evaluator pipeline is reused as-is:
`Lexer -> ParserContext -> StatementListParser -> AST evaluate(Catalog)`.

## What Was Added

### 1. Multi-module project layout
A parent Maven project now contains two modules:
- `DBMS-CLI` (engine module)
- `dbms-spring-boot` (web/API module)

### 2. Engine facade for reuse
File: `DBMS-CLI/src/main/java/dbmscli/DbmsCliEngine.java`

Responsibilities:
- `execute(String sql)`
  - Parses and evaluates SQL using existing compiler-style pipeline.
  - Returns the rendered text form of the structured execution result.
- `executeStructured(String sql)`
  - Returns structured result blocks for the web layer.
  - Table results are exposed as columns plus row values instead of raw printed text.
- `reset()`
  - Reinitializes in-memory `Catalog`.

### 3. Spring Boot API layer
Files:
- `dbms-spring-boot/src/main/java/com/example/dbmsspringboot/controller/SqlController.java`
- `dbms-spring-boot/src/main/java/com/example/dbmsspringboot/service/SqlExecutionService.java`
- `dbms-spring-boot/src/main/java/com/example/dbmsspringboot/config/EngineConfig.java`
- DTOs under `dbms-spring-boot/src/main/java/com/example/dbmsspringboot/dto/`

Responsibilities:
- Controller exposes API endpoints.
- Service validates SQL input and delegates to `DbmsCliEngine`.
- Service maps structured DBMS result blocks into API DTOs.
- Config declares `DbmsCliEngine` as a Spring bean.

### 4. Browser workbench frontend
Files:
- `dbms-spring-boot/src/main/resources/static/index.html`
- `dbms-spring-boot/src/main/resources/static/styles.css`
- `dbms-spring-boot/src/main/resources/static/app.js`

Responsibilities:
- Presents SQL input in an editor-style textarea.
- Calls the existing Spring Boot API endpoints.
- Renders errors in red.
- Renders table result blocks as HTML tables.

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
  "error": "Table 'STUDENTS' already exists.",
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
- All API errors are returned as structured JSON via `SqlResponse`.
- The API now returns `results` blocks for successful execution.
- `SELECT` table output is exposed in both forms:
  - `output`: legacy text rendering
  - `results`: structured columns and rows for the frontend

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
