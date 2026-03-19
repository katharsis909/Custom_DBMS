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

    public int getValue() {
        return value;
    }

    @Override
    public String toString()
    {
        return Integer.toString(value);
    }

    @Override
    public boolean equals(DBMSDataType other) {
        return (other instanceof MyInt)
                && this.value == ((MyInt)(other)).value;
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
