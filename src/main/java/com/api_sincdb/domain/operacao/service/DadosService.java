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

import org.apache.commons.lang3.tuple.Pair;
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
import com.api_sincdb.domain.info.service.SincronizacaoSchemaService;
import com.api_sincdb.domain.operacao.model.EstruturaTabela;
import com.api_sincdb.domain.operacao.model.TabelaDetalhe;
import com.api_sincdb.domain.operacao.model.TerminalLog;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.enums.TipoOperacao;
import com.api_sincdb.helper.EstruturaDadosUtils;
import com.api_sincdb.helper.JwtHelper;
import com.api_sincdb.util.SyncCacheKeys;
import com.api_sincdb.util.ThreadUtils;
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

    @Autowired
    private SincronizacaoSchemaService sincronizacaoSchemaService;

    @Autowired
    private EstruturaDadosUtils estruturaDadosUtils;

    @Autowired
    private JwtHelper jwtHelper;

    // ================================================================
    // FUNCOES PRINCIPAIS
    // ================================================================

    public Map<String, Object> verificarDados(String token, String database, String esquema, String tabela)
            throws Exception {

        Map<String, Object> response = new LinkedHashMap<>();
        List<TabelaDetalhe> detalhes = new ArrayList<>();

        String usuario = jwtHelper.extrairUsuario(token);

        Pair<Connection, Connection> conexoes = estruturaDadosUtils.abrirConexoes(database, token);
        Connection cloud = conexoes.getLeft();
        Connection local = conexoes.getRight();

        try {

            sincronizacaoSchemaService.iniciar(database, esquema, usuario, TipoOperacao.DADOS);

            ThreadUtils.verificarCancelamento();

            HashMap<String, List<String>> querys = obterDadosTabela(database, cloud, local, tabela,
                    detalhes);
            ThreadUtils.verificarCancelamento();

            String cacheKey = SyncCacheKeys.dados(database, esquema, tabela);
            if (querys != null) {
                cacheService.salvarCache(cacheKey, querys);
                response.put("tabelas_afetadas", detalhes);
            } else {
                cacheService.salvarCache(cacheKey, new HashMap<String, List<String>>());
            }

            response.put("sucesso", true);

            sincronizacaoSchemaService.marcarComoDesatualizado(database, esquema, usuario,
                    "Scripts gerados para sincronização.", TipoOperacao.DADOS);

        } catch (InterruptedException e) {
            Thread.interrupted();
            estruturaDadosUtils.finalizarCancelado(database, esquema, usuario, response, e, TipoOperacao.DADOS);
        } catch (SQLException e) {
            estruturaDadosUtils.finalizarErro(database, esquema, usuario, response, e, TipoOperacao.DADOS);
        } catch (Exception e) {
            estruturaDadosUtils.finalizarErro(database, esquema, usuario, response, e, TipoOperacao.DADOS);
        } finally {

            if (cloud != null)
                cloud.close();
            if (local != null)
                local.close();
        }

        return response;
    }

    public Map<String, Object> sincronizarDados(String token, String database, String esquema, String tabela,
            Boolean fl_verificacao) {

        Map<String, Object> response = new HashMap<String, Object>();
        List<Map<String, String>> detalhes = new ArrayList<>();

        String usuario = jwtHelper.extrairUsuario(token);

        try (Connection conexaoLocal = conexaoBanco.abrirConexao(database, TipoConexao.LOCAL, token)) {

            sincronizacaoSchemaService.iniciar(database, esquema, usuario, TipoOperacao.DADOS);

            conexaoLocal.setAutoCommit(false);

            desativarConstraints(conexaoLocal);

            @SuppressWarnings("unchecked")
            HashMap<String, List<String>> querys = cacheService.buscarCache(
                    SyncCacheKeys.dados(database, esquema, tabela), HashMap.class);

            if (querys == null) {
                response.put("sucesso", false);
                response.put("errors", "Nenhuma verificação foi feita previamente.");
                logPublisher.enviarLog("Nenhuma verificação foi feita previamente.");

                return response;
            }

            if (querys.isEmpty()) {
                response.put("sucesso", true);
                response.put("tabelas_afetadas", detalhes);
                response.put("errors", Collections.emptyList());
                ativarConstraints(conexaoLocal);
                sincronizacaoSchemaService.finalizarSucesso(database,
                        esquema, usuario,
                        "Nada a sincronizar para este escopo.",
                        TipoOperacao.DADOS);
                return response;
            }

            operacaoBancoService.executarQueriesEmLotes(conexaoLocal, querys, detalhes);

            // To:do - construir uma tela a consultar a integridade dos dados
            // validarIntegridadeDados(conexaoLocal, response);

            List<String> listaErro = new ArrayList<>();
            for (Map<String, String> erro : detalhes) {
                String mensagem = erro.get("erro");
                if (mensagem != null && !mensagem.isBlank()) {
                    listaErro.add(mensagem);
                }
            }

            boolean possuiErros = !listaErro.isEmpty();
            response.put("sucesso", !possuiErros);
            response.put("tabelas_afetadas", detalhes);
            response.put("errors", listaErro);

            ativarConstraints(conexaoLocal);

            if (possuiErros) {
                sincronizacaoSchemaService.finalizarErro(database,
                        esquema,
                        usuario,
                        "Sincronização com erros: " + listaErro.get(0), TipoOperacao.DADOS);
            } else {
                sincronizacaoSchemaService.finalizarSucesso(database,
                        esquema, usuario,
                        "Sincronização concluída. Total de tabelas processadas: " + detalhes.size(),
                        TipoOperacao.DADOS);
            }

        } catch (Exception e) {

            sincronizacaoSchemaService.finalizarErro(database,
                    esquema,
                    usuario,
                    "Erro ao sincronizar scripts: " + e.getMessage(), TipoOperacao.DADOS);

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

        //inicio - Feedback Usuario
        int totalTabelas = tabelas.size();
        AtomicInteger tabelasProcessadas = new AtomicInteger(0);
        processoService.enviarProgresso("Iniciando", 0, "Iniciando processamento de " + totalTabelas + " tabelas.",
                null);
        logPublisher.enviarLog(
                TerminalLog.warn("Iniciando processamento de " + totalTabelas + " tabelas"));
        //fim - Feedback Usuario


        for (String itemTabela : tabelas) {

            //inicio - Feedback Usuario
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("Cancelado");
            logPublisher.enviarLog(
                    TerminalLog.tabela(itemTabela));
            int progresso = (int) ((tabelasProcessadas.incrementAndGet() / (double) totalTabelas) * 100);
            processoService.enviarProgresso("Processando", progresso, "Processando tabela: " + itemTabela, itemTabela);
            //fim - Feedback Usuario

            Map<String, Object> parametros = definirParametrosVerificacao(conexaoCloud, conexaoLocal, itemTabela);

            if (parametros != null) {
                TabelaDetalhe infoDetalhe = new TabelaDetalhe();

                if ((Boolean) parametros.get("novo")) {

                    logPublisher.enviarLog(
                            TerminalLog.info("Gerando carga inicial"));

                    List<String> query = criarInsertsDadosService.cargaInicialCompleta(conexaoCloud, conexaoLocal,
                            itemTabela);

                    if (query.size() > 0) {
                        infoDetalhe.setTabela(itemTabela);
                        infoDetalhe.setAcao("Inserção");
                        infoDetalhe.setLinhaInseridas(query.size());
                        detalhes.add(infoDetalhe);
                        criacaoDados.addAll(query);

                        logPublisher.enviarLog(
                                TerminalLog.ok(query.size() + " registros inseridos"));
                    }

                } else if ((Boolean) parametros.get("existente")) {

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

                        logPublisher.enviarLog(
                                TerminalLog.ok(query.size() + " registros atualizados"));
                    }

                } else {
                    logPublisher.enviarLog(
                            TerminalLog.skip("Nenhum registro necessário"));

                }

                String querySeq = atualizarSequencias(conexaoLocal, itemTabela);
                if (querySeq != null) {
                    infoDetalhe.setTabela(itemTabela);
                    infoDetalhe.setAcao("Atualização Sequência");
                    infoDetalhe.setLinhaInseridas(1);
                    infoDetalhe.setQuerys(querySeq);
                    detalhes.add(infoDetalhe);
                    criacaoAtualizacaoSeq.add(querySeq);

                    logPublisher.enviarLog(
                            TerminalLog.ok("Sequência atualizada"));

                }
            }

        }

        //inicio - Feedback Usuario
        processoService.enviarProgresso("Concluido", 100, "Verificação concluída.", null);
        logPublisher.enviarLog(
                TerminalLog.done("Verificação concluída."));
        //fim - Feedback Usuario

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

            String query = """
                    select
                        n.nspname as table_schema,
                        t.relname as table_name,
                        a.attname as column_name,
                        sn.nspname as sequence_schema,
                        concat(sn.nspname,'.',s.relname)  as sequence_name
                    from pg_class s
                    join pg_depend d on d.objid = s.oid
                    join pg_class t on d.refobjid = t.oid
                    join pg_attribute a on a.attrelid = t.oid and a.attnum = d.refobjsubid
                    join pg_namespace sn on sn.oid = s.relnamespace
                    join pg_namespace n on n.oid = t.relnamespace
                    where s.relkind = 'S'
                    and t.relname = ?
                    and n.nspname = ?;
                    """;
            ;

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
