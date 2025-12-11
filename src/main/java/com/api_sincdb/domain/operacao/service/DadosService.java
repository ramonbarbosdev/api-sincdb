package com.api_sincdb.domain.operacao.service;

import java.beans.Statement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.operacao.model.TabelaDetalhe;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.util.UtilsSync;
import com.api_sincdb.websocket.LogPublisher;

import jakarta.persistence.criteria.CriteriaBuilder;

@Service
public class DadosService {

    @Autowired
    private AtualizarEstruturaService atualizarEstruturaService;

    @Autowired
    private CicloService cicloService;

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private OperacaoBancoService operacaoBancoService;

    @Autowired
    private UtilsSync utilsSync;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ConexaoBanco conexaoBanco;

    @Autowired
    private LogPublisher logPublisher;

    @Autowired
    private CriarInsertsDadosService criarInsertsDadosService;

    @Autowired
    private AtualizarDadosService atualizarDadosService;

    // ================================================================
    // FUNCOES PRINCIPAIS
    // ================================================================

    public Map<String, Object> verificarDados(String token, String database, String tabela) {
        Map<String, Object> response = new HashMap<String, Object>();
        List<TabelaDetalhe> detalhes = new ArrayList<>();

        try (Connection conexaoCloud = conexaoBanco.abrirConexao(database, TipoConexao.CLOUD, token);
                Connection conexaoLocal = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token)) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");
            HashMap<String, List<String>> querys = obterDadosTabela(database, conexaoCloud, conexaoLocal, tabela,
                    detalhes);
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");

            if (querys != null) {
                cacheService.salvarCache(database + "_dados:", querys);
                response.put("tabelas_afetadas", detalhes);
            }

