package indexing.bplustree;

public interface BPlusTreeValueSerializer<T> {
    byte[] serialize(T value);

    T deserialize(byte[] bytes);
}
