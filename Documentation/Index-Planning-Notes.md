# Notes: Index Planning & Lookup Architecture

This document explains the purpose, architecture, and design rationale of the index planning classes in `QUERY_PLANNER/` and `STRUCTURE/`:
1. `IndexDefinition` (in `STRUCTURE/`)
2. `ColumnBounds` (in `QUERY_PLANNER/`)
3. `IndexedLookup` (in `QUERY_PLANNER/`)
4. `IndexedAccess` (in `QUERY_PLANNER/`)
5. `TableIndexPlanner` (in `QUERY_PLANNER/`)

---

## 1. Quick Reference Summary

| Class | What it is | Real-Life Analogy | Primary Role |
| :--- | :--- | :--- | :--- |
| **`IndexDefinition`** | Schema metadata for an index | The sign on a library drawer | Uniformly describes Primary Keys and Secondary Indexes. |
| **`ColumnBounds`** | Aggregated boundaries for **one column** | Your checklist criteria for one attribute | Collapses scattered WHERE conditions on a column into min/max/exact bounds. |
| **`IndexedLookup`** | The physical B+ Tree search instructions + score | The exact step-by-step instructions to pull books from a shelf | Translates column bounds into concatenated B+ Tree string keys and scores the candidate plan. |
| **`IndexedAccess`** | A pair: `(IndexDefinition, IndexedLookup)` | The clipboard note: "Use Drawer X with Instruction Y" | Packages the winning index and its search instructions together for the engine. |

---

## 2. Deep Dive: `ColumnBounds` & The "Binary Condition" Question

### Why does `ColumnBounds` exist?
When a query executes, the WHERE clause AST arrives as a `ConditionList` containing individual `UnaryCondition` nodes.
`ColumnBounds` inspects those conditions for a single target column and summarizes them into:
- `equalityKey`: Set when a `=` predicate exists.
- `lowerKey`: The tightest lower bound (`>` or `>=`).
- `upperKey`: The tightest upper bound (`<` or `<=`).

```java
final class ColumnBounds {
    final String equalityKey;
    final String lowerKey;
    final String upperKey;
}
```

---

### Why is it called `UnaryCondition` in the AST?
In compiler terminology, an expression like `age > 18` has two operands (`age` and `18`) and an operator (`>`), which is technically a binary operation.

However, in this codebase, the author named it `UnaryCondition` because it represents:
> **"A condition on ONE single table column against a literal value."**  
*(This distinguishes it from join conditions like `orders.cust_id = customer.id`, which involve two columns).*

---

### Why couldn't this be replaced by a "Binary Condition" or Range node in the Parser?

It is tempting to think: *"Why didn't the parser just parse `age >= 18 AND age <= 25` into a single RangeCondition or BinaryCondition node?"*

There are three key reasons why the AST parser cannot do this on its own:

#### 1. SQL Conditions Can Be Scattered Anywhere
Users do not write SQL with all conditions on the same column grouped together. A query can look like:
```sql
SELECT * FROM students 
WHERE age >= 18 
  AND department = 'CS' 
  AND gpa > 3.5 
  AND age <= 25;
```
Notice that `age >= 18` and `age <= 25` are separated by other columns (`department` and `gpa`).

#### 2. The Parser Reads Sequentially (Left-to-Right)
The parser operates as a stream tokenizer:
- When it reads `age >= 18`, it cannot look ahead into the future to see that 6 words later the user will specify another condition on `age`.
- Expecting the parser to stitch these together would mix **AST parsing** (syntax analysis) with **semantic query analysis** (semantic aggregation).

#### 3. Handling Redundant or Conflicting Bounds
A user or query generator might produce multiple conditions on the same column:
```sql
WHERE age > 18 AND age > 21 AND age < 30
```
Someone needs to calculate the tightest lower bound (`max(18, 21) = 21`). That calculation is a query-planning task, not a grammar-parsing task.

### Summary of `ColumnBounds` Purpose:
> **`ColumnBounds` acts as an accumulator / bucket.** It scans through all independent, scattered conditions in the WHERE clause, filters for **one column**, and calculates its consolidated `[min, max]` interval or `equality` value so the index engine can work with clean bounds.

---

## 3. Deep Dive: `IndexDefinition`

```java
final class IndexDefinition {
    final String indexName;
    final List<String> columnNames;
    final boolean primaryKey;
}
```

