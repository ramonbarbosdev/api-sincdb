package com.api_sincdb.domain.sql.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SqlSecurityValidatorTest {

    private final SqlSecurityValidator validator = new SqlSecurityValidator();

    @Test
    void devePermitirSelectComLimit() {
        String sql = validator.validate("SELECT * FROM public.usuario LIMIT 100;");

        assertEquals("SELECT * FROM public.usuario LIMIT 100", sql);
    }

    @Test
    void deveBloquearComandoDiferenteDeSelect() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validate("UPDATE usuario SET nome = 'Teste' LIMIT 1"));
    }

    @Test
    void deveBloquearMultiplasInstrucoes() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validate("SELECT * FROM usuario LIMIT 1; DROP TABLE usuario;"));
    }

    @Test
    void deveExigirLimit() {
        assertThrows(ResponseStatusException.class,
                () -> validator.validate("SELECT * FROM usuario"));
    }
}
