package com.api_sincdb.domain.sql.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLTimeoutException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.config.SshTunnelService;
import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.conexao.model.Conexao;
import com.api_sincdb.domain.conexao.repository.ConexaoRepository;
import com.api_sincdb.domain.parametro.service.ParametroMasterService;
import com.api_sincdb.domain.sql.dto.SqlCatalogoColumnDTO;
import com.api_sincdb.domain.sql.dto.SqlCatalogoDTO;
import com.api_sincdb.domain.sql.dto.SqlCatalogoSchemaDTO;
import com.api_sincdb.domain.sql.dto.SqlCatalogoTableDTO;
import com.api_sincdb.enums.TipoConexao;

@Service
public class SqlCatalogoService {

    private static final int CATALOGO_TIMEOUT_SECONDS = 15;
    private static final String CATALOGO_ERROR_MESSAGE =
            "Nao foi possivel carregar o catalogo da base selecionada.";
    private static final String SQL_CATALOGO = """
            SELECT
                c.table_schema,
                c.table_name,
                c.column_name,
                c.data_type,
                c.ordinal_position
            FROM information_schema.columns c
            JOIN information_schema.tables t
                ON t.table_schema = c.table_schema
               AND t.table_name = c.table_name
            WHERE c.table_schema NOT IN ('pg_catalog', 'information_schema')
              AND t.table_type = 'BASE TABLE'
            ORDER BY
                c.table_schema,
                c.table_name,
                c.ordinal_position
            """;

    private final ConexaoRepository conexaoRepository;
    private final ParametroMasterService parametroMasterService;
    private final SqlCatalogoCacheService cacheService;
    private final ConexaoBanco conexaoBanco;

    public SqlCatalogoService(
            ConexaoRepository conexaoRepository,
            ParametroMasterService parametroMasterService,
            SqlCatalogoCacheService cacheService,
            ConexaoBanco conexaoBanco) {
        this.conexaoRepository = conexaoRepository;
        this.parametroMasterService = parametroMasterService;
        this.cacheService = cacheService;
        this.conexaoBanco = conexaoBanco;
    }

    public SqlCatalogoDTO buscarCatalogo(String ambiente, String idConexao, String base) {
        validarParametros(ambiente, idConexao, base);
        validarAmbientePermitido(ambiente);

        String idEmpresa = exigirContexto("Organizacao ativa nao encontrada no token.",
                TenantRuntimeContext.getIdEmpresa());
        String idTenant = exigirContexto("Tenant ativo nao encontrado no token.", TenantRuntimeContext.getIdTenant());

        String ambienteNormalizado = ambiente.trim().toLowerCase();
        String idConexaoNormalizado = idConexao.trim();
        String baseNormalizada = base.trim();
        String cacheKey = cacheKey(idEmpresa, idConexaoNormalizado, ambienteNormalizado, baseNormalizada);

        return cacheService.get(cacheKey)
                .orElseGet(() -> carregarECacher(
                        cacheKey,
                        idEmpresa,
                        idTenant,
                        idConexaoNormalizado,
                        ambienteNormalizado,
                        baseNormalizada));
    }

    private SqlCatalogoDTO carregarECacher(String cacheKey, String idEmpresa, String idTenant,
            String idConexao, String ambiente, String base) {
        Conexao conexao = buscarConexao(idConexao, idEmpresa, idTenant);
        SqlCatalogoDTO catalogo = carregarCatalogo(conexao, ambiente, base);
        cacheService.put(cacheKey, catalogo);
        return catalogo;
    }

    private SqlCatalogoDTO carregarCatalogo(Conexao conexao, String ambiente, String base) {
        Credenciais credenciais = resolverCredenciais(conexao, ambiente);
        TipoConexao tipoConexao = resolverTipoConexao(ambiente);
        try {
            SshTunnelService.ResolvedJdbcEndpoint endpoint = conexaoBanco.resolverJdbcParaConexao(conexao, tipoConexao);
            return carregarCatalogo(credenciais, endpoint, base);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CATALOGO_ERROR_MESSAGE);
        }
    }

    private SqlCatalogoDTO carregarCatalogo(
            Credenciais credenciais,
            SshTunnelService.ResolvedJdbcEndpoint endpoint,
            String base) {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(endpoint.host(), endpoint.port(), base),
                credenciais.user(),
                credenciais.password());
                PreparedStatement statement = connection.prepareStatement(SQL_CATALOGO)) {

            statement.setQueryTimeout(CATALOGO_TIMEOUT_SECONDS);

            try (ResultSet resultSet = statement.executeQuery()) {
                return montarCatalogo(resultSet);
            }
        } catch (SQLTimeoutException e) {
            throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, CATALOGO_ERROR_MESSAGE);
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CATALOGO_ERROR_MESSAGE);
        }
    }

    private SqlCatalogoDTO montarCatalogo(ResultSet resultSet) throws SQLException {
        Map<String, SqlCatalogoSchemaDTO> schemas = new LinkedHashMap<>();
        Map<String, SqlCatalogoTableDTO> tables = new LinkedHashMap<>();

        while (resultSet.next()) {
            String schemaName = resultSet.getString("table_schema");
            String tableName = resultSet.getString("table_name");
            String columnName = resultSet.getString("column_name");
            String columnType = resultSet.getString("data_type");

            SqlCatalogoSchemaDTO schema = schemas.computeIfAbsent(
                    schemaName,
                    name -> new SqlCatalogoSchemaDTO(name, new ArrayList<>()));

            String tableKey = schemaName + "." + tableName;
            SqlCatalogoTableDTO table = tables.computeIfAbsent(tableKey, key -> {
                SqlCatalogoTableDTO novaTabela = new SqlCatalogoTableDTO(tableName, new ArrayList<>());
                schema.getTables().add(novaTabela);
                return novaTabela;
            });

            table.getColumns().add(new SqlCatalogoColumnDTO(columnName, columnType));
        }

        return new SqlCatalogoDTO(new ArrayList<>(schemas.values()));
    }

    private void validarParametros(String ambiente, String idConexao, String base) {
        if (isBlank(ambiente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente nao informado.");
        }
        if (!"cloud".equalsIgnoreCase(ambiente.trim()) && !"local".equalsIgnoreCase(ambiente.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente deve ser cloud ou local.");
        }
        if (isBlank(idConexao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conexao nao informada.");
        }
        if (isBlank(base)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base nao informada.");
        }
    }

    private void validarAmbientePermitido(String ambiente) {
        if (!"cloud".equalsIgnoreCase(ambiente)) {
            return;
        }

        boolean cloudHabilitado = parametroMasterService.parametroBooleano(
                SqlEditorService.PARAM_SQL_EDITOR_CLOUD_HABILITADO,
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

    private TipoConexao resolverTipoConexao(String ambiente) {
        if ("cloud".equalsIgnoreCase(ambiente)) {
            return TipoConexao.CLOUD;
        }
        return TipoConexao.LOCAL;
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

        return credenciaisObrigatorias(
                conexao.getDb_local_host(),
                conexao.getDb_local_port(),
                conexao.getDb_local_user(),
                conexao.getDb_local_password(),
                "local");
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

    private String cacheKey(String idEmpresa, String idConexao, String ambiente, String base) {
        return String.join(":", idEmpresa, idConexao, ambiente, base);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Credenciais(String host, String port, String user, String password) {
    }
}
