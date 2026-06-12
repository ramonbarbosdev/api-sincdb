package com.api_sincdb.domain.sql.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.conexao.model.Conexao;
import com.api_sincdb.domain.conexao.repository.ConexaoRepository;
import com.api_sincdb.domain.parametro.service.ParametroMasterService;
import com.api_sincdb.domain.sql.dto.SqlColumnDTO;
import com.api_sincdb.domain.sql.dto.SqlExecutionRequest;
import com.api_sincdb.domain.sql.dto.SqlExecutionResponse;

@Service
public class SqlEditorService {

    public static final String PARAM_SQL_EDITOR_CLOUD_HABILITADO = "PARAM_SQL_EDITOR_CLOUD_HABILITADO";

    private final SqlSecurityValidator sqlSecurityValidator;
    private final SqlHistoryService sqlHistoryService;
    private final ConexaoRepository conexaoRepository;
    private final ParametroMasterService parametroMasterService;

    public SqlEditorService(SqlSecurityValidator sqlSecurityValidator,
            SqlHistoryService sqlHistoryService,
            ConexaoRepository conexaoRepository,
            ParametroMasterService parametroMasterService) {
        this.sqlSecurityValidator = sqlSecurityValidator;
        this.sqlHistoryService = sqlHistoryService;
        this.conexaoRepository = conexaoRepository;
        this.parametroMasterService = parametroMasterService;
    }

    public SqlExecutionResponse executar(SqlExecutionRequest request) {
        long inicio = System.currentTimeMillis();
        String sqlValidado = null;

        try {
            validarRequest(request);
            validarAmbientePermitido(request.getAmbiente());
            sqlValidado = sqlSecurityValidator.validate(request.getSql());

            String idEmpresa = exigirContexto("Organizacao ativa nao encontrada no token.", TenantRuntimeContext.getIdEmpresa());
            String idTenant = exigirContexto("Tenant ativo nao encontrado no token.", TenantRuntimeContext.getIdTenant());
            exigirContexto("Usuario nao encontrado no token.", TenantRuntimeContext.getIdUsuario());

            Conexao conexao = buscarConexao(request.getConexaoId(), idEmpresa, idTenant);
            Credenciais credenciais = resolverCredenciais(conexao, request.getAmbiente());

            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl(credenciais.host(), credenciais.port(), request.getBase().trim()),
                    credenciais.user(),
                    credenciais.password());
                    Statement statement = connection.createStatement()) {

                statement.setQueryTimeout(SqlSecurityValidator.QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(SqlSecurityValidator.MAX_ROWS);

                try (ResultSet resultSet = statement.executeQuery(sqlValidado)) {
                    SqlExecutionResponse response = montarResponse(resultSet, System.currentTimeMillis() - inicio);
                    registrarHistorico(request, sqlValidado, response.getExecutionTimeMs(), true, null);
                    return response;
                }
            }
        } catch (ResponseStatusException e) {
            registrarHistorico(request, sqlValidado, System.currentTimeMillis() - inicio, false, e.getReason());
            throw e;
        } catch (SQLTimeoutException e) {
            String message = "Timeout ao executar a consulta SQL.";
            registrarHistorico(request, sqlValidado, System.currentTimeMillis() - inicio, false, message);
            throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, message);
        } catch (Exception e) {
            String message = "Erro JDBC ao executar consulta: " + e.getMessage();
            registrarHistorico(request, sqlValidado, System.currentTimeMillis() - inicio, false, message);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private SqlExecutionResponse montarResponse(ResultSet resultSet, long executionTimeMs) throws Exception {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int totalColunas = metaData.getColumnCount();
        List<SqlColumnDTO> columns = new ArrayList<>();

        for (int i = 1; i <= totalColunas; i++) {
            columns.add(new SqlColumnDTO(metaData.getColumnLabel(i), metaData.getColumnTypeName(i)));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= totalColunas; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }

        return new SqlExecutionResponse(columns, rows, executionTimeMs, 0, "Consulta executada com sucesso.");
    }

    private void validarRequest(SqlExecutionRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request nao informado.");
        }
        if (request.getAmbiente() == null || request.getAmbiente().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente nao informado.");
        }
        if (request.getBase() == null || request.getBase().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base nao informada.");
        }
        if (request.getConexaoId() == null || request.getConexaoId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conexao nao informada.");
        }
    }

    private void validarAmbientePermitido(String ambiente) {
        if (!"cloud".equalsIgnoreCase(ambiente)) {
            return;
        }

        boolean cloudHabilitado = parametroMasterService.parametroBooleano(
                PARAM_SQL_EDITOR_CLOUD_HABILITADO,
                false);

        if (!cloudHabilitado) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Execucao SQL no ambiente cloud esta desabilitada por parametro.");
        }
    }

    private Conexao buscarConexao(String conexaoId, String idEmpresa, String idTenant) {
        Conexao conexao = conexaoRepository.findByIdAndId_empresa(conexaoId, idEmpresa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conexao nao encontrada."));

        if (!Boolean.TRUE.equals(conexao.getFl_ativo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conexao nao encontrada.");
        }

        if (conexao.getId_tenant() != null && !conexao.getId_tenant().isBlank()
                && !idTenant.equals(conexao.getId_tenant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conexao nao encontrada.");
        }

        return conexao;
    }

    private Credenciais resolverCredenciais(Conexao conexao, String ambiente) {
        if ("cloud".equalsIgnoreCase(ambiente)) {
            return credenciaisObrigatorias(
                    conexao.getDb_cloud_host(),
                    conexao.getDb_cloud_port(),
                    conexao.getDb_cloud_user(),
                    conexao.getDb_cloud_password(),
                    "cloud");
        }

        if ("local".equalsIgnoreCase(ambiente)) {
            return credenciaisObrigatorias(
                    conexao.getDb_local_host(),
                    conexao.getDb_local_port(),
                    conexao.getDb_local_user(),
                    conexao.getDb_local_password(),
                    "local");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente deve ser cloud ou local.");
    }

    private Credenciais credenciaisObrigatorias(String host, String port, String user, String password,
            String ambiente) {
        if (isBlank(host) || isBlank(port) || isBlank(user) || password == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dados de conexao " + ambiente + " incompletos.");
        }
        return new Credenciais(host.trim(), port.trim(), user.trim(), password);
    }

    private String exigirContexto(String message, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        }
        return value;
    }

    private String jdbcUrl(String host, String port, String base) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + base;
    }

    private void registrarHistorico(SqlExecutionRequest request, String sql, long executionTimeMs,
            boolean success, String errorMessage) {
        if (request == null) {
            return;
        }

        try {
            sqlHistoryService.registrar(
                    request.getAmbiente(),
                    request.getConexaoId(),
                    request.getBase(),
                    sql != null ? sql : request.getSql(),
                    executionTimeMs,
                    success,
                    errorMessage);
        } catch (Exception ignored) {
            // Historico nao deve impedir a resposta da execucao SQL.
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Credenciais(String host, String port, String user, String password) {
    }
}
