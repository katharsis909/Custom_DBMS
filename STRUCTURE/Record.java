package STRUCTURE;

import java.util.HashMap;

public class Record
//Record is a single tuple,
//formed as a HashMap
//which maps column names to the values
//It is used in Table
{
    private HashMap<String, DBMSDataType> data;

    Record()
    {
        this.data = new HashMap<>();
    }

    boolean containsColumn(String colName)
    {
        return data.containsKey(colName);
    }
    //I am sure It would come into use somewhere

    void setValue(String columnName, DBMSDataType value)
    {
        data.put(columnName, value);
    }

    public DBMSDataType getValue(String columnName) throws DBMSException
    {
        if (!data.containsKey(columnName))
        {
            throw new DBMSException("Column '" + columnName + "' not found in Table.");
        }
        return data.get(columnName);
    }

    public HashMap<String, DBMSDataType> getAllValues()
    //modify a bit?
    {
        return new HashMap<>(data);
        // Defensive copy (optional, but useful for immutability)
    }
}