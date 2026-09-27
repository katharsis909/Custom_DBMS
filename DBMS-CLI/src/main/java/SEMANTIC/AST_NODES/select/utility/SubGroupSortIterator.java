package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.OrderByItem;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class SubGroupSortIterator implements Iterator<Record> {
    private final Iterator<Record> sourceIterator;
    private final List<OrderByItem> orderByItems;
    private final int prefixLength;
    private final RecordSortEngine sortEngine;

    private final List<Record> currentSubGroupBuffer = new ArrayList<>();
    private Iterator<Record> sortedSubGroupIterator = null;
    private Record pendingRecordFromNextGroup = null;
    private boolean finishedSource = false;

    public SubGroupSortIterator(
            Iterator<Record> sourceIterator,
            List<OrderByItem> orderByItems,
            int prefixLength,
            RecordSortEngine sortEngine
    ) {
        this.sourceIterator = sourceIterator;
        this.orderByItems = orderByItems;
        this.prefixLength = Math.min(prefixLength, orderByItems.size());
        this.sortEngine = sortEngine;
    }

    @Override
    public boolean hasNext() {
        if (sortedSubGroupIterator != null && sortedSubGroupIterator.hasNext()) {
            return true;
        }

        if (finishedSource && pendingRecordFromNextGroup == null) {
            return false;
        }

        prepareNextSubGroup();
        return sortedSubGroupIterator != null && sortedSubGroupIterator.hasNext();
    }

    @Override
    public Record next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return sortedSubGroupIterator.next();
    }

    private void prepareNextSubGroup() {
        currentSubGroupBuffer.clear();

        if (pendingRecordFromNextGroup != null) {
            currentSubGroupBuffer.add(pendingRecordFromNextGroup);
            pendingRecordFromNextGroup = null;
        }

        while (sourceIterator != null && sourceIterator.hasNext()) {
            Record rec = sourceIterator.next();
            if (currentSubGroupBuffer.isEmpty()) {
                currentSubGroupBuffer.add(rec);
            } else {
                Record anchor = currentSubGroupBuffer.get(0);
                if (matchesPrefixKey(anchor, rec)) {
                    currentSubGroupBuffer.add(rec);
                } else {
                    pendingRecordFromNextGroup = rec;
                    break;
                }
            }
        }

        if (sourceIterator != null && !sourceIterator.hasNext()) {
            finishedSource = true;
        }

        if (!currentSubGroupBuffer.isEmpty()) {
            if (prefixLength < orderByItems.size()) {
                List<OrderByItem> trailingItems = orderByItems.subList(prefixLength, orderByItems.size());
                sortEngine.sortRecordsIfNeeded(currentSubGroupBuffer, trailingItems);
            }
            sortedSubGroupIterator = new ArrayList<>(currentSubGroupBuffer).iterator();
        } else {
            sortedSubGroupIterator = null;
        }
    }

    private boolean matchesPrefixKey(Record left, Record right) {
        for (int i = 0; i < prefixLength; i++) {
            try {
                String leftVal = orderByItems.get(i).getColumn().evaluate(left).toString();
                String rightVal = orderByItems.get(i).getColumn().evaluate(right).toString();
                if (!leftVal.equals(rightVal)) {
                    return false;
                }
            } catch (DBMSException e) {
                return false;
            }
        }
        return true;
    }
}
