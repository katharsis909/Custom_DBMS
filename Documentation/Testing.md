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

### Parser Unit Tests

Most parser classes now follow one shared unit-testing pattern.

Files:
- `DBMS-CLI/src/test/java/SEMANTIC/PARSER/BasicElementParserTest.java`
- `DBMS-CLI/src/test/java/SEMANTIC/PARSER/CollectionParserTest.java`
- `DBMS-CLI/src/test/java/SEMANTIC/PARSER/StatementBodyParserTest.java`
- `DBMS-CLI/src/test/java/SEMANTIC/PARSER/StatementParserTest.java`
- `DBMS-CLI/src/test/java/SEMANTIC/PARSER/StatementListParserTest.java`

Shared approach:
- mock `ParserContext`
- use Mockito static mocking for child parser calls where needed
- return real AST objects or concrete mocked AST nodes
- verify only the parser’s own responsibility: routing, loop control, field assignment, or error creation

One example:
- `ColumnDefinitionParser` does not need a real SQL string
- the test mocks `IdentifierParser.parse(ctx)` and `DataTypeParser.parse(ctx)`
- then it checks that the resulting `ColumnDefinition` stores those exact objects

Covered parser groups:
- leaf/simple parsers such as `IdentifierParser`, `LiteralParser`, `DataTypeParser`, `OperatorParser`, `WhereClauseParser`
- composed element parsers such as `ColumnDefinitionParser`, `ColumnMentionParser`, `ValueParser`, `UnaryConditionParser`
- list parsers such as `ColumnDefinitionListParser`, `ConditionListParser`, `ValueListParser`, `SelectedColumnListParser`
- statement body parsers such as `CreateTableStatementParser`, `DropTableStatementParser`, `InsertIntoStatementParser`, `SelectStatementParser`
- top-level coordinators such as `StatementParser` and `StatementListParser`

Outliers:
- `ParserContext` is tested with a CSV-driven mocked lexer because it is stateful and step-based
- `LiteralParser` needs separate branch tests for string, numeric, and invalid tokens
- `SelectedColumnListParser` needs separate tests for `*` and explicit column lists
- `SelectStatementParser` needs separate tests for with-`WHERE` and without-`WHERE`
- `StatementListParser` is best tested with a few direct control-flow cases instead of a CSV file
- `CreateTableStatementParser` is still a direct unit test for local guard clauses such as missing keywords or parentheses

### AST Node Unit Tests

The AST model layer now follows one shared unit-testing pattern too.

File:
- `DBMS-CLI/src/test/java/SEMANTIC/AST_NODES/AstNodeTest.java`

Shared approach:
- keep the tests at AST-node level instead of driving them through full SQL parsing
- use real leaf/value objects for simple state and evaluation behavior
- use Mockito for collaborators such as `Catalog`, `Table`, `TableIterator`, `ConditionList`, `SelectedColumnList`, and `ValueList`
- verify only the node’s own responsibility: storing fields, delegating to collaborators, building result rows, or attaching source positions to errors

One example:
- `InsertIntoStatement` does not need a real parser or database directory
- the test mocks `Catalog`, `Table`, and `ValueList`
- then it checks that the statement fetches the table, evaluates values against the table schema, and passes the evaluated row to `table.addRecord(...)`

Covered node groups:
- leaf and metadata nodes such as `Identifier`, `DataType`, `Operator`, `NumericLiteral`, `StringLiteral`
- structural nodes such as `ColumnDefinition`, `ColumnDefinitionList`, `Value`, `ValueList`, `ConditionList`, `WhereClause`, `SelectedColumnList`
- record-reading nodes such as `ColumnMention` and `UnaryCondition`
- executable statement nodes such as `CreateTableStatement`, `DropTableStatement`, `InsertIntoStatement`, `SelectStatement`, `Statement`, `StatementList`

Outliers:
- `CreateTableStatement` uses a static converter helper, so the test statically mocks `Converter.Main.toColumnListFromDefinition(...)`
- `SelectStatement` needs mocked `TableIterator` behavior because it owns row filtering, row-to-string conversion, and header construction
- `Statement.evaluate(...)` is one of the few AST tests that captures `System.out`, because its observable behavior is printed output rather than returned data

### Persistence Unit Tests

The disk-persistence layer now uses a mixed testing strategy: CSV for compact data-shape scenarios and Mockito for collaborator-heavy iterator behavior.

Files:
- `DBMS-CLI/src/test/java/disk_persistence/PageCsvTest.java`
- `DBMS-CLI/src/test/java/disk_persistence/RowSerializerCsvTest.java`
- `DBMS-CLI/src/test/java/disk_persistence/PageManagerTest.java`
- `DBMS-CLI/src/test/java/disk_persistence/TableIteratorTest.java`
- `DBMS-CLI/src/test/java/disk_persistence/RowPointerTest.java`
- `DBMS-CLI/src/test/resources/page-cases.csv`
- `DBMS-CLI/src/test/resources/row-serializer-cases.csv`

