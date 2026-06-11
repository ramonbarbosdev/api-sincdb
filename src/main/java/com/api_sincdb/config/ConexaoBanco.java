package com.api_sincdb.config;

import com.api_sincdb.ApplicationContextLoad;
import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.conexao.model.Conexao;
import com.api_sincdb.domain.conexao.repository.ConexaoRepository;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.security.JWTTokenAutenticacaoService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Configuration
public class ConexaoBanco {

    private static final Dotenv dotenv;

    private final ConexaoRepository conexaoRepository;

    public ConexaoBanco(ConexaoRepository conexaoRepository) {
        this.conexaoRepository = conexaoRepository;
    }

    @Autowired
    private JWTTokenAutenticacaoService autenticacaoService;

    static {
        try {
            dotenv = Dotenv.configure()
                    // .directory("src/main/resources/.env")
                    .directory("./.env")
                    .ignoreIfMissing() // não derruba o backend
                    .ignoreIfMalformed()
                    .load();
                    
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Erro ao carregar o arquivo .env: " + e.getMessage());
        }
    }

    private static final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();

    private static HikariDataSource criarDataSource(String host, String port, String database, String user,
            String password) throws Exception {

        String dbToUse = database;

        if (dbToUse == null || dbToUse.isBlank() || dbToUse.contains("mudar")) {
            dbToUse = detectarBancoDefault(host, port, user, password);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbToUse);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(30000);
        return new HikariDataSource(config);
    }

    private static String detectarBancoDefault(String host, String port, String user, String password)
            throws Exception {
        String[] candidatos = { "postgres", "defaultdb", user, "template1" };

        for (String candidato : candidatos) {
            if (existeDatabase(host, port, user, password, candidato)) {
                return candidato;
            }
        }

        throw new RuntimeException(
                "Conexao padrao encontrada, mas nenhum banco inicial acessivel foi localizado. "
                        + "Informe um banco existente ou garanta acesso a postgres, defaultdb, "
                        + user + " ou template1.");
    }

