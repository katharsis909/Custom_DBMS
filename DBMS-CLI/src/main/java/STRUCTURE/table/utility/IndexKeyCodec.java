package STRUCTURE.table.utility;

import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.util.List;

public class IndexKeyCodec {
    public static final char INDEX_COMPONENT_SEPARATOR = '\u0000';

    public static String indexKey(Record record, List<String> columnNames) throws DBMSException {
        if (columnNames.size() == 1) {
            return indexKey(record.getValue(columnNames.get(0)));
        }

        StringBuilder key = new StringBuilder();
        for (String columnName : columnNames) {
            key.append(encodeIndexComponent(indexKey(record.getValue(columnName))));
        }
        return key.toString();
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
