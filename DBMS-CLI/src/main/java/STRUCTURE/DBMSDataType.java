package STRUCTURE;

public interface DBMSDataType
{

    /*static methods cannot be overridden
    static DBMSDataType convtoDB_DT(String str)
    {
        //return MyInt.convtoDB_DT(str);
    }*/

    String toString();
    // for printing values

    public boolean equals(DBMSDataType other);

    public boolean typeEquals(DBMSDataType other);

    public boolean typeEquals(String columnType);

    public String getType();

    /*
    int dataTypeCode();
    // Returns a unique code for each data type
    boolean valueEquals(DBMSDataType other);
    // Compares values appropriately
    boolean fullyEquals(DBMSDataType other);
    // Compares both type and value
    */

}
