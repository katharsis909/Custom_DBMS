package SEMANTIC.AST_NODES.select.utility;

import SEMANTIC.AST_NODES.JoinClause;
import SEMANTIC.AST_NODES.TableBinding;
import SEMANTIC.AST_NODES.WhereClause;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.table.utility.IndexKeyCodec;
import disk_persistence.RowPointer;
import disk_persistence.TableIterator;
import indexing.bplustree.BPlusTreeDiskStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class JoinedRecordIterator implements Iterator<Record> {
    private final List<TableBinding> bindings;
    private final List<JoinClause> joins;
    private final WhereClause whereClause;
    private final RecordExecutor recordExecutor;

    private Record nextJoinedRecord;
    private boolean advanceChecked;

    public JoinedRecordIterator(
            List<TableBinding> bindings,
            List<JoinClause> joins,
            WhereClause whereClause
    ) {
        this.bindings = bindings;
        this.joins = joins;
        this.whereClause = whereClause;
        this.recordExecutor = new RecordExecutor(bindings);
        this.advanceChecked = false;
    }

    @Override
    public boolean hasNext() {
        if (!advanceChecked) {
            try {
                nextJoinedRecord = recordExecutor.nextValidJoinedRecord();
            } catch (DBMSException e) {
                throw new RuntimeException("Error advancing joined record iterator", e);
            }
            advanceChecked = true;
        }
        return nextJoinedRecord != null;
    }

    @Override
    public Record next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Record record = nextJoinedRecord;
        advanceChecked = false;
        return record;
    }

    private class RecordExecutor {
        private final List<TableBinding> bindings;
        private final Iterator<Record>[] iterators;
        private final Record[] currentRecords;
        private int currentLevel;
        private boolean initialized = false;

        @SuppressWarnings("unchecked")
        RecordExecutor(List<TableBinding> bindings) {
            this.bindings = bindings;
            this.iterators = new Iterator[bindings.size()];
            this.currentRecords = new Record[bindings.size()];
            this.currentLevel = 0;
        }

        Record nextValidJoinedRecord() throws DBMSException {
            if (!initialized) {
                if (bindings.isEmpty()) {
                    initialized = true;
                    return null;
                }
                iterators[0] = getTableIterator(0, new Record());
                initialized = true;
            }

            while (currentLevel >= 0) {
                if (iterators[currentLevel] != null && iterators[currentLevel].hasNext()) {
                    Record rec = iterators[currentLevel].next();
                    currentRecords[currentLevel] = rec;

                    Record combined = buildCombinedRecordUntilLevel(currentLevel);
                    if (!levelMatchesPredicates(currentLevel, combined)) {
                        continue;
                    }

                    if (currentLevel == bindings.size() - 1) {
                        return combined;
                    } else {
                        currentLevel++;
                        iterators[currentLevel] = getTableIterator(currentLevel, combined);
                    }
                } else {
                    iterators[currentLevel] = null;
                    currentLevel--;
                }
            }

            return null;
        }

        private Iterator<Record> getTableIterator(int level, Record outerCombined) throws DBMSException {
            TableBinding binding = bindings.get(level);
            if (level > 0 && joins != null) {
                // Try INLJ (Index-Nested-Loop Join) lookup if join condition has index on inner table
                for (JoinClause join : joins) {
                    if (join.getTableName().getName().equals(binding.tableName) || (binding.alias != null && binding.alias.equals(join.getTableName().getName()))) {
                        String leftName = join.getLeftColumn().getColumnName().getName();
                        String rightName = join.getRightColumn().getColumnName().getName();

                        String outerCol = null;
                        String innerCol = null;

                        if (outerCombined.containsColumn(leftName)) {
                            outerCol = leftName;
                            innerCol = unqualified(rightName);
                        } else if (outerCombined.containsColumn(rightName)) {
                            outerCol = rightName;
                            innerCol = unqualified(leftName);
                        }

                        if (outerCol != null && innerCol != null && binding.table.hasIndexOnColumn(innerCol)) {
                            BPlusTreeDiskStore<String, RowPointer> store = binding.table.getIndexStoreForColumn(innerCol);
                            if (store != null) {
                                DBMSDataType rawVal = outerCombined.getValue(outerCol);
                                String searchKey = IndexKeyCodec.indexKey(rawVal);
                                List<RowPointer> pointers = store.search(searchKey);
                                List<Record> matched = new ArrayList<>();
                                for (RowPointer p : pointers) {
                                    matched.add(binding.table.readRecord(p));
                                }
                                return matched.iterator();
                            }
                        }
                    }
                }
            }

            TableIterator rawIterator = binding.table.iterator();
            return new Iterator<Record>() {
                @Override
                public boolean hasNext() {
                    try {
                        return rawIterator.hasNext();
                    } catch (DBMSException e) {
                        return false;
                    }
                }

                @Override
                public Record next() {
                    try {
                        return rawIterator.next();
                    } catch (DBMSException e) {
                        throw new NoSuchElementException();
                    }
                }
            };
        }

        private Record buildCombinedRecordUntilLevel(int level) throws DBMSException {
            Record combined = new Record();
            for (int i = 0; i <= level; i++) {
                TableBinding binding = bindings.get(i);
                Record src = currentRecords[i];
                if (src != null) {
                    for (Column column : binding.table.getColumnList()) {
                        DBMSDataType value = src.getValue(column.getColumnName());
                        combined.setValue(binding.tableName + "." + column.getColumnName(), value);
                        combined.setValue(binding.alias + "." + column.getColumnName(), value);
                        combined.setValue(column.getColumnName(), value);
                    }
                }
            }
            return combined;
        }

        private boolean levelMatchesPredicates(int level, Record combined) throws DBMSException {
            if (joins != null) {
                for (JoinClause join : joins) {
                    if (isClauseEvaluatableAtLevel(join, level)) {
                        if (!join.evaluate(combined)) {
                            return false;
                        }
                    }
                }
            }
            if (level == bindings.size() - 1 && whereClause != null) {
                return whereClause.evaluate(combined);
            }
            return true;
        }

        private boolean isClauseEvaluatableAtLevel(JoinClause join, int level) {
            String leftName = join.getLeftColumn().getColumnName().getName();
            String rightName = join.getRightColumn().getColumnName().getName();

            boolean leftAvailable = false;
            boolean rightAvailable = false;

            for (int i = 0; i <= level; i++) {
                TableBinding b = bindings.get(i);
                if (leftName.startsWith(b.tableName + ".") || leftName.startsWith(b.alias + ".") || leftName.equals(b.tableName)) {
                    leftAvailable = true;
                }
                if (rightName.startsWith(b.tableName + ".") || rightName.startsWith(b.alias + ".") || rightName.equals(b.tableName)) {
                    rightAvailable = true;
                }
            }
            return leftAvailable && rightAvailable;
        }

        private String unqualified(String name) {
            int dot = name.indexOf('.');
            return dot < 0 ? name : name.substring(dot + 1);
        }
    }
}
