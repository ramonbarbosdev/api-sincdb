package com.api_sincdb.domain.sql.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.sql.model.SqlExecutionHistory;
import com.api_sincdb.domain.sql.repository.SqlExecutionHistoryRepository;

@Service
public class SqlHistoryService {

    private final SqlExecutionHistoryRepository repository;

    public SqlHistoryService(SqlExecutionHistoryRepository repository) {
        this.repository = repository;
    }

    public void registrar(String ambiente, String conexaoId, String base, String sql,
            long executionTimeMs, boolean success, String errorMessage) {
        SqlExecutionHistory history = new SqlExecutionHistory();
        history.setIdUsuario(TenantRuntimeContext.getIdUsuario());
        history.setId_empresa(TenantRuntimeContext.getIdEmpresa());
        history.setId_tenant(TenantRuntimeContext.getIdTenant());
        history.setAmbiente(ambiente);
        history.setConexaoId(conexaoId);
        history.setBase(base);
        history.setSql(sql);
        history.setExecutionTimeMs(executionTimeMs);
        history.setSuccess(success);
        history.setErrorMessage(errorMessage);
        history.setExecutedAt(LocalDateTime.now());

        repository.save(history);
    }
}
