package com.api_sincdb.domain.sql.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.domain.sql.dto.SqlRiskResult;

class SqlRiskAnalyzerTest {

    private final SqlRiskAnalyzer analyzer = new SqlRiskAnalyzer();

    @Test
    void deveClassificarSelectSemLimitComoLow() {
        SqlRiskResult result = analyzer.analyze("SELECT * FROM public.usuario;");

        assertEquals("LOW", result.getRiskLevel());
        assertFalse(result.isRequiresConfirmation());
        assertEquals("SELECT * FROM public.usuario", result.getSql());
    }

    @Test
    void deveClassificarCteSelectComoLow() {
        SqlRiskResult result = analyzer.analyze("""
                WITH parametros AS (
                    SELECT 2026::int AS dt_ano
                )
                SELECT *
                FROM parametros
                """);

        assertEquals("LOW", result.getRiskLevel());
        assertFalse(result.isRequiresConfirmation());
        assertEquals("with", result.getCommand());
    }

    @Test
    void deveClassificarUpdateComWhereComoMedium() {
        SqlRiskResult result = analyzer.analyze("UPDATE usuario SET nome = 'Teste' WHERE id = 1");

        assertEquals("MEDIUM", result.getRiskLevel());
        assertFalse(result.isRequiresConfirmation());
    }

    @Test
    void deveClassificarUpdateSemWhereComoHigh() {
        SqlRiskResult result = analyzer.analyze("UPDATE usuario SET ativo = false");

        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.isRequiresConfirmation());
    }

    @Test
    void deveClassificarDropComoCritical() {
        SqlRiskResult result = analyzer.analyze("DROP TABLE usuario");

        assertEquals("CRITICAL", result.getRiskLevel());
        assertTrue(result.isRequiresConfirmation());
    }

    @Test
    void deveBloquearMultiplasInstrucoesPerigosas() {
        assertThrows(ResponseStatusException.class,
                () -> analyzer.analyze("SELECT * FROM usuario; DROP TABLE usuario;"));
    }

    @Test
    void devePermitirSqlSelectLongoDentroDoLimite() {
        StringBuilder sql = new StringBuilder("SELECT * FROM usuario WHERE id IN (");
        for (int i = 0; i < 3000; i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append(i);
        }
        sql.append(")");

        SqlRiskResult result = analyzer.analyze(sql.toString());

        assertEquals("LOW", result.getRiskLevel());
        assertFalse(result.isRequiresConfirmation());
    }
}
