package SEMANTIC.PARSER.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import LEXICAL.Lexer;
import LEXICAL.LexerException;
import LEXICAL.Token;
import LEXICAL.TokenType;
import SEMANTIC.PARSER.Exception.ParseException;

class ParserContextCsvTest {

    // "{0}" is the case name from the CSV, so JUnit output stays readable.
    @ParameterizedTest(name = "{index}. {0}")
    @MethodSource("csvCases")
    void shouldHandleParserContextScenario(
            String caseName,
            String lexerSteps,
            String operation,
            String operationArg,
            String expectedCurrentType,
            String expectedCurrentLexeme,
            String expectedReturnType,
            String expectedReturnLexeme,
            String expectedParseMessage,
            String expectedParsePosition,
            String expectedLexerMessage,
            String expectedIsAtEnd,
            String notes) throws Exception {

        Lexer lexer = mock(Lexer.class);
        configureLexerMock(lexer, lexerSteps);

        if ("constructor_throws".equals(operation)) {
            LexerException ex = assertThrows(LexerException.class, () -> new ParserContext(lexer));
            assertEquals(expectedLexerMessage, ex.getMessage(), caseName + " lexer exception mismatch. " + notes);
            return;
        }

        ParserContext ctx = new ParserContext(lexer);

        Token returnedToken = null;
        ParseException parseException = null;
        LexerException lexerException = null;
        Boolean actualIsAtEnd = null;

        try {
            switch (operation) {
                case "constructor_current":
                    break;
                case "advance":
                    ctx.advance();
                    break;
                case "expect_success":
                    returnedToken = ctx.expect(TokenType.valueOf(operationArg));
                    break;
                case "expect_parse_exception":
                    parseException = assertThrows(ParseException.class, () -> ctx.expect(TokenType.valueOf(operationArg)));
                    break;
                case "expect_lexer_exception":
                    lexerException = assertThrows(LexerException.class, () -> ctx.expect(TokenType.valueOf(operationArg)));
                    break;
                case "advance_lexer_exception":
                    lexerException = assertThrows(LexerException.class, ctx::advance);
                    break;
                case "is_at_end":
                    actualIsAtEnd = ctx.isAtEnd();
                    break;
                case "error":
                    parseException = ctx.error(operationArg);
                    break;
                case "error_at":
                    parseException = ctx.errorAt(operationArg, Integer.parseInt(expectedParsePosition));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation);
            }
        } catch (ParseException ex) {
            parseException = ex;
        } catch (LexerException ex) {
            lexerException = ex;
        }

        assertCurrentToken(ctx, expectedCurrentType, expectedCurrentLexeme, caseName, notes);
        assertReturnedToken(returnedToken, expectedReturnType, expectedReturnLexeme, caseName, notes);
        assertParseException(parseException, expectedParseMessage, expectedParsePosition, caseName, notes);
        assertLexerException(lexerException, expectedLexerMessage, caseName, notes);

