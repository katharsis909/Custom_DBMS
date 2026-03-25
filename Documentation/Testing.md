# Testing

This document is the central place for project testing notes. It can grow as more unit, integration, and system tests are added.

## Current Test Areas

### Lexer Testing

The current automated testing work is focused on `Lexer.nextToken()`.

Files:
- `DBMS-CLI/src/test/java/LEXICAL/LexerNextTokenCsvTest.java`
- `DBMS-CLI/src/test/resources/lexer-next-token-cases.csv`

What this testing does:
- reads one CSV row as one lexer scenario
- creates one `Lexer` for that input
- calls `nextToken()` in a loop
- checks token `type` and `lexeme` after each call
- stops at `EOF`
- verifies `LexerException` for inputs that should fail immediately

What is covered:
- simple SQL token flow
- keywords and identifiers
- lowercase and mixed-case keyword input
- spaces, tabs, and newlines
- symbols such as `(` `)` `,` `=` `*` `;`
- numeric literals
- string literals
- invalid identifiers
- invalid numeric literals
- unterminated strings
- unsupported characters such as `@`
- empty input

Why this test exists:
- it checks the lexer as a token stream, not just one call
- it keeps many scenarios in one small CSV file
- it is easy to extend while learning JUnit

### ParserContext Testing

The current parser-context unit testing is focused on `ParserContext` alone.

Files:
- `DBMS-CLI/src/test/java/SEMANTIC/PARSER/util/ParserContextCsvTest.java`
- `DBMS-CLI/src/test/resources/parser-context-cases.csv`

What this testing does:
- reads one CSV row as one parser-context scenario
- uses Mockito to mock `Lexer`
- scripts the mocked `nextToken()` calls from the CSV
- checks `current()`, `advance()`, `expect(...)`, `isAtEnd()`, `error()`, and `errorAt()`
- verifies both `ParseException` and `LexerException` paths

What is covered:
- constructor loading the first token
- advancing to the next token
- successful `expect(...)`
- failing `expect(...)`
- `isAtEnd()` for normal tokens and `EOF`
- `error()` using current token position
- `errorAt()` using explicit position
- lexer exception propagation from constructor
- lexer exception propagation from `advance()`
- lexer exception propagation from `expect(...)`

Why this test exists:
- it isolates `ParserContext` from real lexer behavior
- it checks parser state changes one step at a time
- it keeps many small context scenarios in one CSV file

Runtime note:
- Mockito-based lexer mocking was verified under Java 17
- Java 24 caused local mocking/tooling issues in this environment
- local development should use Java 17 for this project

## Running Tests

From the repository root:

```bash
mvn -pl DBMS-CLI test
```

From inside `DBMS-CLI/`:

```bash
mvn test
```

## Adding More Tests

For lexer testing:
- add a new row to `lexer-next-token-cases.csv`
- write the input text
- list expected token types with `|`
- list expected lexemes with `|`
- add an exception message only if an exception is expected

Use `<EMPTY>` when an expected lexeme is an empty string, such as for `EOF`.

For parser-context testing:
- add a new row to `parser-context-cases.csv`
- describe the mocked lexer token stream in `lexer_steps`
- choose the `ParserContext` operation to run
- fill only the expected result fields that matter for that scenario
- add notes so failure messages stay readable
