package STRUCTURE;

public class MyInt implements DBMSDataType
{
    private final int value;
    //this data type will be actually stored in the Records
    private MyInt(int value) 
    {
        this.value = value;
    }

    public static DBMSDataType convtoDB_DT(String str) {
        int val = Integer.parseInt(str);
            return new MyInt(val);
    }

    @Override
    public String toString() 
    {
        return Integer.toString(value);
    }
    
    /* @Override
    public int dataTypeCode() {
        return 1;
    }

    @Override
    public boolean valueEquals(DBMSDataType other) {
        //if (other instanceof MyInt) 
        //{
            return this.value == ((MyInt)(other)).value;
        //}
        //return false;
    }*/

    @Override
    public boolean equals(DBMSDataType other) {
        //return this.dataTypeCode() == other.dataTypeCode() && this.valueEquals(other);
        return (other instanceof MyInt)
                && this.value == ((MyInt)(other)).value;//a - Such a thing is possible Wow!
    }

    @Override
    public boolean typeEquals(DBMSDataType other) {
        return other instanceof MyInt;
    }

    @Override
    public boolean typeEquals(String columnType) {
        return columnType.equals("INT");
    }

    @Override
    public String getType() {
        return "INT";
    }
}

