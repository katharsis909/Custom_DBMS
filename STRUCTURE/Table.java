package STRUCTURE;

import java.util.ArrayList;
import java.util.List;

public class Table {
    private List<Column> columnList;
    private List<Record> recordList;
    private String table_name;

    public List<Column> getColumnList() {
        return columnList;
    }

    public void setColumnList(List<Column> columnList) {
        this.columnList = columnList;
    }

    public List<Record> getRecordList() {
        return recordList;
    }

    public void setRecordList(List<Record> recordList) {
        this.recordList = recordList;
    }

    public String getTable_name() {
        return table_name;
    }

    public void setTable_name(String table_name) {
        this.table_name = table_name;
    }

    public void addRecord(List<DBMSDataType> values) throws DBMSException {
        if (values.size() != columnList.size()) {
            throw new DBMSException("Column count mismatch. Expected " + columnList.size() + " but got " + values.size());
        }

        for (int i = 0; i < values.size(); i++) {
            String expectedType = columnList.get(i).getColumn_type();
            DBMSDataType value = values.get(i);

            if (!value.typeEquals(expectedType)) {
                throw new DBMSException("Type mismatch at column " + columnList.get(i).getColumnName()
                        + ". Expected " + expectedType + " but got " + value.getType());
            }
        }
        Record newRecord = new Record();
        for (int i = 0; i < values.size(); i++) {
            String columnName = columnList.get(i).getColumnName();
            DBMSDataType value = values.get(i);
            newRecord.setValue(columnName,value);
            //First doing exception handling above; thenn doing changes to commit!
        }
        recordList.add(newRecord);
    }

    public List<DBMSDataType> getValueFromRecord(Record r) throws DBMSException
    //chose to define this method in table than record
    {
        List<DBMSDataType> ans = new ArrayList<DBMSDataType>();
        for (int i = 0; i < columnList.size(); i++) {
            String columnName = columnList.get(i).getColumnName();
            ans.add(r.getValue(columnName));
        }
        return ans;
    }

    public void printColumns()
    {
        for (int i = 0; i < columnList.size(); i++) {
            Column column = columnList.get(i);
            System.out.print(column.getColumnName());
            if(i != columnList.size() - 1)
                System.out.print(", ");
        }
        System.out.println();
    }

}
