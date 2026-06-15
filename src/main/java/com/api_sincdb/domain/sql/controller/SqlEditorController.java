package com.api_sincdb.domain.sql.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.sql.dto.SqlCatalogoDTO;
import com.api_sincdb.domain.sql.dto.SqlExecutionRequest;
import com.api_sincdb.domain.sql.dto.SqlExecutionResponse;
import com.api_sincdb.domain.sql.service.SqlCatalogoService;
import com.api_sincdb.domain.sql.service.SqlEditorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/sql")
public class SqlEditorController {

    private final SqlEditorService sqlEditorService;
    private final SqlCatalogoService sqlCatalogoService;

    public SqlEditorController(SqlEditorService sqlEditorService, SqlCatalogoService sqlCatalogoService) {
        this.sqlEditorService = sqlEditorService;
        this.sqlCatalogoService = sqlCatalogoService;
    }

    @PostMapping("/executar")
    public ResponseEntity<SqlExecutionResponse> executar(@Valid @RequestBody SqlExecutionRequest request) {
        return ResponseEntity.ok(sqlEditorService.executar(request));
    }

    @GetMapping("/catalogo")
    public ResponseEntity<SqlCatalogoDTO> catalogo(
            @RequestParam String ambiente,
            @RequestParam String idConexao,
            @RequestParam String base) {
        return ResponseEntity.ok(sqlCatalogoService.buscarCatalogo(ambiente, idConexao, base));
    }
}
