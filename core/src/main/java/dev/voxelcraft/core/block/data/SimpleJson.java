package dev.voxelcraft.core.block.data;

// 中文标注：本文件已标记。

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {
    private SimpleJson() {
    }

    public static Object parse(String source) {
        Parser parser = new Parser(source == null ? "" : source);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isEnd()) {
            throw new IllegalArgumentException("Unexpected trailing token at index " + parser.index);
        }
        return value;
    }

    public static Map<String, Object> parseObject(String source) {
        Object parsed = parse(source);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object key must be a string");
            }
            out.put(key, entry.getValue());
        }
        return out;
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private Object parseValue() {
            skipWhitespace();
            if (isEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char current = source.charAt(index);
            return switch (current) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> object = new LinkedHashMap<>();
            if (peek('}')) {
                expect('}');
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> list = new ArrayList<>();
            if (peek(']')) {
                expect(']');
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!isEnd()) {
                char current = source.charAt(index++);
                if (current == '"') {
                    return out.toString();
                }
                if (current == '\\') {
                    if (isEnd()) {
                        throw new IllegalArgumentException("Invalid escape at end of input");
                    }
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> out.append(parseUnicodeEscape());
                        default -> throw new IllegalArgumentException("Unsupported escape sequence: \\" + escaped);
                    }
                } else {
                    out.append(current);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string literal");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > source.length()) {
                throw new IllegalArgumentException("Invalid unicode escape");
            }
            String hex = source.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid unicode escape: \\u" + hex, exception);
            }
        }

        private Boolean parseBoolean() {
            if (source.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (source.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean token at index " + index);
        }

        private Object parseNull() {
            if (!source.startsWith("null", index)) {
                throw new IllegalArgumentException("Invalid token at index " + index);
            }
            index += 4;
            return null;
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            consumeDigits();
            boolean fractional = false;
            if (peek('.')) {
                fractional = true;
                index++;
                consumeDigits();
            }
            if (peek('e') || peek('E')) {
                fractional = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                consumeDigits();
            }

            String raw = source.substring(start, index);
            if (raw.isEmpty() || raw.equals("-")) {
                throw new IllegalArgumentException("Invalid number token at index " + start);
            }
            try {
                if (!fractional) {
                    long value = Long.parseLong(raw);
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                        return (int) value;
                    }
                    return value;
                }
                return Double.parseDouble(raw);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid number token: " + raw, exception);
            }
        }

        private void consumeDigits() {
            int start = index;
            while (!isEnd() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected digit at index " + index);
            }
        }

        private void skipWhitespace() {
            while (!isEnd()) {
                char current = source.charAt(index);
                if (current == ' ' || current == '\t' || current == '\r' || current == '\n') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private void expect(char expected) {
            if (isEnd() || source.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + index);
            }
            index++;
        }

        private boolean peek(char expected) {
            return !isEnd() && source.charAt(index) == expected;
        }

        private boolean isEnd() {
            return index >= source.length();
        }
    }
}
