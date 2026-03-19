# RowSerializer

## Responsibility

`RowSerializer` converts between:

```text
Record <-> byte[]
```

This is the bridge between logical DBMS objects and physical page storage.

## Why It Needs Table Schema
The row format does not store column names. It depends on the table schema order to know:
- which column is at index `i`
- which type to encode or decode

## Current Encoding
- `INT` -> 4 bytes
- `STRING` -> fixed 31 bytes
  - 1 byte length
  - up to 15 UTF-16 chars

## Main Operations
- `serialize(Record record, Table table)`
- `deserialize(byte[] rowBytes, Table table)`

## Row Shape
Current row bytes include:
- deleted flag
- column count
- offsets
- reserved bytes
- encoded column data

## Why It Exists
Persistence should not leak byte-level concerns into `Table`, `SelectStatement`, or AST classes. `RowSerializer` centralizes that logic.

## Limitations
- no null support
- fixed string length
- deleted flag not yet used during scans
