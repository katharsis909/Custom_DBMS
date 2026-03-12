package com.example.dbmsspringboot.service;

import com.example.dbmsspringboot.dto.SqlResultBlock;
import com.example.dbmsspringboot.dto.SqlResponse;
import dbmscli.DbmsCliEngine;
import dbmscli.result.ExecutionResult;
import dbmscli.result.QueryResultBlock;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SqlExecutionService {
    private final DbmsCliEngine engine;

    public SqlExecutionService(DbmsCliEngine engine) {
        this.engine = engine;
    }

    public SqlResponse execute(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return new SqlResponse(false, null, "SQL must not be empty", null);
        }

        String normalizedSql = sql.trim();
        if (!normalizedSql.endsWith(";")) {
            normalizedSql = normalizedSql + ";";
        }

        try {
            ExecutionResult executionResult = engine.executeStructured(normalizedSql);
            String output = executionResult.renderText();
            if (output == null || output.isBlank()) {
                output = "OK";
            }
            return new SqlResponse(true, output, null, toResponseBlocks(executionResult));
        } catch (Exception ex) {
            return new SqlResponse(false, null, ex.getMessage(), null);
        }
    }

    public SqlResponse resetCatalog() {
        engine.reset();
        return new SqlResponse(true, "Catalog reset", null, null);
    }

    private List<SqlResultBlock> toResponseBlocks(ExecutionResult executionResult) {
        List<SqlResultBlock> blocks = new ArrayList<>();
        for (QueryResultBlock block : executionResult.getBlocks()) {
            blocks.add(new SqlResultBlock(block.getMessage(), block.getColumns(), block.getRows()));
        }
        return blocks;
    }
}
