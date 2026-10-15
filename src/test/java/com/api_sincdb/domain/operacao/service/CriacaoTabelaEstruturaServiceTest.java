package com.api_sincdb.domain.operacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CriacaoTabelaEstruturaServiceTest {

    @Test
    void tipoInteiroAutoIncremento_preservaBigint() {
        assertEquals("bigint", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("bigint"));
        assertEquals("bigint", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("INT8"));
        assertEquals("bigint", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("bigserial"));
        assertEquals("bigint", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("SERIAL8"));
    }

    @Test
    void tipoInteiroAutoIncremento_mantemIntegerParaSerialClassico() {
        assertEquals("integer", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("integer"));
        assertEquals("integer", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("int4"));
        assertEquals("integer", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento("serial"));
        assertEquals("integer", CriacaoTabelaEstruturaService.tipoInteiroAutoIncremento(null));
    }
}
