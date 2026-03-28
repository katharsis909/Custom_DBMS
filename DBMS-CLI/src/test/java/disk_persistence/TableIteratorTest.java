package disk_persistence;

import STRUCTURE.Record;
import STRUCTURE.Table;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TableIteratorTest {

    @Test
    void hasNextMovesAcrossPagesAndNextDeserializesCurrentRow() throws Exception {
        Table table = mock(Table.class);
        PageManager pageManager = mock(PageManager.class);
        Page firstPage = mock(Page.class);
        Page secondPage = mock(Page.class);
        Record firstRecord = new Record();
        Record secondRecord = new Record();
        byte[] firstRow = new byte[]{1};
        byte[] secondRow = new byte[]{2};

        when(pageManager.getCurrentPageId()).thenReturn(1);
        when(pageManager.loadPage(0)).thenReturn(firstPage);
        when(pageManager.loadPage(1)).thenReturn(secondPage);
        when(firstPage.getRowCount()).thenReturn(1, 1, 1);
        when(secondPage.getRowCount()).thenReturn(1, 1, 1);
        when(firstPage.getRow(0)).thenReturn(firstRow);
        when(secondPage.getRow(0)).thenReturn(secondRow);

        try (MockedStatic<RowSerializer> serializer = mockStatic(RowSerializer.class)) {
            serializer.when(() -> RowSerializer.deserialize(firstRow, table)).thenReturn(firstRecord);
            serializer.when(() -> RowSerializer.deserialize(secondRow, table)).thenReturn(secondRecord);

            TableIterator iterator = new TableIterator(table, pageManager);

            assertTrue(iterator.hasNext());
            assertSame(firstRecord, iterator.next());
            assertTrue(iterator.hasNext());
            assertSame(secondRecord, iterator.next());
            assertFalse(iterator.hasNext());

            serializer.verify(() -> RowSerializer.deserialize(firstRow, table));
            serializer.verify(() -> RowSerializer.deserialize(secondRow, table));
        }

        verify(pageManager).loadPage(0);
        verify(pageManager).loadPage(1);
    }

    @Test
    void nextThrowsWhenIteratorIsExhausted() throws Exception {
        Table table = mock(Table.class);
        PageManager pageManager = mock(PageManager.class);
        Page page = mock(Page.class);

        when(pageManager.getCurrentPageId()).thenReturn(0);
        when(pageManager.loadPage(0)).thenReturn(page);
        when(page.getRowCount()).thenReturn(0);

        TableIterator iterator = new TableIterator(table, pageManager);

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}
