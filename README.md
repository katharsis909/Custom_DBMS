# Custom DBMS (MiniSQL) + Spring Boot Wrapper

This repository contains a custom in-memory MiniSQL DBMS engine and a Spring Boot API wrapper.

## Project Structure

- `DBMS-CLI/`
  - Core MiniSQL engine (lexer, parser, AST evaluation, catalog/table/record model)
  - BlueJ-compatible source tree under `DBMS-CLI/src/main/java`
- `dbms-spring-boot/`
  - REST API wrapper around the DBMS engine
- `Documentation/`
  - Feature and integration notes
- `pom.xml`
  - Parent Maven multi-module build file

## Features

Current SQL support:
- `CREATE TABLE`
- `INSERT INTO`
- `SELECT ... FROM ... [WHERE ... AND ...]`
- `DROP TABLE`

## Spring Boot API

Base path: `/api/sql`

### Execute SQL
- `POST /api/sql/execute`
- Body:

```json
{
  "sql": "CREATE TABLE students (id INT, name STRING);"
}
```

### Reset In-Memory Catalog
- `POST /api/sql/reset`

## Build and Run

From repository root:

```bash
mvn -pl dbms-spring-boot -am spring-boot:run
```

Default server port: `8080`

## BlueJ Usage

Open this folder in BlueJ:

- `DBMS-CLI/src/main/java`

This is the BlueJ project root for the DBMS source package layout.

## Notes

- Data is in-memory and resets on application restart.
- This project currently favors educational clarity over production DB features.
