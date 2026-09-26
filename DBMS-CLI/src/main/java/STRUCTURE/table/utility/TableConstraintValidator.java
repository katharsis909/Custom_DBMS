package STRUCTURE.table.utility;

import STRUCTURE.Catalog;
import STRUCTURE.Column;
import STRUCTURE.DBMSDataType;
import STRUCTURE.DBMSException;
import STRUCTURE.Record;
import STRUCTURE.table.Table;
import disk_persistence.TableIterator;

import java.util.List;

public class TableConstraintValidator {

    public static Record validateAndBuildRecord(List<DBMSDataType> values, List<Column> columnList, Catalog catalog, Table currentTable) throws DBMSException {
        if (values.size() != columnList.size()) {
            throw new DBMSException("Column count mismatch. Expected " + columnList.size() + " but got " + values.size());
        }

        Record record = new Record();
        for (int i = 0; i < values.size(); i++) {
            Column column = columnList.get(i);
            DBMSDataType value = values.get(i);
            if (value == null) {
                if (column.isPrimaryKey()) {
                    throw new DBMSException("Primary key column '" + column.getColumnName() + "' must be non-null and non-empty.");
                }
                throw new DBMSException("Column '" + column.getColumnName() + "' must not be null.");
            }
            if (!value.typeEquals(column.getColumn_type())) {
                throw new DBMSException("Type mismatch at column " + column.getColumnName()
                        + ". Expected " + column.getColumn_type() + " but got " + value.getType());
            }
            record.setValue(column.getColumnName(), value);
        }
        validateForeignKeyReferences(record, columnList, catalog);
        return record;
    }

    public static void validatePrimaryKey(Record record, List<String> primaryKeyColumns, TableIndexManager indexManager) throws DBMSException {
        if (primaryKeyColumns.isEmpty()) {
            return;
        }
        for (String primaryKeyColumn : primaryKeyColumns) {
            DBMSDataType value = record.getValue(primaryKeyColumn);
            if (value == null || value.toString().isEmpty()) {
                throw new DBMSException("Primary key column '" + primaryKeyColumn + "' must be non-null and non-empty.");
            }
        }
        String primaryKey = IndexKeyCodec.indexKey(record, primaryKeyColumns);
        if (!indexManager.getPrimaryKeyIndex().search(primaryKey).isEmpty()) {
            throw new DBMSException("Duplicate primary key value.");
        }
    }

    public static void validateForeignKeyReferences(Record record, List<Column> columnList, Catalog catalog) throws DBMSException {
        for (Column column : columnList) {
            if (!column.hasForeignKey()) {
                continue;
            }
            if (catalog == null) {
                throw new DBMSException("Foreign key validation requires catalog context.");
            }

            DBMSDataType value = record.getValue(column.getColumnName());
            Table referencedTable = catalog.getTable(column.getForeignTableName());
            if (!referencedTable.containsValue(column.getForeignColumnName(), value)) {
                throw new DBMSException("Foreign key violation on column '" + column.getColumnName()
                        + "': value '" + value + "' does not exist in "
                        + column.getForeignTableName() + "." + column.getForeignColumnName() + ".");
            }
        }
    }

    public static boolean containsValue(Table table, String columnName, DBMSDataType expectedValue) throws DBMSException {
        TableIterator tableIterator = table.iterator();
        while (tableIterator.hasNext()) {
            Record record = tableIterator.next();
            if (record.getValue(columnName).equals(expectedValue)) {
                return true;
            }
        }
        return false;
    }
}
