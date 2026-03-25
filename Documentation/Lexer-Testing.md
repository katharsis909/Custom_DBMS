# Lexer Testing

This project now includes a CSV-driven JUnit test for `Lexer.nextToken()`.

## Files

- `DBMS-CLI/src/test/java/LEXICAL/LexerNextTokenCsvTest.java`
- `DBMS-CLI/src/test/resources/lexer-next-token-cases.csv`

## How It Works

- each CSV row is one lexer scenario
- the test creates one `Lexer` per input string
- it calls `nextToken()` in a loop
- token `type` and `lexeme` are collected after every call
- the loop stops when `EOF` is returned
- if `LexerException` is thrown, the test verifies that too

## What Is Covered

- keywords and identifiers
- lowercase and mixed-case keywords
- spaces, tabs, and newlines
- symbols such as `(` `)` `,` `=` `*` `;`
- string literals
- numeric literals
- invalid identifiers
- invalid numeric literals
- unterminated strings
- unsupported characters that throw `LexerException`
- empty input

## Running The Tests

From the repository root:

```bash
mvn -pl DBMS-CLI test
```

Or from inside `DBMS-CLI/`:

```bash
mvn test
```

## Adding More Cases

- add a new row in `lexer-next-token-cases.csv`
- write the input string
- list expected token types with `|`
- list expected lexemes with `|`
- fill the exception message only when an exception is expected

Use `<EMPTY>` for an empty lexeme, such as the `EOF` token.