- **What it does**: Stores the metadata of an index (its name, the ordered list of columns it covers, and whether it represents the table's primary key).
- **The Design Benefit**: In database storage, the primary key index (`primary_key_index/`) and secondary indexes (`indexes/<name>/`) live in different subdirectories on disk. However, for query optimization, the query planner does not want separate `if (isPrimaryKey)` branches everywhere.
- By wrapping the primary key as `IndexDefinition.primary(columns)` alongside user-created indexes, the planner can iterate through `candidateIndexes()` uniformly.

---

## 4. Deep Dive: `IndexedLookup` (Purpose & Benefits)

```java
final class IndexedLookup {
    final String equalityKey;
    final String lowerKey;
    final String upperKey;
    final int score;
}
```

### Benefit 1: Translating SQL Concepts into B+ Tree String Keys
- **SQL Domain**: Thinks in columns, data types, and logical operators (`WHERE dept = 'CS' AND sem >= 3`).
- **B+ Tree Domain**: In this engine, `BPlusTreeDiskStore<String, RowPointer>` is completely unaware of SQL. It only knows two primitive string methods:
  - `search(String key)`
  - `searchRange(String lowerKey, String upperKey)`

`IndexedLookup` acts as the **bridge/translator**:
1. Numbers are padded to 10 digits (`18` $\to$ `"0000000018"`) so alphabetical sorting matches numerical sorting.
2. Composite columns are concatenated using the null byte `\u0000` as a delimiter:
   $$\text{"CS"} + \verb|\u0000| + \text{"0000000003"} + \verb|\u0000|$$
3. Range boundaries are terminated with `\uFFFF` (`Character.MAX_VALUE`) to sweep all records under a prefix:
   - `lowerKey`: `"CS\u00000000000003\u0000"`
   - `upperKey`: `"CS\u0000" + \uFFFF`

`IndexedLookup` holds these final, concrete string boundaries ready for disk execution.

---

### Benefit 2: The Optimizer "Search Proposal" & Why `score` Lives Here
Imagine a table has multiple indexes:
- **Index 1**: `(department)`
- **Index 2**: `(department, semester)`
- **Index 3**: `(age)`

And the query is:
```sql
WHERE department = 'CS' AND semester = 4 AND age > 20
```

The database evaluates each index:
- It creates an `IndexedLookup` for Index 1 $\implies$ uses `dept = 'CS'` $\implies$ **Score = 2**
- It creates an `IndexedLookup` for Index 2 $\implies$ uses `dept = 'CS'` AND `semester = 4` $\implies$ **Score = 5**
- It creates an `IndexedLookup` for Index 3 $\implies$ uses `age > 20` $\implies$ **Score = 1**

The engine compares the scores on paper, chooses Index 2, and discards the others **before loading any data pages from disk**.

> **Why `score` belongs in `IndexedLookup`, NOT in `ColumnBounds`:**  
> A column in isolation (e.g. `age > 20`) has no score. The score measures **how much of a complete INDEX matches the query**. A composite index covering 2 columns is better than an index covering 1 column. Because the score measures the quality of the index match, it must live on the index lookup proposal.

---

## 5. Deep Dive: `IndexedAccess`

```java
final class IndexedAccess {
    final IndexDefinition index;
    final IndexedLookup lookup;
}
```

- **What it does**: A simple container that pairs the winning `IndexDefinition` with its winning `IndexedLookup`.
- **Purpose**: Decouples the **planning phase** from the **execution phase**:
  1. **Planning Phase** (`Table.hasUsableIndexForWhere`): Returns `true` if `bestIndexedAccess(...) != null`, allowing `SelectStatement` to choose between a table scan vs. an index scan without reading rows.
  2. **Execution Phase** (`Table.indexedRecordsFor`): Retrieves `access.index` to open the right B+ Tree file, and passes `access.lookup` keys to retrieve matching `RowPointer`s.

---

## 6. Engineering Critique & Modern Simplification

While the separation of responsibilities is logically sound, having **4 separate top-level files** for helper logic that is only used inside `Table.java` is over-engineered.

### Recommended Clean Refactoring:
1. **Keep `IndexDefinition`**: It represents table metadata and belongs in `STRUCTURE`.
2. **Delete `IndexedAccess.java`**: Put `final IndexDefinition index;` directly inside `IndexedLookup`.
3. **Move `ColumnBounds` and `IndexedLookup` inside `Table.java`**:
   Declare them as `private static class` (or Java `record`s) inside `Table.java`. This removes file clutter from BlueJ/IDE project views while preserving clean logic.