        if (!expectedIsAtEnd.isBlank()) {
            assertNotNull(actualIsAtEnd, caseName + " should have checked isAtEnd(). " + notes);
            assertEquals(Boolean.parseBoolean(expectedIsAtEnd), actualIsAtEnd, caseName + " isAtEnd mismatch. " + notes);
        }
    }

    static Stream<Arguments> csvCases() {
        List<Arguments> cases = new ArrayList<>();

        try (InputStream inputStream = ParserContextCsvTest.class.getResourceAsStream("/parser-context-cases.csv")) {
            if (inputStream == null) {
                throw new IllegalStateException("Could not find test resource: parser-context-cases.csv");
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
                    if (columns.size() != 13) {
                        throw new IllegalStateException("Expected 13 columns but found " + columns.size() + " in line: " + line);
                    }

                    cases.add(Arguments.of(
                            columns.get(0),
                            decodeEscapes(columns.get(1)),
                            columns.get(2),
                            decodeEscapes(columns.get(3)),
                            columns.get(4),
                            decodeEscapes(columns.get(5)),
                            columns.get(6),
                            decodeEscapes(columns.get(7)),
                            decodeEscapes(columns.get(8)),
                            columns.get(9),
                            decodeEscapes(columns.get(10)),
                            columns.get(11),
                            columns.get(12)));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load parser context test cases", ex);
        }

        return cases.stream();
    }

    private static void configureLexerMock(Lexer lexer, String lexerSteps) throws Exception {
        String[] steps = lexerSteps.split(";", -1);
        if (steps.length == 0 || lexerSteps.isBlank()) {
            throw new IllegalArgumentException("lexerSteps must contain at least one step");
        }

        org.mockito.stubbing.OngoingStubbing<Token> stubbing = null;

        for (String step : steps) {
            String[] parts = step.split("~", -1);
            String kind = parts[0];

            if ("TOKEN".equals(kind)) {
                if (parts.length != 4) {
                    throw new IllegalArgumentException("TOKEN step must have 4 parts: " + step);
                }
                Token token = new Token(
                        TokenType.valueOf(parts[1]),
                        "<EMPTY>".equals(parts[2]) ? "" : decodeEscapes(parts[2]),
                        Integer.parseInt(parts[3]));

                stubbing = stubbing == null ? when(lexer.nextToken()).thenReturn(token) : stubbing.thenReturn(token);
            } else if ("LEXER_EXCEPTION".equals(kind)) {
                if (parts.length != 2) {
                    throw new IllegalArgumentException("LEXER_EXCEPTION step must have 2 parts: " + step);
                }
                LexerException ex = new LexerException(decodeEscapes(parts[1]));
                stubbing = stubbing == null ? when(lexer.nextToken()).thenThrow(ex) : stubbing.thenThrow(ex);
            } else {
                throw new IllegalArgumentException("Unknown lexer step kind: " + kind);
            }
        }
    }

    private static void assertCurrentToken(
            ParserContext ctx,
            String expectedCurrentType,
            String expectedCurrentLexeme,
            String caseName,
            String notes) {

        if (expectedCurrentType.isBlank()) {
            return;
        }

        assertEquals(TokenType.valueOf(expectedCurrentType), ctx.current().getType(), caseName + " current token type mismatch. " + notes);
        assertEquals(expectedCurrentLexeme, ctx.current().getLexeme(), caseName + " current token lexeme mismatch. " + notes);
    }

    private static void assertReturnedToken(
            Token returnedToken,
            String expectedReturnType,
            String expectedReturnLexeme,
            String caseName,
            String notes) {

        if (expectedReturnType.isBlank()) {
            assertNull(returnedToken, caseName + " should not have returned a token. " + notes);
            return;
        }

        assertNotNull(returnedToken, caseName + " should have returned a token. " + notes);
        assertEquals(TokenType.valueOf(expectedReturnType), returnedToken.getType(), caseName + " returned token type mismatch. " + notes);
        assertEquals(expectedReturnLexeme, returnedToken.getLexeme(), caseName + " returned token lexeme mismatch. " + notes);
    }

    private static void assertParseException(
            ParseException parseException,
            String expectedParseMessage,
            String expectedParsePosition,
            String caseName,
            String notes) {

        if (expectedParseMessage.isBlank()) {
            assertNull(parseException, caseName + " unexpectedly produced ParseException. " + notes);
            return;
        }

        assertNotNull(parseException, caseName + " should have produced ParseException. " + notes);
        assertEquals(expectedParseMessage, parseException.getMessage(), caseName + " parse exception message mismatch. " + notes);

        if (!expectedParsePosition.isBlank()) {
            assertEquals(Integer.valueOf(expectedParsePosition), parseException.getPosition(), caseName + " parse exception position mismatch. " + notes);
        }
    }

    private static void assertLexerException(
            LexerException lexerException,
            String expectedLexerMessage,
            String caseName,
            String notes) {

        if (expectedLexerMessage.isBlank()) {
            assertNull(lexerException, caseName + " unexpectedly produced LexerException. " + notes);
            return;
        }

        assertNotNull(lexerException, caseName + " should have produced LexerException. " + notes);
        assertEquals(expectedLexerMessage, lexerException.getMessage(), caseName + " lexer exception message mismatch. " + notes);
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