            response.put("sucesso", true);
        } catch (InterruptedException e) {
            utilsSync.tratarErroCancelamento(response, e);
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            utilsSync.tratarErroSincronizacao(response, e);
        } catch (Exception e) {
            utilsSync.tratarErroSincronizacao(response, e);
        }

        return response;
    }

    public Map<String, Object> sincronizarDados(String token, String database, String tabela, Boolean fl_verificacao) {

        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, String>> detalhes = new ArrayList<>();

        try (Connection conexaoLocal = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token)) {
            conexaoLocal.setAutoCommit(false);

            desativarConstraints(conexaoLocal);

            @SuppressWarnings("unchecked")
            HashMap<String, List<String>> querys = cacheService.buscarCache(database + "_dados:", HashMap.class);

            if (querys == null) {
                response.put("sucesso", false);
                response.put("errors", "Nenhuma verificação foi feita previamente.");
                logPublisher.enviarLog("Nenhuma verificação foi feita previamente.");

                return response;
            }

            operacaoBancoService.executarQueriesEmLotes(conexaoLocal, querys, detalhes);

            // To:do - construir uma tela a consultar a integridade dos dados
            // validarIntegridadeDados(conexaoLocal, response);

            List<String> listaErro = new ArrayList<>();
            for (Map<String, String> erro : detalhes) {
                listaErro.add(erro.get("errors"));
            }

            response.put("sucesso", true);
            response.put("tabelas_afetadas", detalhes);
            response.put("errors", listaErro);

            ativarConstraints(conexaoLocal);

        } catch (SQLException e) {
            utilsSync.tratarErroSincronizacao(response, e);
        } catch (Exception e) {
            utilsSync.tratarErroSincronizacao(response, e);
        }

        return response;
    }

    public HashMap<String, List<String>> obterDadosTabela(
            String database,
            Connection conexaoCloud,
            Connection conexaoLocal,
            String tabela,
            List<TabelaDetalhe> detalhes) throws SQLException, InterruptedException {

        processoService.iniciarProcesso(database);

        Map<String, Object> parametrosMap = carregarOrdemTabela(conexaoCloud, conexaoLocal, tabela);
        List<String> tabelas = (List<String>) parametrosMap.get("ordemCarga");

        // querys
        List<String> criacaoAtualizacaoSeq = Collections.synchronizedList(new ArrayList<>());
        List<String> criacaoDados = Collections.synchronizedList(new ArrayList<>());
        List<String> atualizacaoDados = Collections.synchronizedList(new ArrayList<>());

        // Processamento
        int totalTabelas = tabelas.size();
        AtomicInteger tabelasProcessadas = new AtomicInteger(0);
        processoService.enviarProgresso("Iniciando", 0, "Iniciando processamento de " + totalTabelas + " tabelas.",
                null);

        logPublisher.enviarLog("Iniciando processamento de " + totalTabelas + " tabelas.");

        for (String itemTabela : tabelas) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");

            int progresso = (int) ((tabelasProcessadas.incrementAndGet() / (double) totalTabelas) * 100);
            processoService.enviarProgresso("Processando", progresso, "Processando tabela: " + itemTabela, itemTabela);

            Map<String, Object> parametros = definirParametrosVerificacao(conexaoCloud, conexaoLocal, itemTabela);

            if (parametros != null) {
                TabelaDetalhe infoDetalhe = new TabelaDetalhe();

                if ((Boolean) parametros.get("novo")) {
                    logPublisher.enviarLog("Criacao da script da '" + itemTabela + "'.");

                    List<String> query = criarInsertsDadosService.cargaInicialCompleta(conexaoCloud, conexaoLocal,
                            itemTabela);

                    if (query.size() > 0) {
                        infoDetalhe.setTabela(itemTabela);
                        infoDetalhe.setAcao("Inserção");
                        infoDetalhe.setLinhaInseridas(query.size());
                        detalhes.add(infoDetalhe);
                        criacaoDados.addAll(query);
                    }

                } else if ((Boolean) parametros.get("existente")) {

                    logPublisher.enviarLog("Tabela '" + itemTabela + "' com atualizações de dados pendendes.");

                    String pkColumn = (String) parametros.get("pkColumn");

                    List<String> query = atualizarDadosService.verificarConsistenciaRegistros(conexaoLocal,
                            conexaoCloud, itemTabela,
                            pkColumn);

                    if (query.size() > 0) {
                        infoDetalhe.setTabela(itemTabela);
                        infoDetalhe.setAcao("Atualização");
                        infoDetalhe.setLinhaAtualizadas(query.size());
                        infoDetalhe.setQuerys(String.join(";\n", query));
                        detalhes.add(infoDetalhe);
                        atualizacaoDados.addAll(query);
                    }

                } else {
                    logPublisher.enviarLog("Tabela '" + itemTabela + "' não possui atualizações de dados pendentes.");

                }

                String querySeq = atualizarSequencias(conexaoLocal, itemTabela);
                if (querySeq != null) {
                    infoDetalhe.setTabela(itemTabela);
                    infoDetalhe.setAcao("Atualização Sequencia");
                    infoDetalhe.setLinhaInseridas(1);
                    infoDetalhe.setQuerys(querySeq);
                    detalhes.add(infoDetalhe);
                    criacaoAtualizacaoSeq.add(querySeq);
                }
            }

        }

        // Processamento
        processoService.enviarProgresso("Concluido", 100, "Verificação concluída com sucesso", null);
        logPublisher.enviarLog("Verificação concluída com sucesso");

        HashMap<String, List<String>> queries = new LinkedHashMap<>();
        queries.put("Criacao", criacaoDados);
        queries.put("Atualizacao", atualizacaoDados);
        queries.put("Sequencia", criacaoAtualizacaoSeq);

        return queries;
    }

    // ================================================================
    // UTILITÁRIOS
    // ================================================================

    public String atualizarSequencias(Connection connection, String nomeTabela) throws SQLException {
        String pkColumn = obterNomeColunaPK(connection, nomeTabela);
        String seq = consultarSequenciasPorTabela(connection, nomeTabela);

        if (seq == null)
            return "";

        String query = String.format(
                "SELECT setval('%s', " +
                        "COALESCE((SELECT MAX(CASE WHEN %s::TEXT ~ '^[0-9]+$' THEN %s::BIGINT ELSE NULL END) FROM %s), 1), true);",
                seq, pkColumn, pkColumn, nomeTabela);

        return query;
    }

    public String consultarSequenciasPorTabela(Connection conexao, String nomeTabela) {
        String seq = null;

        String tabela = utilsSync.extrairTabela(nomeTabela);
        String schema = utilsSync.extrairSchema(nomeTabela);

        try {

            String query = "select " +
                    "t.table_schema, " +
                    "t.table_name, " +
                    "c.column_name, " +
                    "c.column_default, " +
                    "s.schemaname AS sequence_schema, " +
                    "s.sequencename AS sequence_name, " +
                    "s.last_value " +
                    "from information_schema.columns c " +
                    "join information_schema.tables t ON t.table_name = c.table_name AND t.table_schema = c.table_schema "
                    +
                    "join pg_sequences s on c.column_default like '%nextval(''' || s.sequencename || '''%' " +
                    "where t.table_name = ? " +
                    "and t.table_schema = ? ;";

            PreparedStatement stmt = conexao.prepareStatement(query.toString());

            stmt.setString(1, tabela);
            stmt.setString(2, schema);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String sequenceName = rs.getString("sequence_name");

                seq = sequenceName;

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return seq != null ? seq : null;
    }

    public long obterMaxId(Connection conexao, String tabela, String nomeColuna) throws SQLException {
        if (nomeColuna == null || nomeColuna.isEmpty()) {
            nomeColuna = obterNomeColunaPK(conexao, tabela);
            if (nomeColuna == null) {
                return (Long) null;
            }
        }

        String tabelaSemSchema = utilsSync.extrairTabela(tabela);

        String sqlCheck = "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)";

        try (PreparedStatement stmtCheck = conexao.prepareStatement(sqlCheck)) {
            stmtCheck.setString(1, tabelaSemSchema);
            try (ResultSet rsCheck = stmtCheck.executeQuery()) {
                if (rsCheck.next() && !rsCheck.getBoolean(1)) {
                    throw new SQLException("A tabela '" + tabela + "' não existe.");
                }
            }
        }

        String sql = String.format(
                "SELECT COALESCE(MAX(CASE WHEN %s::TEXT ~ '^[0-9]+$' THEN %s::BIGINT ELSE NULL END), 0) FROM %s",
                nomeColuna, nomeColuna, tabela);

        try (PreparedStatement stmt = conexao.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    public String obterNomeColunaPK(Connection conexao, String tabela) throws SQLException {
        String schema = utilsSync.extrairSchema(tabela);
        String nomeTabela = utilsSync.extrairTabela(tabela);

        try (ResultSet rs = conexao.getMetaData().getPrimaryKeys(null, schema, nomeTabela)) {
            if (rs.next()) {
                return rs.getString("COLUMN_NAME");
            }

            return null;
        }
    }

    public int obterQuantidadeRegistro(Connection conexao, String tabela) throws SQLException {
        DSLContext create = DSL.using(conexao, SQLDialect.POSTGRES);

        int count = create.fetchCount(DSL.table(tabela));

        return count;

    }

    public Map<String, Set<String>> obterDependenciasTabelas(Connection conexao) throws SQLException {
        Map<String, Set<String>> dependencias = new HashMap<>();
        DatabaseMetaData meta = conexao.getMetaData();

        // Mapeia todas as tabelas com schema
        try (ResultSet tabelas = meta.getTables(null, null, "%", new String[] { "TABLE" })) {
            while (tabelas.next()) {
                String schema = tabelas.getString("TABLE_SCHEM");
                String nomeTabela = tabelas.getString("TABLE_NAME");
                String chave = schema + "." + nomeTabela;
                dependencias.putIfAbsent(chave, new HashSet<>());
            }
        }

        // Mapeia as dependências (FK -> PK)
        try (ResultSet fks = meta.getImportedKeys(conexao.getCatalog(), null, null)) {
            while (fks.next()) {
                String schemaFilha = fks.getString("FKTABLE_SCHEM");
                String tabelaFilha = fks.getString("FKTABLE_NAME");

                String schemaPai = fks.getString("PKTABLE_SCHEM");
                String tabelaPai = fks.getString("PKTABLE_NAME");

                String chaveFilha = schemaFilha + "." + tabelaFilha;
                String chavePai = schemaPai + "." + tabelaPai;

                dependencias.computeIfAbsent(chaveFilha, k -> new HashSet<>()).add(chavePai);
            }
        }

        return dependencias;
    }

    public List<String> ordenarTabelasPorDependencia(Map<String, Set<String>> dependencias) {
        List<String> ordenadas = new ArrayList<>();
        Set<String> visitadas = new HashSet<>();
        Set<String> emProcessamento = new HashSet<>();
        Set<Set<String>> ciclos = new HashSet<>();

        cicloService.detectarCiclos(dependencias, ciclos);

        for (String tabela : dependencias.keySet()) {
            if (!visitadas.contains(tabela)) {
                cicloService.ordenacaoTopologica(tabela, dependencias, visitadas, emProcessamento, ordenadas, ciclos);
            }
        }

        return ordenadas;
    }

    public Map<String, Object> carregarOrdemTabela(Connection conexaoCloud, Connection conexaoLocal,
            String tabela) throws SQLException {

        Map<String, Object> parametrosMap = new HashMap<String, Object>();

        Map<String, Set<String>> dependencias = obterDependenciasTabelas(conexaoCloud);

        List<String> ordemCarga = ordenarTabelasPorDependencia(dependencias);

        if (tabela != null) {
            ordemCarga = filtrarTabelasRelevantes(tabela, ordemCarga, dependencias);
        }

        parametrosMap.put("ordemCarga", ordemCarga);

        return parametrosMap;
    }

    public void desativarConstraints(Connection conn) throws SQLException {
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("SET session_replication_role = replica");
        }
    }

    public void ativarConstraints(Connection conn) throws SQLException {
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("SET session_replication_role = origin");
        }
    }

    public void validarIntegridadeDados(Connection conexaoLocal, Map<String, Object> response) throws SQLException {
        Map<String, Object> validacao = validarIntegridadeComRelatorio(conexaoLocal);
        response.put("validacao", validacao);

        if (!(Boolean) validacao.getOrDefault("integridade_ok", true)) {
            logPublisher.enviarLog("Problemas de integridade encontrados");
        }
    }

    public Map<String, Object> validarIntegridadeComRelatorio(Connection conn) {
        Map<String, Object> relatorio = new LinkedHashMap<>();
        List<Map<String, Object>> problemas = new ArrayList<>();
        boolean integridadeOk = true;

        try (java.sql.Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT tc.table_name, tc.constraint_name, " +
                                "kcu.column_name, ccu.table_name AS foreign_table_name " +
                                "FROM information_schema.table_constraints tc " +
                                "JOIN information_schema.key_column_usage kcu " +
                                "  ON tc.constraint_name = kcu.constraint_name " +
                                "JOIN information_schema.constraint_column_usage ccu " +
                                "  ON ccu.constraint_name = tc.constraint_name " +
                                "WHERE tc.constraint_type = 'FOREIGN KEY'")) {

            while (rs.next()) {
                String table = rs.getString("table_name");
                String constraint = rs.getString("constraint_name");
                String column = rs.getString("column_name");
                String foreignTable = rs.getString("foreign_table_name");

                try {
                    // Query para encontrar registros inconsistentes
                    String query = String.format(
                            "SELECT COUNT(*) FROM %s t WHERE NOT EXISTS " +
                                    "(SELECT 1 FROM %s ft WHERE t.%s = ft.id)",
                            table, foreignTable, column);

                    try (java.sql.Statement countStmt = conn.createStatement();
                            ResultSet countRs = countStmt.executeQuery(query)) {

                        if (countRs.next() && countRs.getInt(1) > 0) {
                            Map<String, Object> problema = new HashMap<>();
                            problema.put("tabela", table);
                            problema.put("constraint", constraint);
                            problema.put("coluna", column);
                            problema.put("tabela_referencia", foreignTable);
                            problema.put("registros_inconsistentes", countRs.getInt(1));
                            problemas.add(problema);
                            integridadeOk = false;
                        }
                    }
                } catch (SQLException e) {
                    Map<String, Object> erro = new HashMap<>();
                    erro.put("tabela", table);
                    erro.put("erro", "Falha ao validar: " + e.getMessage());
                    problemas.add(erro);
                    integridadeOk = false;
                }
            }
        } catch (SQLException e) {
            relatorio.put("erro", "Falha ao gerar relatório de integridade: " + e.getMessage());
            return relatorio;
        }

        relatorio.put("integridade_ok", integridadeOk);
        relatorio.put("total_problemas", problemas.size());
        relatorio.put("problemas", problemas);

        return relatorio;
    }

    public List<String> filtrarTabelasRelevantes(String filtroParcial, List<String> ordemCarga,
            Map<String, Set<String>> dependencias) {

        Set<String> tabelasRelevantes = new LinkedHashSet<>();
        Set<String> visitado = new HashSet<>();
        Set<String> ciclo = new HashSet<>();

        if (!filtroParcial.contains(".")) {
            List<String> tabelasDoEsquema = dependencias.keySet().stream()
                    .filter(t -> t.toLowerCase().startsWith(filtroParcial.toLowerCase() + "."))
                    .toList();

            for (String tabela : tabelasDoEsquema) {
                buscarDependencias(tabela, dependencias, tabelasRelevantes, visitado, ciclo);
            }
        } else {
            Optional<String> tabelaCorrespondente = dependencias.keySet().stream()
                    .filter(t -> t.toLowerCase().contains(filtroParcial.toLowerCase()))
                    .findFirst();

            tabelaCorrespondente
                    .ifPresent(t -> buscarDependencias(t, dependencias, tabelasRelevantes, visitado, ciclo));
        }

        return ordemCarga.stream()
                .filter(tabelasRelevantes::contains)
                .collect(Collectors.toList());
    }

    private void buscarDependencias(String tabela, Map<String, Set<String>> dependencias,
            Set<String> tabelasRelevantes, Set<String> visitado, Set<String> ciclo) {

        if (ciclo.contains(tabela)) {

            dependencias.getOrDefault(tabela, new HashSet<>()).clear();

        }
        if (visitado.contains(tabela)) {
            return;
        }

        visitado.add(tabela);
        tabelasRelevantes.add(tabela);

        if (dependencias.containsKey(tabela)) {
            for (String dependente : dependencias.get(tabela)) {
                buscarDependencias(dependente, dependencias, tabelasRelevantes, visitado, ciclo);
            }
        }
    }

    public Map<String, Object> definirParametrosVerificacao(Connection conexaoCloud, Connection conexaoLocal,
            String tabela) throws SQLException {

        Map<String, Object> parametros = new HashMap<String, Object>();

        if (tabela == null) {
            logPublisher.enviarLog("Tabela não especificada.");
            throw new SQLException("Tabela não especificada.");
        }
        
        String pkColumn = obterNomeColunaPK(conexaoCloud, tabela);

        if (pkColumn == null)
            return null;

        long maxCloudId = obterMaxId(conexaoCloud, tabela, pkColumn);
        long maxLocalId = obterMaxId(conexaoLocal, tabela, pkColumn);

        int countLocal = obterQuantidadeRegistro(conexaoLocal, tabela);

        if (maxCloudId == 0)
            return null;

        if (maxLocalId == 0 && countLocal == 0) {
            parametros.put("novo", true);
            parametros.put("existente", false);

        } else {
            parametros.put("novo", false);
            parametros.put("existente", true);

            parametros.put("pkColumn", pkColumn);

        }

        return parametros;
    }

}
