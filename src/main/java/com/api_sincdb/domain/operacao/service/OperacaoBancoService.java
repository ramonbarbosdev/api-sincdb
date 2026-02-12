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

import com.api_sincdb.domain.operacao.model.TerminalLog;
import com.api_sincdb.domain.parametro.service.ParametroMasterService;
import com.api_sincdb.websocket.LogPublisher;

@Service
public class OperacaoBancoService {

    @Autowired
    private ProcessoService processoService;

    @Autowired
    private LogPublisher logPublisher;

    @Autowired
    private ParametroMasterService parametroService;

    private boolean grupoEhCritico(String tipo) {
        return switch (tipo) {
            case "Schemas",
                    "Criação de Tabelas",
                    "Alterações",
                    "Chaves Estrangeiras" ->
                true;
            default -> false;
        };
    }

    public void executarQueriesEmLotes(Connection conexao, HashMap<String, List<String>> queries,
            List<Map<String, String>> detalhes) throws IOException {
        try {

            logPublisher.enviarLog(TerminalLog.warn("Iniciando sincronização"));


            int totalQueries = queries.values().stream().mapToInt(List::size).sum();
            AtomicInteger queriesExecutadas = new AtomicInteger(0);

            Map<String, Object> parametros = parametroService.carregarParametros();
            Boolean param = (Boolean) parametros.getOrDefault("PARAM_TOLERAR_ERROS_NAO_CRITICOS", false);
            boolean tolerarErrosNaoCriticos = Boolean.TRUE.equals(param);

            conexao.setAutoCommit(false);

            for (Map.Entry<String, List<String>> grupo : queries.entrySet()) {
                String tipo = grupo.getKey();
                List<String> listaQueries = grupo.getValue();

                boolean continuarEmErro = tolerarErrosNaoCriticos && !grupoEhCritico(tipo);

                executarGrupoDeQueries(
                        conexao,
                        tipo,
                        listaQueries,
                        detalhes,
                        totalQueries,
                        queriesExecutadas,
                        continuarEmErro);

                conexao.commit();

            }

            logPublisher.enviarLog(TerminalLog.done("Sincronização concluída com sucesso"));
            processoService.enviarProgresso("Concluido", 100, "Sincronização concluída com sucesso", null);

        } catch (SQLException e) {
            logPublisher.enviarLog(TerminalLog.error("Falha na transação geral: " + e.getMessage()));

            try {
                conexao.rollback();
            } catch (SQLException ex) {
                logPublisher.enviarLog(TerminalLog.error("Erro ao tentar rollback: " + ex.getMessage()));

            }
        } finally {
            try {
                conexao.setAutoCommit(true);
            } catch (SQLException e) {
                logPublisher.enviarLog(TerminalLog.error("Erro ao reativar autoCommit: " + e.getMessage()));

            }
        }
    }

    private void executarGrupoDeQueries(
            Connection conexao,
            String tipo,
            List<String> queries,
            List<Map<String, String>> detalhes,
            int totalQueries,
            AtomicInteger queriesExecutadas,
            boolean continuarEmErro) throws SQLException, IOException {

        if (queries == null || queries.isEmpty())
            return;

        logPublisher.enviarLog(TerminalLog.info("Executando " + tipo));

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
                logPublisher.enviarLog(TerminalLog.info("Processando " + tipo + ": " + tabela));
            }

            try (java.sql.Statement stmt = conexao.createStatement()) {
                stmt.execute(query);
            } catch (SQLException e) {

                logPublisher.enviarLog(TerminalLog.error("ERRO AO EXECUTAR QUERY (" + tipo + "):"));
                logPublisher.enviarLog(TerminalLog.error(query));

                Map<String, String> criarDetalhe = new LinkedHashMap<>();
                criarDetalhe.put("tabela", tabela);
                criarDetalhe.put("acao", tipo);
                criarDetalhe.put("erro", e.getMessage() + " | SQLState: " + e.getSQLState());
                detalhes.add(criarDetalhe);

                logPublisher.enviarLog(TerminalLog.error(e.getMessage() + " | SQLState: " + e.getSQLState()));


                if (!continuarEmErro) {
                    throw e;
                }

                continue;
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
