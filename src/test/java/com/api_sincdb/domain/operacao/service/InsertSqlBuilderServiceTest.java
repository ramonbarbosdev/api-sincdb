package com.api_sincdb.domain.operacao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InsertSqlBuilderServiceTest {

    @Test
    void indicesColunasInclusas_omiteColunasGeradas() throws SQLException {
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(meta.getColumnCount()).thenReturn(3);
        when(meta.getColumnName(1)).thenReturn("id");
        when(meta.getColumnName(2)).thenReturn("ds_nu_acervo_digitos");
        when(meta.getColumnName(3)).thenReturn("nm_acervo");

        List<Integer> indices = InsertSqlBuilderService.indicesColunasInclusas(
                meta,
                Set.of("ds_nu_acervo_digitos"));

        assertEquals(List.of(1, 3), indices);
    }
}
