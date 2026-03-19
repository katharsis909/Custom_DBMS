package disk_persistence;

import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.MyInt;
import STRUCTURE.MyString;
import STRUCTURE.Record;
import STRUCTURE.Table;

import java.util.ArrayList;
import java.util.List;

public class RowSerializer {
    private static final int MAX_STRING_LENGTH = 15;
    private static final int FIXED_STRING_BYTES = 31;
    private static final int DELETED_FLAG_SIZE = 1;
    private static final int COLUMN_COUNT_SIZE = 1;
    private static final int RESERVED_SIZE = 4;

    public static byte[] serialize(Record record, Table table) throws DBMSException {
        List<Column> columns = table.getColumnList();
        int columnCount = columns.size();

        List<byte[]> columnBytes = new ArrayList<>();
        int totalDataSize = 0;

        for (Column column : columns) {
            DBMSDataType value = record.getValue(column.getColumnName());
            byte[] encodedValue;
            if ("INT".equals(column.getColumn_type())) {
                encodedValue = intToBytes(((MyInt) value).getValue());
            } else if ("STRING".equals(column.getColumn_type())) {
                encodedValue = stringToBytes(((MyString) value).getValue());
            } else {
                throw new DBMSException("Unsupported column type: " + column.getColumn_type());
            }
            columnBytes.add(encodedValue);
            totalDataSize += encodedValue.length;
        }

        int offsetsSize = columnCount * 2;
        int headerSize = DELETED_FLAG_SIZE + COLUMN_COUNT_SIZE + offsetsSize + RESERVED_SIZE;
        byte[] row = new byte[headerSize + totalDataSize];

        int pos = 0;
        row[pos++] = 0;
        row[pos++] = (byte) columnCount;
        int offsetStart = pos;
        pos += offsetsSize;
        pos += RESERVED_SIZE;

        int dataPos = headerSize;
        for (int i = 0; i < columnCount; i++) {
            writeShort(row, offsetStart + (i * 2), dataPos);
            byte[] bytes = columnBytes.get(i);
            System.arraycopy(bytes, 0, row, dataPos, bytes.length);
            dataPos += bytes.length;
        }

        return row;
    }

    public static Record deserialize(byte[] rowBytes, Table table) throws DBMSException {
        int columnCount = rowBytes[1] & 0xFF;
        List<Column> columns = table.getColumnList();
        if (columnCount != columns.size()) {
            throw new DBMSException("Row column count " + columnCount + " does not match table schema size " + columns.size());
        }

        Record record = new Record();
        int offsetStart = 2;

        for (int i = 0; i < columnCount; i++) {
            Column column = columns.get(i);
            int currentOffset = readShort(rowBytes, offsetStart + (i * 2));
            int nextOffset = i == columnCount - 1 ? rowBytes.length : readShort(rowBytes, offsetStart + ((i + 1) * 2));

            DBMSDataType value;
            if ("INT".equals(column.getColumn_type())) {
                value = MyInt.convtoDB_DT(Integer.toString(bytesToInt(rowBytes, currentOffset)));
            } else if ("STRING".equals(column.getColumn_type())) {
                value = MyString.convtoDB_DT(bytesToString(rowBytes, currentOffset));
            } else {
                throw new DBMSException("Unsupported column type: " + column.getColumn_type());
            }
            record.setValue(column.getColumnName(), value);
        }

        return record;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    private static int bytesToInt(byte[] arr, int pos) {
        return ((arr[pos] & 0xFF) << 24)
                | ((arr[pos + 1] & 0xFF) << 16)
                | ((arr[pos + 2] & 0xFF) << 8)
                | (arr[pos + 3] & 0xFF);
    }

    private static byte[] stringToBytes(String value) throws DBMSException {
        byte[] result = new byte[FIXED_STRING_BYTES];
        int actualLength = Math.min(value.length(), MAX_STRING_LENGTH);
        if (value.length() > MAX_STRING_LENGTH) {
            throw new DBMSException("STRING value exceeds max supported length of " + MAX_STRING_LENGTH + " characters");
        }

        result[0] = (byte) actualLength;
        for (int i = 0; i < actualLength; i++) {
            char c = value.charAt(i);
            result[1 + (i * 2)] = (byte) (c >>> 8);
            result[2 + (i * 2)] = (byte) c;
        }
        return result;
    }

    private static String bytesToString(byte[] arr, int pos) {
        int len = arr[pos] & 0xFF;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int high = arr[pos + 1 + (i * 2)] & 0xFF;
            int low = arr[pos + 2 + (i * 2)] & 0xFF;
            builder.append((char) ((high << 8) | low));
        }
        return builder.toString();
    }

    private static void writeShort(byte[] arr, int pos, int value) {
        arr[pos] = (byte) (value >>> 8);
        arr[pos + 1] = (byte) value;
    }

    private static int readShort(byte[] arr, int pos) {
        return ((arr[pos] & 0xFF) << 8) | (arr[pos + 1] & 0xFF);
    }
}
