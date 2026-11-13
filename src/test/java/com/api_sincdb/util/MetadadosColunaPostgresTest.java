package com.api_sincdb.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MetadadosColunaPostgresTest {

    @Test
    void clausulaGeradaArmazenada_formataExpressao() {
        String clausula = MetadadosColunaPostgres.clausulaGeradaArmazenada(
                "regexp_replace(ds_nu_acervo::text, '\\D', '', 'g')");
        assertEquals(
                " GENERATED ALWAYS AS (regexp_replace(ds_nu_acervo::text, '\\D', '', 'g')) STORED",
                clausula);
    }

    @Test
    void clausulaGeradaArmazenada_retornaVazioQuandoSemExpressao() {
        assertTrue(MetadadosColunaPostgres.clausulaGeradaArmazenada(null).isEmpty());
        assertTrue(MetadadosColunaPostgres.clausulaGeradaArmazenada("   ").isEmpty());
    }
}
