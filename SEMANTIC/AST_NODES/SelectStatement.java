package SEMANTIC.AST_NODES;

import SEMANTIC.AST_NODES.LEAF_NODES.Identifier;
import STRUCTURE.Catalog;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Table;
import STRUCTURE.Record;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement extends Statement {
    private SelectedColumnList selectedColumnList;
    private Identifier tableName;
    private WhereClause whereClause; // can be null

    public SelectedColumnList getSelectedColumnList() {
        return selectedColumnList;
    }

    public void setSelectedColumnList(SelectedColumnList selectedColumnList) {
        this.selectedColumnList = selectedColumnList;
    }

    public Identifier getTableName() {
        return tableName;
    }

    public void setTableName(Identifier tableName) {
        this.tableName = tableName;
    }

    public WhereClause getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(WhereClause whereClause) {
        this.whereClause = whereClause;
    }

    public void evaluate(Catalog db) throws DBMSException {
        Table table = db.getTable(getTableName().getName());
        //FROM is executed before
        List<Record> allRecords = table.getRecordList();

        List<Record> filtered = new ArrayList<>();
        //validated records to be returned
        for (Record r : allRecords)
        {
            if (getWhereClause() == null || getWhereClause().evaluate(r))
            //First use Where; above also checked for "Where not present"
            {
                filtered.add(r);
            }
        }

        List<List<DBMSDataType>> result = new ArrayList<>();
//      Better make List<DBMSDataType> another object
        for (Record r : filtered) {
            //Second, from the validated records, now select specific columns
            //No need to pass information of which columns to select, all info in the below nodes
            //Crazy design pattern!
            result.add(selectedColumnList.evaluate(r,table));
        }
        //System.out.println();

        printResult(result, getSelectedColumnList(), table);

        //return result;
    }

    private void printResult(List<List<DBMSDataType>> rows, SelectedColumnList selectedColumnList, Table table) {
        System.out.print("Columns: ");
        if(selectedColumnList.getColumns() == null)
        {
            table.printColumns();
        }
        else {
            for (int i = 0; i < selectedColumnList.getColumns().size(); i++) {
                System.out.print(selectedColumnList.getColumns().get(i).getColumnName().getName());
                if (i < selectedColumnList.getColumns().size() - 1) System.out.print(", ");
            }
            System.out.println();
        }

        for (List<DBMSDataType> row : rows) {
            System.out.print("Record: ");
            for (int i = 0; i < row.size(); i++) {
                System.out.print(row.get(i));
                if (i < row.size() - 1) System.out.print(", ");
            }
            System.out.println();
        }
    }

}
