package com.example.dbmsspringboot.dto;

import java.util.List;

public class SqlResponse {
    private boolean success;
    private String output;
    private String error;
    private List<SqlResultBlock> results;

    public SqlResponse() {
    }

    public SqlResponse(boolean success, String output, String error, List<SqlResultBlock> results) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.results = results;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<SqlResultBlock> getResults() {
        return results;
    }

    public void setResults(List<SqlResultBlock> results) {
        this.results = results;
    }
}
