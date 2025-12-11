package com.api_sincdb.domain.operacao.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.websocket.LogPublisher;

@Service
public class OperacaoBancoService {

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private LogPublisher logPublisher;

    public void executarQueriesEmLotes(Connection conexao, HashMap<String, List<String>> queries,
            List<Map<String, String>> detalhes) throws IOException {
        try {
            int totalQueries = queries.values().stream().mapToInt(List::size).sum();
            AtomicInteger queriesExecutadas = new AtomicInteger(0);

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

    private void executarGrupoDeQueries(
            Connection conexao,
            String tipo,
            List<String> queries,
            List<Map<String, String>> detalhes,
            int totalQueries,
            AtomicInteger queriesExecutadas) throws SQLException, IOException {

        if (queries == null || queries.isEmpty())
            return;

        logPublisher.enviarLog("\n=== Executando grupo: " + tipo + " ===");

        AtomicInteger ultimoProgressoEnviado = new AtomicInteger(-1);

        for (String query : queries) {

            String tabela = extrairNomeTabelaDaQuery(query);

            int progressoAtual = (int) ((queriesExecutadas.incrementAndGet() / (double) totalQueries) * 100);

            // ENVIA WEBSOCKET APENAS SE O PERCENTUAL MUDAR
            if (progressoAtual != ultimoProgressoEnviado.get()) {
                processoService.enviarProgresso("Processando", progressoAtual,
                        "Processando " + tipo + ": " + tabela, tabela);
                ultimoProgressoEnviado.set(progressoAtual);
            }

            // Envia log somente a cada 200 registros
            if (queriesExecutadas.get() % 200 == 0) {
                logPublisher.enviarLog("Processando " + tipo + ": " + tabela);
            }

            try (java.sql.Statement stmt = conexao.createStatement()) {
                stmt.execute(query);
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

        return "desconhecida";
    }

}