Shared approach:
- use CSV rows for compact page-layout and row-encoding scenarios where the inputs and outputs are mostly data
- use Mockito where a class is mainly coordinating with collaborators, especially `TableIterator` and mocked `Table` schemas
- use real filesystem IO only for `PageManager`, because its job is exactly disk-backed page lifecycle behavior
- keep each test focused on one responsibility: page slot behavior, row encoding rules, iterator page traversal, page-manager flushing, or pointer metadata

One example:
- `RowSerializer` does not need a real table on disk
- the test mocks `Table.getColumnList()` to provide a schema
- then one CSV row can describe a full serialize/deserialize round trip, an unsupported type, or a known future gap such as the currently ignored deleted flag

Covered persistence classes:
- `Page`
- `PageManager`
- `RowPointer`
- `RowSerializer`
- `TableIterator`

Outliers:
- `PageManager` uses real temporary table directories under `data/` because mocking file IO would miss the behavior we care about
- `TableIterator` uses static mocking of `RowSerializer.deserialize(...)` so the test stays focused on page traversal rather than row decoding internals
- some CSV rows intentionally describe future-facing gaps, such as unsupported column types, fixed string-size limits, and the currently ignored deleted-row flag

### B+ Tree Persistence Tests

The B+ tree persistence tests focus on the on-disk index page format and direct page operations.

Files:
- `DBMS-CLI/src/main/java/indexing/bplustree/BPlusTreeDiskStore.java`
- `DBMS-CLI/src/main/java/indexing/bplustree/BPlusTreePageCodec.java`
- `DBMS-CLI/src/test/java/indexing/bplustree/BPlusTreeDiskStoreTest.java`

What this testing does:
- writes and reads one persisted leaf node page
- saves and loads a whole B+ tree snapshot for compatibility
- manually writes persisted B+ tree pages and searches them without loading the whole tree
- checks search through an internal root page
- checks search when the root is already a leaf page
- deletes one value from a duplicate key bucket
- checks that missing-value delete returns `false`
- deletes whole keys and verifies search still works after rebalancing
- deletes enough keys to verify root shrinking

Important behavior:
- `search(key)` reads only the root-to-leaf page path.
- `delete(key, value)` and `delete(key)` mutate persisted pages directly.
- delete may rewrite the target leaf, siblings, parent pages, and metadata, but it does not call `load()` or rebuild the whole tree.
- `RowPointer` has value equality so persisted row references can be deleted by `(pageId, rowOffset)`.

Focused command:

```bash
mvn -q -Dtest=indexing.bplustree.BPlusTreeDiskStoreTest test
```

Design notes:
- tests use `@TempDir` so B+ tree page files are real files but isolated
- manual page fixtures keep direct search tests independent from in-memory insertion behavior
- whole-tree save/load tests still exist to verify snapshot compatibility
- delete tests intentionally exercise duplicate buckets, missing values, leaf/internal rebalancing, and root shrink

### Statement Benchmarks

The project now also has JMH benchmarks for statement execution costs.

Files:
- `DBMS-CLI/src/test/java/benchmark/StatementBenchmark.java`
- `DBMS-CLI/src/test/java/benchmark/StatementBenchmarkRunner.java`

What is benchmarked:
- `insert100Rows`
- `selectAll`
- `selectSingleColumn`
- `selectWhereSingleMatch`
- `selectWhereNoMatch`

Dataset sizes:
- insert benchmark runs `100` inserts per benchmark invocation
- select benchmarks run against `10_000` and `100_000` preloaded rows

Run command:

```bash
mvn -q -DskipTests test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=benchmark.StatementBenchmarkRunner
```

Benchmark note:
- the Maven runner is configured to use non-forked JMH runs so it works directly from the test classpath
- that is convenient for local comparison, but forked standalone runs are still better when you want more rigorous numbers

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

For parser unit testing:
- add another direct JUnit test method when a parser gets a new branch or delegated child parser
- keep each test focused on one parser responsibility
- only add separate tests for real outliers such as optional branches, list loops, or special error formatting

For AST node unit testing:
- add another direct JUnit test method when a node gains new local behavior or error handling
- prefer real AST/value objects for simple nodes and Mockito only at collaborator boundaries
- document only the unusual cases in this file, not every getter/setter test individually

For persistence testing:
- add a new CSV row when a page or serializer scenario is mostly data-driven
- add a direct JUnit test when disk behavior, iterator control flow, or collaborator wiring is the real thing being verified
- keep future-gap cases explicit in the CSV notes so unsupported behavior is documented on purpose

For B+ tree persistence testing:
- add direct JUnit tests instead of CSV rows, because the behavior depends on page topology
- prefer small manual page fixtures when testing direct disk search
- use larger saved trees when testing delete rebalancing and root shrink
- after changing delete or page metadata, run `mvn -q -Dtest=indexing.bplustree.BPlusTreeDiskStoreTest test`

For statement benchmarks:
- add a new benchmark method when you want to compare a distinct execution shape, not just another correctness branch
- keep benchmark setup out of the timed method unless setup cost is part of the thing you want to measure
- treat the numbers as comparative signals, not hard pass/fail thresholds
