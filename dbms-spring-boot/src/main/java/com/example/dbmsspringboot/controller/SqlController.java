package com.example.dbmsspringboot.controller;

import com.example.dbmsspringboot.dto.SqlRequest;
import com.example.dbmsspringboot.dto.SqlResponse;
import com.example.dbmsspringboot.service.SqlExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql")
public class SqlController {
    private final SqlExecutionService sqlExecutionService;

    public SqlController(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<SqlResponse> execute(@RequestBody SqlRequest request) {
        SqlResponse response = sqlExecutionService.execute(request.getSql());
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/reset")
    public ResponseEntity<SqlResponse> reset() {
        return ResponseEntity.ok(sqlExecutionService.resetCatalog());
    }
}
