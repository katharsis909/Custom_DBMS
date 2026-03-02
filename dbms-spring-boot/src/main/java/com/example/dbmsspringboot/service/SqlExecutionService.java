package com.example.dbmsspringboot.service;

import com.example.dbmsspringboot.dto.SqlResponse;
import dbmscli.DbmsCliEngine;
import org.springframework.stereotype.Service;

@Service
public class SqlExecutionService {
    private final DbmsCliEngine engine;

    public SqlExecutionService(DbmsCliEngine engine) {
        this.engine = engine;
    }

    public SqlResponse execute(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return new SqlResponse(false, null, "SQL must not be empty");
        }

        String normalizedSql = sql.trim();
        if (!normalizedSql.endsWith(";")) {
            normalizedSql = normalizedSql + ";";
        }

        try {
            String output = engine.execute(normalizedSql);
            if (output == null || output.isBlank()) {
                output = "OK";
            }
            return new SqlResponse(true, output, null);
        } catch (Exception ex) {
            return new SqlResponse(false, null, ex.getMessage());
        }
    }

    public SqlResponse resetCatalog() {
        engine.reset();
        return new SqlResponse(true, "Catalog reset", null);
    }
}
