package com.api_sincdb.domain.operacao.service;

import java.beans.Statement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.Table;
import org.jooq.conf.ParamType;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.SQLDataType;
import org.jooq.util.postgres.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.websocket.LogPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OperacaoBancoService {

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private LogPublisher logPublisher;

    @Autowired
    private InsertSqlBuilderService insertSqlBuilderService;

    public List<String> registroDesconhecido(Connection connection, String tabela, Long id, String pkColumn) {
        DSLContext dsl = DSL.using(connection);

        Table<Record> tabelaRecord = DSL.table(tabela);

        List<String> queryList = new ArrayList<>();

        String deleteQuery = dsl.delete(tabelaRecord)
                .where(DSL.field(pkColumn).eq(id))
                .getSQL(ParamType.INLINED)
                .toString();

        queryList.add(deleteQuery);
        // queryList.add(";\n");

        return queryList;

    }

    public List<String> registroExtra(Connection conexaoLocal, Connection conexaoCloud, String tabela, Long id,
            String pkColumn) throws SQLException {
        List<String> queryList = new ArrayList<>();

        String sql = "SELECT * FROM " + tabela + " WHERE " + pkColumn + " = ?";
        try (PreparedStatement stmt = conexaoCloud.prepareStatement(sql)) {
            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String insertSQL = insertSqlBuilderService.construirInsertSQL(tabela, rs);
                    queryList.add(insertSQL);
                } else {
                    throw new SQLException("Nenhum dado encontrado na tabela '" + tabela + "' com ID " + id);
                }
            }
        }

        return queryList;
    }

    public void executarQueriesEmLotes(Connection conexao, HashMap<String, List<String>> queries,
            List<Map<String, String>> detalhes) throws IOException {
        try {
            int totalQueries = queries.values().stream().mapToInt(List::size).sum();
            AtomicInteger queriesExecutadas = new AtomicInteger(0);

            // Deixar o commit manual
            conexao.setAutoCommit(false);

            for (Map.Entry<String, List<String>> grupo : queries.entrySet()) {
                String tipo = grupo.getKey();
                List<String> listaQueries = grupo.getValue();

                executarGrupoDeQueries(conexao, tipo, listaQueries, detalhes, totalQueries, queriesExecutadas);
                conexao.commit();

            }

            processoService.enviarProgresso("Concluido", 100, "Sincronização concluída com sucesso", null);
            logPublisher.enviarLog("Sincronização concluída com sucesso");

        } catch (SQLException e) {
            logPublisher.enviarLog("Falha na transação geral: " + e.getMessage());

            try {
                conexao.rollback();
            } catch (SQLException ex) {
                logPublisher.enviarLog("Erro ao tentar rollback: " + ex.getMessage());

            }
        } finally {
            try {
                conexao.setAutoCommit(true);
            } catch (SQLException e) {
                logPublisher.enviarLog("Erro ao reativar autoCommit: " + e.getMessage());

            }
        }
    }

    private void executarGrupoDeQueries(Connection conexao, String tipo, List<String> queries,
            List<Map<String, String>> detalhes, int totalQueries, AtomicInteger queriesExecutadas)
            throws SQLException, IOException {
        if (queries == null || queries.isEmpty())
            return;

        logPublisher.enviarLog("\n=== Executando grupo: " + tipo + " ===");

        for (String query : queries) {
            String tabela = extrairNomeTabelaDaQuery(query);

            int progressoAtual = (int) ((queriesExecutadas.incrementAndGet() / (double) totalQueries) * 100);
            processoService.enviarProgresso("Processando", progressoAtual, "Processando " + tipo + ": " + tabela,
                    tabela);
            logPublisher.enviarLog("Processando " + tipo + ": " + tabela);

            try {
                try (java.sql.Statement stmt = conexao.createStatement()) {
                    stmt.execute(query);
                }
            } catch (SQLException e) {
                Map<String, String> criarDetalhe = new LinkedHashMap<>();
                criarDetalhe.put("tabela", tabela);
                criarDetalhe.put("acao", tipo);
                criarDetalhe.put("erro", e.getMessage() + " | SQLState: " + e.getSQLState());
                detalhes.add(criarDetalhe);
                logPublisher.enviarLog(e.getMessage() + " | SQLState: " + e.getSQLState());

                throw e;
            }
        }
        conexao.commit();
    }

    public String extrairNomeTabelaDaQuery(String query) {
        query = query.trim().toUpperCase();

        String patternCreate = "CREATE TABLE IF NOT EXISTS ([\\w\\.]+)";
        String patternCreateSimple = "CREATE TABLE ([\\w\\.]+)";
        String patternAlter = "ALTER TABLE ([\\w\\.]+)";
        String patternForeign = "ALTER TABLE ONLY ([\\w\\.]+)";
        String patternInsert = "INSERT INTO ([\\w\\.]+)";

        List<String> patterns = Arrays.asList(patternCreate, patternCreateSimple, patternAlter, patternForeign,
                patternInsert);

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "desconhecida"; // Caso não consiga identificar
    }

    public List<String> cargaInicialCompleta(Connection conexaoCloud, Connection conexaoLocal, String tabela)
            throws SQLException {
        // Map para armazenar as instruções SQL (cache)
        List<String> sqlCache = new ArrayList<>();

        final int BATCH_SIZE = 1000;
        final int PAGE_SIZE = 50000;
        long offset = 0;

        try (java.sql.Statement cloudStmt = conexaoCloud.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)) {
            cloudStmt.setFetchSize(BATCH_SIZE);

            while (true) {
                String query = String.format("SELECT * FROM %s ORDER BY 1 LIMIT %d OFFSET %d", tabela, PAGE_SIZE,
                        offset);

                try (ResultSet rs = cloudStmt.executeQuery(query)) {
                    if (rs.isBeforeFirst()) {
                        // Coletar os registros e construir as instruções SQL
                        while (rs.next()) {
                            // Supondo que a primeira coluna seja a chave (id)
                            String id = rs.getString(1);
                            // Construir a instrução SQL
                            String sql = insertSqlBuilderService.construirInsertSQL(tabela, rs);
                            // Armazenar a instrução SQL no Map
                            sqlCache.add(sql);
                        }

                        // Verificar se terminou a paginação
                        if (sqlCache.size() < PAGE_SIZE) {
                            break;
                        }

                        offset += PAGE_SIZE;
                    } else {
                        break;
                    }
                }
            }

        } catch (BatchUpdateException e) {
            conexaoLocal.rollback();
            handleBatchUpdateException(e, tabela);
        } catch (SQLException e) {
            conexaoLocal.rollback();
            throw e;
        }

        // Retornar o Map com todas as instruções SQL
        return sqlCache;
    }
  
    // Método para tratar exceções de lote
    private void handleBatchUpdateException(BatchUpdateException e, String tabela) {
        logPublisher.enviarLog("Erro durante a execução do lote: " + e.getMessage());

        SQLException nextException = e.getNextException();
        while (nextException != null) {
            if (nextException.getMessage().contains("duplicate key value violates unique constraint")) {
                logPublisher.enviarLog("Erro: Chave duplicada detectada. Registro já existe na tabela " + tabela + ".");

                throw new RuntimeException(
                        "Erro: Chave duplicada detectada. Registro já existe na tabela " + tabela + ".");
            } else {
                logPublisher.enviarLog("Outro erro SQL: " + nextException.getMessage());
                System.err.println("Outro erro SQL: " + nextException.getMessage());
            }
            nextException = nextException.getNextException();
        }
        logPublisher.enviarLog("Falha ao executar batch");

        throw new RuntimeException("Falha ao executar batch", e);
    }

}
