package com.api_sincdb.domain.sql.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.sql.dto.SqlExecutionRequest;
import com.api_sincdb.domain.sql.dto.SqlExecutionResponse;
import com.api_sincdb.domain.sql.service.SqlEditorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/sql")
public class SqlEditorController {

    private final SqlEditorService sqlEditorService;

    public SqlEditorController(SqlEditorService sqlEditorService) {
        this.sqlEditorService = sqlEditorService;
    }

    @PostMapping("/executar")
    public ResponseEntity<SqlExecutionResponse> executar(@Valid @RequestBody SqlExecutionRequest request) {
        return ResponseEntity.ok(sqlEditorService.executar(request));
    }
}
