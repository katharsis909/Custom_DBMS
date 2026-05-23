package SEMANTIC.AST_NODES;

import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.Table;

import java.util.ArrayList;
import java.util.List;

public class SelectedColumnList {
    private List<ColumnMention> columns;
    private List<AggregateFunction> aggregateFunctions = new ArrayList<>();
    private boolean isSelectAll; // true if "SELECT *"

    public List<ColumnMention> getColumns() {

        return columns;
    }

    public void setColumns(List<ColumnMention> columns) {
        this.columns = columns;
    }

    public List<AggregateFunction> getAggregateFunctions() {
        return aggregateFunctions;
    }

    public void setAggregateFunctions(List<AggregateFunction> aggregateFunctions) {
        this.aggregateFunctions = aggregateFunctions;
    }

    public boolean hasAggregates() {
        return aggregateFunctions != null && !aggregateFunctions.isEmpty();
    }

    public boolean isSelectAll() {
        return isSelectAll;
    }

    public void setSelectAll(boolean selectAll) {
        isSelectAll = selectAll;
    }

    public List<DBMSDataType> evaluate(Record record, Table table) throws DBMSException {
        List<DBMSDataType> values = new ArrayList<>();
        //to be returned

        if (isSelectAll())
        //or if(this.columns == null)
        {
            values = table.getValueFromRecord(record);
            //chose to define this method in table than record
        } else {
            for (ColumnMention col : getColumns())
            //for all the columns to be selected
            {
                values.add(col.evaluate(record));
                //fetch the values of those columns from the present record
                //names of the columns are present in "AST_NODES-ColumnMention" themselves,
                //names were stored during parsing
            }
        }
        return values;
    }
}
