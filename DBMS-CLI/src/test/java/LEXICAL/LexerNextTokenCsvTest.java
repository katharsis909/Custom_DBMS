package LEXICAL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LexerNextTokenCsvTest {

    @ParameterizedTest(name = "{index}. {0}")
    @MethodSource("csvCases")
    void shouldTokenizeInputAsExpected(
            String caseName,
            String input,
            List<String> expectedTypes,
            List<String> expectedLexemes,
            String expectedExceptionMessage,
            String notes) {

        // These lists store the full output stream produced by repeated
        // nextToken() calls for a single input string.
        List<String> actualTypes = new ArrayList<>();
        List<String> actualLexemes = new ArrayList<>();
        Lexer lexer = new Lexer(input);

        LexerException thrown = null;

        try {
            Token token;
            do {
                // The lexer keeps its own internal position, so calling
                // nextToken() again naturally moves us to the next token.
                token = lexer.nextToken();
                actualTypes.add(token.getType().name());
                actualLexemes.add(token.getLexeme());
            } while (token.getType() != TokenType.EOF);
        } catch (LexerException ex) {
            // Some inputs are expected to fail immediately, such as
            // unsupported standalone characters like '@'.
            thrown = ex;
        }

        assertEquals(expectedTypes, actualTypes, caseName + " token types mismatch. " + notes);
        assertEquals(expectedLexemes, actualLexemes, caseName + " lexemes mismatch. " + notes);

        if (expectedExceptionMessage.isBlank()) {
            if (thrown != null) {
                fail(caseName + " unexpectedly threw LexerException: " + thrown.getMessage());
            }
        } else {
            if (thrown == null) {
                fail(caseName + " should have thrown LexerException. " + notes);
            }
            assertEquals(expectedExceptionMessage, thrown.getMessage(), caseName + " exception message mismatch.");
        }
    }

    static Stream<Arguments> csvCases() {
        List<Arguments> cases = new ArrayList<>();

        try (InputStream inputStream = LexerNextTokenCsvTest.class.getResourceAsStream("/lexer-next-token-cases.csv")) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find test resource: lexer-next-token-cases.csv");
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
                    if (columns.size() != 6) {
                        throw new IllegalStateException("Expected 6 columns but found " + columns.size() + " in line: " + line);
                    }

                    String caseName = columns.get(0);
                    String input = decodeEscapes(columns.get(1));
                    List<String> expectedTypes = splitPipeSeparated(columns.get(2));
                    List<String> expectedLexemes = splitPipeSeparated(decodeEscapes(columns.get(3)));
                    String expectedExceptionMessage = decodeEscapes(columns.get(4));
                    String notes = columns.get(5);

                    // Each CSV row becomes one parameterized JUnit test case.
                    cases.add(Arguments.of(caseName, input, expectedTypes, expectedLexemes, expectedExceptionMessage, notes));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load lexer test cases", ex);
        }

        return cases.stream();
    }

    private static List<String> parseCsvLine(String line) {
        // Small CSV parser so we can safely keep commas inside quoted fields.
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

    private static List<String> splitPipeSeparated(String value) {
        if (value.isEmpty()) {
            return new ArrayList<>();
        }

        // We use '|' inside the CSV cell to represent a sequence of expected
        // token values for one input string.
        String[] parts = value.split("\\|", -1);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add("<EMPTY>".equals(part) ? "" : part);
        }
        return result;
    }

    private static String decodeEscapes(String value) {
        // The CSV stores visible escape sequences like \n and \t, so we
        // convert them back before handing the text to the lexer.
        return value
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r");
    }
}
