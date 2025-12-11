package com.api_sincdb.domain.operacao.service;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.websocket.LogPublisher;

@Service
public class CriarInsertsDadosService {

    @Autowired
    private InsertSqlBuilderService insertSqlBuilderService;

    @Autowired
    private LogPublisher logPublisher;

    public List<String> cargaInicialCompleta(Connection conexaoCloud, Connection conexaoLocal, String tabela)
            throws SQLException {

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

                        while (rs.next()) {

                            String sql;

                            try {
                                sql = insertSqlBuilderService.construirInsertSQL(tabela, rs);
                            } catch (Exception ex) {
                                logPublisher.enviarLog("Erro ao construir SQL para tabela " + tabela + ": " + ex);
                                throw new SQLException("Falha ao gerar INSERT da tabela " + tabela, ex);
                            }

                            sqlCache.add(sql);
                        }

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
            logPublisher.enviarLog("Erro durante a execução do lote: " + e);
            throw e;
        }

        return sqlCache;
    }

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
