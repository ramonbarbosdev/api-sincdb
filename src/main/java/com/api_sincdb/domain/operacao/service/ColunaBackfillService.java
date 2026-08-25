package com.api_sincdb.domain.operacao.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.model.TerminalLog;
import com.api_sincdb.util.UtilsSync;
import com.api_sincdb.websocket.LogPublisher;

@Service
public class ColunaBackfillService {

    private static final int BATCH_SIZE = 500;

    @Autowired
    private UtilsSync utilsSync;

    @Autowired
    private LogPublisher logPublisher;

    @Autowired
    private ProcessoService processoService;

    public void preencherColunasEApplicarNotNull(
            Connection conexaoCloud,
            Connection conexaoLocal,
            List<String> pendentes,
            List<Map<String, String>> detalhes,
            int totalQueries,
            AtomicInteger queriesExecutadas) throws SQLException, IOException {

        if (pendentes == null || pendentes.isEmpty()) {
            return;
        }

        logPublisher.enviarLog(TerminalLog.info(
                "Preenchendo colunas a partir do cloud antes de aplicar NOT NULL"));

        for (String marcador : pendentes) {
            ColunaPendenteRef ref = parseMarcador(marcador);
            if (ref == null) {
                continue;
            }

            int progresso = (int) ((queriesExecutadas.incrementAndGet() / (double) totalQueries) * 100);
            processoService.enviarProgresso(
                    "Processando",
                    progresso,
                    "Preenchendo " + ref.tabela + "." + ref.coluna,
                    ref.tabela);

            try {
                int preenchidos = preencherColuna(conexaoCloud, conexaoLocal, ref.tabela, ref.coluna);
                conexaoLocal.commit();

                logPublisher.enviarLog(TerminalLog.ok(
                        "Backfill " + ref.tabela + "." + ref.coluna + ": " + preenchidos + " valor(es)"));

                aplicarNotNullSePossivel(conexaoLocal, ref.tabela, ref.coluna);
                conexaoLocal.commit();
            } catch (SQLException e) {
                try {
                    conexaoLocal.rollback();
                } catch (SQLException rollbackEx) {
                    logPublisher.enviarLog(TerminalLog.error(
                            "Erro ao rollback após backfill: " + rollbackEx.getMessage()));
                }

                Map<String, String> detalhe = new LinkedHashMap<>();
                detalhe.put("tabela", ref.tabela);
                detalhe.put("acao", "Preenchimento de Colunas");
                detalhe.put("erro", e.getMessage() + " | SQLState: " + e.getSQLState());
                detalhes.add(detalhe);

                logPublisher.enviarLog(TerminalLog.error(
                        "Falha ao preencher " + ref.tabela + "." + ref.coluna + ": " + e.getMessage()));
            }
        }
    }

    private int preencherColuna(
            Connection conexaoCloud,
            Connection conexaoLocal,
            String tabela,
            String coluna) throws SQLException {

        String pk = obterNomeColunaPK(conexaoLocal, tabela);
        if (pk == null) {
            throw new SQLException(
                    "Tabela " + tabela + " sem chave primária — impossível copiar valores do cloud");
        }

        String selectSql = "SELECT " + pk + ", " + coluna + " FROM " + tabela;
        String updateSql = "UPDATE " + tabela + " SET " + coluna + " = ? WHERE " + pk + " = ? AND "
                + coluna + " IS NULL";

        int preenchidos = 0;

        try (PreparedStatement select = conexaoCloud.prepareStatement(selectSql);
                ResultSet rs = select.executeQuery();
                PreparedStatement update = conexaoLocal.prepareStatement(updateSql)) {

            while (rs.next()) {
                Object valor = rs.getObject(2);
                if (valor == null) {
                    continue;
                }

                update.setObject(1, valor);
                update.setObject(2, rs.getObject(1));
                update.addBatch();
                preenchidos++;

                if (preenchidos % BATCH_SIZE == 0) {
                    update.executeBatch();
                }
            }

            if (preenchidos % BATCH_SIZE != 0) {
                update.executeBatch();
            }
        }

        return preenchidos;
    }

    private void aplicarNotNullSePossivel(
            Connection conexaoLocal,
            String tabela,
            String coluna) throws SQLException, IOException {

        String countSql = "SELECT COUNT(*) FROM " + tabela + " WHERE " + coluna + " IS NULL";

        try (PreparedStatement pst = conexaoLocal.prepareStatement(countSql);
                ResultSet rs = pst.executeQuery()) {
            if (rs.next() && rs.getLong(1) > 0) {
                long nulos = rs.getLong(1);
                logPublisher.enviarLog(TerminalLog.warn(
                        "NOT NULL adiado em " + tabela + "." + coluna
                                + ": " + nulos + " linha(s) ainda nulas após backfill"));
                return;
            }
        }

        String sql = String.format(
                "ALTER TABLE %s ALTER COLUMN %s SET NOT NULL;",
                tabela,
                coluna);

        try (java.sql.Statement stmt = conexaoLocal.createStatement()) {
            stmt.execute(sql);
        }

        logPublisher.enviarLog(TerminalLog.ok("NOT NULL aplicado em " + tabela + "." + coluna));
    }

    private ColunaPendenteRef parseMarcador(String marcador) {
        if (marcador == null || marcador.isBlank()) {
            return null;
        }

        String raw = marcador.trim();
        if (raw.startsWith("BACKFILL ")) {
            raw = raw.substring("BACKFILL ".length()).trim();
        }

        int pipe = raw.indexOf('|');
        if (pipe > 0) {
            return new ColunaPendenteRef(raw.substring(0, pipe), raw.substring(pipe + 1));
        }

        int lastDot = raw.lastIndexOf('.');
        if (lastDot > 0 && lastDot < raw.length() - 1) {
            return new ColunaPendenteRef(raw.substring(0, lastDot), raw.substring(lastDot + 1));
        }

        return null;
    }

    private String obterNomeColunaPK(Connection conexao, String tabela) throws SQLException {
        String schema = utilsSync.extrairSchema(tabela);
        String nomeTabela = utilsSync.extrairTabela(tabela);

        DatabaseMetaData metaData = conexao.getMetaData();
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, nomeTabela)) {
            if (rs.next()) {
                return rs.getString("COLUMN_NAME");
            }
            return null;
        }
    }

    private static final class ColunaPendenteRef {
        final String tabela;
        final String coluna;

        ColunaPendenteRef(String tabela, String coluna) {
            this.tabela = tabela;
            this.coluna = coluna;
        }
    }
}
