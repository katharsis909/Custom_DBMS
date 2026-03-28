package disk_persistence;

import STRUCTURE.Column;
import STRUCTURE.DBMSException;
import STRUCTURE.MyInt;
import STRUCTURE.MyString;
import STRUCTURE.Record;
import STRUCTURE.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RowSerializerCsvTest {

    @ParameterizedTest(name = "{index}. {0}")
    @MethodSource("csvCases")
    void shouldHandleRowSerializerScenario(
            String caseName,
            String operation,
            String recordSchema,
            String tableSchema,
            String rowValues,
            String mutation,
            String expectedSerializedLength,
            String expectedValues,
            String expectedExceptionMessage,
            String notes) throws Exception {

        Table recordTable = mockTable(parseSchema(recordSchema));
        Table table = mockTable(parseSchema(tableSchema));
        Record record = buildRecord(rowValues);

        switch (operation) {
            case "round_trip":
                byte[] rowBytes = RowSerializer.serialize(record, recordTable);
                assertEquals(Integer.parseInt(expectedSerializedLength), rowBytes.length, caseName + " serialized length mismatch. " + notes);
                assertEquals(expectedValues, formatRecord(RowSerializer.deserialize(rowBytes, table), parseSchema(tableSchema)), caseName + " round-trip values mismatch. " + notes);
                break;
            case "serialize_error":
                DBMSException serializeException = assertThrows(
                        DBMSException.class,
                        () -> RowSerializer.serialize(record, recordTable),
                        caseName + " should fail during serialize. " + notes
                );
                assertEquals(expectedExceptionMessage, serializeException.getMessage(), caseName + " serialize exception mismatch. " + notes);
                break;
            case "deserialize_error":
                byte[] mismatchBytes = RowSerializer.serialize(record, recordTable);
                DBMSException deserializeException = assertThrows(
                        DBMSException.class,
                        () -> RowSerializer.deserialize(mismatchBytes, table),
                        caseName + " should fail during deserialize. " + notes
                );
                assertEquals(expectedExceptionMessage, deserializeException.getMessage(), caseName + " deserialize exception mismatch. " + notes);
                break;
            case "deserialize_with_mutation":
                byte[] mutatedBytes = RowSerializer.serialize(record, recordTable);
                applyMutation(mutatedBytes, mutation);
                assertEquals(expectedValues, formatRecord(RowSerializer.deserialize(mutatedBytes, table), parseSchema(tableSchema)), caseName + " mutated deserialize mismatch. " + notes);
                break;
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    static Stream<Arguments> csvCases() {
        List<Arguments> cases = new ArrayList<>();

        try (InputStream inputStream = RowSerializerCsvTest.class.getResourceAsStream("/row-serializer-cases.csv")) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find test resource: row-serializer-cases.csv");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean headerSkipped = false;

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }

                    List<String> columns = parseCsvLine(line);
                    if (columns.size() != 10) {
                        throw new IllegalStateException("Expected 10 columns but found " + columns.size() + " in line: " + line);
                    }

                    cases.add(Arguments.of(
                            columns.get(0),
                            columns.get(1),
                            columns.get(2),
                            columns.get(3),
                            columns.get(4),
                            columns.get(5),
                            columns.get(6),
                            columns.get(7),
                            columns.get(8),
                            columns.get(9)
                    ));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load row serializer test cases", ex);
        }

        return cases.stream();
    }

    private static Table mockTable(List<Column> columns) throws Exception {
        Table table = mock(Table.class);
        when(table.getColumnList()).thenReturn(columns);
        return table;
    }

    private static Record buildRecord(String rowValues) {
        Record record = new Record();
        for (String entry : splitPipeSeparated(rowValues)) {
            String[] parts = entry.split("=", 2);
            String name = parts[0];
            String value = parts[1];
            if (value.matches("-?\\d+")) {
                record.setValue(name, MyInt.convtoDB_DT(value));
            } else {
                record.setValue(name, MyString.convtoDB_DT(value));
            }
        }
        return record;
    }

    private static List<Column> parseSchema(String schema) {
        List<Column> columns = new ArrayList<>();
        for (String entry : splitPipeSeparated(schema)) {
            String[] parts = entry.split(":", 2);
            columns.add(new Column(parts[0], parts[1]));
        }
        return columns;
    }

    private static void applyMutation(byte[] bytes, String mutation) {
        if ("mark_deleted".equals(mutation)) {
            bytes[0] = 1;
            return;
        }
        if (!mutation.isBlank()) {
            throw new IllegalArgumentException("Unknown mutation: " + mutation);
        }
    }

    private static String formatRecord(Record record, List<Column> columns) throws DBMSException {
        List<String> parts = new ArrayList<>();
        for (Column column : columns) {
            parts.add(column.getColumnName() + "=" + record.getValue(column.getColumnName()));
        }
        return String.join("|", parts);
    }

    private static List<String> splitPipeSeparated(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\|", -1));
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if (currentChar == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (currentChar == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }

        values.add(current.toString());
        return values;
    }
}
