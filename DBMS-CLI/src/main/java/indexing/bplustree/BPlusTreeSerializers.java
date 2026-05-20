package indexing.bplustree;

import disk_persistence.RowPointer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class BPlusTreeSerializers {
    private BPlusTreeSerializers() {
    }

    public static BPlusTreeValueSerializer<Integer> integers() {
        return new BPlusTreeValueSerializer<>() {
            @Override
            public byte[] serialize(Integer value) {
                return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
            }

            @Override
            public Integer deserialize(byte[] bytes) {
                return ByteBuffer.wrap(bytes).getInt();
            }
        };
    }

    public static BPlusTreeValueSerializer<String> strings() {
        return new BPlusTreeValueSerializer<>() {
            @Override
            public byte[] serialize(String value) {
                return value.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String deserialize(byte[] bytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
        };
    }

    public static BPlusTreeValueSerializer<RowPointer> rowPointers() {
        return new BPlusTreeValueSerializer<>() {
            @Override
            public byte[] serialize(RowPointer value) {
                return ByteBuffer.allocate(Integer.BYTES * 2)
                        .putInt(value.getPageId())
                        .putInt(value.getRowOffset())
                        .array();
            }

            @Override
            public RowPointer deserialize(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                return new RowPointer(buffer.getInt(), buffer.getInt());
            }
        };
    }
}
