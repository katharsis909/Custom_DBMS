package STRUCTURE.table.utility;

import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.util.List;

public class IndexKeyCodec {
    public static final char INDEX_COMPONENT_SEPARATOR = '\u0000';

    public static String indexKey(Record record, List<String> columnNames) throws DBMSException {
        return indexKey(record, columnNames, null);
    }

    public static String indexKey(Record record, List<String> columnNames, List<Boolean> columnDirections) throws DBMSException {
        if (columnNames.size() == 1) {
            String raw = indexKey(record.getValue(columnNames.get(0)));
            boolean asc = columnDirections == null || columnDirections.isEmpty() || columnDirections.get(0);
            return asc ? raw : invert(raw);
        }

        StringBuilder key = new StringBuilder();
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            boolean asc = columnDirections == null || i >= columnDirections.size() || columnDirections.get(i);
            String raw = indexKey(record.getValue(columnName));
            key.append(encodeIndexComponent(asc ? raw : invert(raw)));
        }
        return key.toString();
    }

    public static String invert(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            sb.append((char) (0xFFFF - value.charAt(i)));
        }
        return sb.toString();
    }

    public static String indexKey(DBMSDataType value) {
        if (value.getType().equals("INT")) {
            return String.format("%010d", Integer.parseInt(value.toString()));
        }
        return value.toString();
    }

    public static String encodeIndexComponent(String value) {
        return value + INDEX_COMPONENT_SEPARATOR;
    }

    public static String joinIndexPrefix(List<String> encodedComponents) {
        StringBuilder key = new StringBuilder();
        for (String encodedComponent : encodedComponents) {
            key.append(encodedComponent);
        }
        return key.toString();
    }
}
