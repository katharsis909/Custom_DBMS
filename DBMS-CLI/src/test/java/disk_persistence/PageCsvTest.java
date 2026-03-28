package disk_persistence;

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageCsvTest {

    @ParameterizedTest(name = "{index}. {0}")
    @MethodSource("csvCases")
    void shouldHandlePageScenario(
            String caseName,
            String operation,
            int pageId,
            String rowPayloads,
            String slotIndex,
            String probeSize,
            String loadLength,
            String expectedPageId,
            String expectedRowCount,
            String expectedHasSpace,
            String expectedRowText,
            String expectedExceptionType,
            String expectedExceptionMessage,
            String notes) {

        Page page = new Page(pageId);
        RuntimeException runtimeException = null;

        try {
            switch (operation) {
                case "init":
                    break;
                case "insert_and_read":
                    insertPayloads(page, rowPayloads);
                    byte[] rowBytes = page.getRow(Integer.parseInt(slotIndex));
                    assertArrayEquals(expectedRowText.getBytes(StandardCharsets.UTF_8), rowBytes, caseName + " row contents mismatch. " + notes);
                    break;
                case "invalid_slot":
                    insertPayloads(page, rowPayloads);
                    runtimeException = assertThrows(
                            IllegalArgumentException.class,
                            () -> page.getRow(Integer.parseInt(slotIndex)),
                            caseName + " should reject invalid slot access. " + notes
                    );
                    break;
                case "load_wrong_size":
                    runtimeException = assertThrows(
                            IllegalArgumentException.class,
                            () -> page.loadData(new byte[Integer.parseInt(loadLength)]),
                            caseName + " should reject invalid page images. " + notes
                    );
                    break;
                case "full_page_probe":
                    page.insertRow(new byte[4080]);
                    break;
                case "insert_too_large":
                    runtimeException = assertThrows(
                            IllegalStateException.class,
                            () -> page.insertRow(new byte[Integer.parseInt(probeSize)]),
                            caseName + " should reject oversized rows. " + notes
                    );
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
        } finally {
            assertEquals(Integer.parseInt(expectedPageId), page.getPageId(), caseName + " page id mismatch. " + notes);
            assertEquals(Integer.parseInt(expectedRowCount), page.getRowCount(), caseName + " row count mismatch. " + notes);

            if (!expectedHasSpace.isBlank()) {
                assertEquals(
                        Boolean.parseBoolean(expectedHasSpace),
                        page.hasSpace(Integer.parseInt(probeSize)),
                        caseName + " hasSpace mismatch. " + notes
                );
            }

            if (!expectedExceptionType.isBlank()) {
                assertEquals(expectedExceptionType, runtimeException.getClass().getSimpleName(), caseName + " exception type mismatch. " + notes);
                assertEquals(expectedExceptionMessage, runtimeException.getMessage(), caseName + " exception message mismatch. " + notes);
            }
        }
    }

    static Stream<Arguments> csvCases() {
        List<Arguments> cases = new ArrayList<>();

        try (InputStream inputStream = PageCsvTest.class.getResourceAsStream("/page-cases.csv")) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find test resource: page-cases.csv");
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
                    if (columns.size() != 14) {
                        throw new IllegalStateException("Expected 14 columns but found " + columns.size() + " in line: " + line);
                    }

                    cases.add(Arguments.of(
                            columns.get(0),
                            columns.get(1),
                            Integer.parseInt(columns.get(2)),
                            decodeEscapes(columns.get(3)),
                            columns.get(4),
                            columns.get(5),
                            columns.get(6),
                            columns.get(7),
                            columns.get(8),
                            columns.get(9),
                            decodeEscapes(columns.get(10)),
                            columns.get(11),
                            decodeEscapes(columns.get(12)),
                            columns.get(13)
                    ));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load page test cases", ex);
        }

        return cases.stream();
    }

    private static void insertPayloads(Page page, String rowPayloads) {
        for (String payload : splitPipeSeparated(rowPayloads)) {
            page.insertRow(payload.getBytes(StandardCharsets.UTF_8));
        }
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

    private static String decodeEscapes(String value) {
        return value
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r");
    }
}
