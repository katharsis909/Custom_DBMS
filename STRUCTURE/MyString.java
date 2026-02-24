package STRUCTURE;

public class MyString implements DBMSDataType
{
    private final String value;

    private MyString(String value) {
        this.value = value;
    }

    public static DBMSDataType convtoDB_DT(String str) {
        return new MyString(str);
    }

    public String getValue() 
    {
        return value;
    }

    public String toString() 
    {
        return value;
    }

    @Override
    public boolean equals(DBMSDataType other) {
        //return this.dataTypeCode() == other.dataTypeCode() && this.valueEquals(other);
        return (other instanceof MyString) 
        && this.value.equals( ((MyString)(other)).value );//a - Such a thing is possible Wow!
    }

    @Override
    public boolean typeEquals(DBMSDataType other) {
        return other instanceof MyString;
    }

    @Override
    public boolean typeEquals(String columnType) {
        return columnType.equals("STRING");
    }

    @Override
    public String getType() {
        return "STRING";
    }
}
