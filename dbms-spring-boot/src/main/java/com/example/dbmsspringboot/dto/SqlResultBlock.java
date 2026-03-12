package com.example.dbmsspringboot.dto;

import java.util.List;

public class SqlResultBlock {
    private String message;
    private List<String> columns;
    private List<List<String>> rows;

    public SqlResultBlock() {
    }

    public SqlResultBlock(String message, List<String> columns, List<List<String>> rows) {
        this.message = message;
        this.columns = columns;
        this.rows = rows;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }
}
