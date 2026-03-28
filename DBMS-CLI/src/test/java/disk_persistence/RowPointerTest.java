package disk_persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RowPointerTest {

    @Test
    void exposesPageIdAndRowOffset() {
        RowPointer pointer = new RowPointer(4, 1024);

        assertEquals(4, pointer.getPageId());
        assertEquals(1024, pointer.getRowOffset());
    }
}