    private static boolean existeDatabase(String host, String port, String user, String password, String db) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + port + "/" + db, user, password);
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (Exception e) {
            return false; // não existe ou não acessível
        }
    }

    public Connection abrirConexao(String database, TipoConexao tipo, String token) throws Exception {

        return abrirConexao(database, tipo, token, null);
    }

    public Connection abrirConexao(String database, TipoConexao tipo, String token, String idConexaoSelecionada)
            throws Exception {

        Map<String, String> response = gerenciarConexao(database, tipo, false, token, idConexaoSelecionada);

        String host = response.get("host");
        String port = response.get("port");
        String user = response.get("user");
        String password = response.get("password");
        String idEmpresa = response.get("id_empresa");
        String idConexao = response.get("id_conexao");

        String chave = String.join("_",
                idEmpresa == null || idEmpresa.isBlank() ? "sem_empresa" : idEmpresa,
                idConexao == null || idConexao.isBlank() ? "sem_conexao" : idConexao,
                database,
                tipo.name());

        HikariDataSource dataSource = dataSourceMap.get(chave);

        if (host == null || host.isBlank() || port == null || port.isBlank()) {
            throw new SQLException("Dados incompletos: host ou porta ausentes ao tentar conectar com o banco " + tipo);
        }

        if (dataSource == null) {
            synchronized (ConexaoBanco.class) {
                dataSource = dataSourceMap.get(chave);
                if (dataSource == null) {
                    dataSource = criarDataSource(host, port, database, user, password);
                    dataSourceMap.put(chave, dataSource);
                }
            }
        }

        return dataSource.getConnection();
    }

    public Map<String, String> gerenciarConexao(String database, TipoConexao tipo, Boolean form, String token)
            throws SQLException {
        return gerenciarConexao(database, tipo, form, token, null);
    }

    public Map<String, String> gerenciarConexao(String database, TipoConexao tipo, Boolean form, String token,
            String idConexaoSelecionada) throws SQLException {
        Map<String, String> response = new HashMap<String, String>();

        String host = "";
        String port = "";
        String user = "";
        String password = "";

        Map<String, String> dados = buscarDadosConexao(tipo, token, idConexaoSelecionada);

        if (dados != null && dados.size() > 0) {
            if (dados == null)
                throw new SQLException("Falha ao buscar dados da conexão para: " + tipo);

            host = dados.get("host");
            port = dados.get("port");
            user = dados.get("user");
            password = dados.get("password");

        } else {

            // if (tipo.equals(TipoConexao.LOCAL)) {
            // host = ConfigPropertiesBanco.get("spring.datasource.host");
            // port = ConfigPropertiesBanco.get("spring.datasource.port");
            // user = ConfigPropertiesBanco.get("spring.datasource.username");
            // password = ConfigPropertiesBanco.get("spring.datasource.password");
            // } else if (tipo.equals(TipoConexao.CLOUD)) {
            // host = dotenv.get("DATABASE_" + tipo + "_HOST");
            // port = dotenv.get("DATABASE_" + tipo + "_PORT");
            // user = dotenv.get("DATABASE_" + tipo + "_USER");
            // password = dotenv.get("DATABASE_" + tipo + "_PASS");
            // } else {
            // throw new SQLException("Tipo de conexão inválido: " + tipo);
            // }

        }

        validacaoConecao(database, tipo, dados);

        response.put("host", host);
        response.put("port", port);
        response.put("user", user);
        response.put("password", password);
        response.put("id_empresa", dados.get("id_empresa"));
        response.put("id_conexao", dados.get("id_conexao"));

        return response;
    }

    public static void validacaoConecao(String database, TipoConexao tipo, Map<String, String> dados)
            throws SQLException {
        if (database == null || database.isEmpty())
            throw new IllegalArgumentException("O nome do banco de dados não pode ser nulo ou vazio.");

        if (tipo == null)
            throw new IllegalArgumentException("O tipo de conexão não pode ser nulo.");

        if (dados != null && dados.size() > 0) {
            if (dados.get("host") == null)
                throw new IllegalArgumentException(
                        "O host do banco de dados " + tipo + " não pode ser nulo ou vazio. Verifique as Conexões!");
            if (dados.get("port") == null)
                throw new IllegalArgumentException(
                        "A porta do banco de dados " + tipo + " não pode ser nulo ou vazio. Verifique as Conexões!");
            if (dados.get("user") == null)
                throw new IllegalArgumentException(
                        "O usuário do banco de dados " + tipo + " não pode ser nulo ou vazio. Verifique as Conexões!");
            if (dados.get("password") == null)
                throw new IllegalArgumentException(
                        "A senha do banco de dados " + tipo + " não pode ser nulo ou vazio. Verifique as Conexões!");
        }

        String chave = database + "_" + tipo.name();

        HikariDataSource dataSource = dataSourceMap.get(chave);
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Pool de conexão já existe para o banco: " + chave);
        } else {
            System.out.println("Nenhum pool encontrado para o banco: " + chave);
        }
    }

    public static void fecharConexao(String database, TipoConexao tipo) {
        String chave = database + "_" + tipo.name();
        HikariDataSource dataSource = dataSourceMap.get(chave);

        if (dataSource != null) {
            if (!dataSource.isClosed()) {
                try {
                    dataSource.close();
                    System.out.println("Pool de conexão fechado para o banco: " + chave);
                } catch (Exception e) {
                    System.err.println("Erro ao fechar o pool para o banco " + chave + ": " + e.getMessage());
                } finally {
                    dataSourceMap.remove(chave);
                }
            } else {
                System.out.println("Pool já estava fechado para o banco: " + chave);
                dataSourceMap.remove(chave);
            }
        } else {
            System.out.println("Nenhum pool encontrado para o banco: " + chave);
        }
    }

    public static void fecharTodos() {
        for (Map.Entry<String, HikariDataSource> entry : dataSourceMap.entrySet()) {
            String chave = entry.getKey();
            HikariDataSource dataSource = entry.getValue();
            if (dataSource != null) {
                dataSource.close();
                System.out.println("Pool de conexão fechado para: " + chave);
            }
        }
        dataSourceMap.clear();
    }

    public Map<String, String> buscarDadosConexao(TipoConexao tipo, String token) {
        return buscarDadosConexao(tipo, token, null);
    }

    public Map<String, String> buscarDadosConexao(TipoConexao tipo, String token, String idConexaoSelecionada) {

        Map<String, String> dados = new HashMap<>();

        String idEmpresaContexto = resolverIdEmpresaAtiva(token);
        String user = autenticacaoService.obterUsuarioLogado(token);

        Usuario usuario = ApplicationContextLoad.getApplicationContext()
                .getBean(UsuarioRepository.class)
                .findByLogin(user);

        if (user == null || usuario == null)
            return dados;

        Optional<Conexao> optionalConexao = Optional.empty();

        if (idConexaoSelecionada != null && !idConexaoSelecionada.isBlank()
                && idEmpresaContexto != null && !idEmpresaContexto.isBlank()) {
            optionalConexao = conexaoRepository
                    .findByIdAndId_empresaAndIdUsuario(idConexaoSelecionada, idEmpresaContexto, usuario.getId())
                    .filter(conexao -> Boolean.TRUE.equals(conexao.getFl_ativo()));
        }

        if (optionalConexao.isEmpty() && idConexaoSelecionada != null && !idConexaoSelecionada.isBlank()
                && (idEmpresaContexto == null || idEmpresaContexto.isBlank())) {
            optionalConexao = conexaoRepository.findById(idConexaoSelecionada)
                    .filter(conexao -> usuario != null && usuario.getId().equals(conexao.getIdUsuario()))
                    .filter(conexao -> Boolean.TRUE.equals(conexao.getFl_ativo()));
        }

        if (optionalConexao.isEmpty() && idEmpresaContexto != null && !idEmpresaContexto.isBlank()) {
            optionalConexao = conexaoRepository
                    .findFirstById_empresaAndIdUsuarioAndFl_padraoTrueAndFl_ativoTrue(
                            idEmpresaContexto,
                            usuario.getId());
        }

        if (optionalConexao.isEmpty() && (idEmpresaContexto == null || idEmpresaContexto.isBlank())) {
            optionalConexao = Optional.ofNullable(conexaoRepository.findFirstByIdUsuarioAndFl_ativoTrue(usuario.getId()));
        }

        if (optionalConexao.isPresent()) {
            Conexao conexao = optionalConexao.get();

            adicionarValido(dados, "id_empresa", conexao.getId_empresa());
            adicionarValido(dados, "id_conexao", conexao.getId());

            if (tipo == TipoConexao.CLOUD) {
                adicionarValido(dados, "host", conexao.getDb_cloud_host());
                adicionarValido(dados, "port", conexao.getDb_cloud_port());
                adicionarValido(dados, "user", conexao.getDb_cloud_user());
                adicionarValido(dados, "password", conexao.getDb_cloud_password());
            } else if (tipo == TipoConexao.LOCAL) {
                adicionarValido(dados, "host", conexao.getDb_local_host());
                adicionarValido(dados, "port", conexao.getDb_local_port());
                adicionarValido(dados, "user", conexao.getDb_local_user());
                adicionarValido(dados, "password", conexao.getDb_local_password());
            }

            return dados;
        } else {

            throw new RuntimeException("Nenhuma conexão encontrada");
        }
    }

    private static void adicionarValido(Map<String, String> mapa, String chave, String valor) {
        if (valor != null && !valor.trim().isEmpty()) {
            mapa.put(chave, valor);
        }
    }

    private String resolverIdEmpresaAtiva(String token) {
        String idEmpresa = TenantRuntimeContext.getIdEmpresa();

        if (idEmpresa != null && !idEmpresa.isBlank()) {
            return idEmpresa;
        }

        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            return autenticacaoService.extractEmpresaId(token);
        } catch (Exception e) {
            return null;
        }
    }

}
