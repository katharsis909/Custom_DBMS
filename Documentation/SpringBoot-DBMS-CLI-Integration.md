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
  - Captures printed output from statement evaluation and returns it as a string.
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
- Config declares `DbmsCliEngine` as a Spring bean.

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
  "output": "OK",
  "error": null
}
```

Error response example:
```json
{
  "success": false,
  "output": null,
  "error": "Table 'STUDENTS' already exists."
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
- SQL output is text captured from `System.out` (legacy-compatible, not tabular JSON yet).
- Only equality operator (`=`) is currently implemented for WHERE conditions.
