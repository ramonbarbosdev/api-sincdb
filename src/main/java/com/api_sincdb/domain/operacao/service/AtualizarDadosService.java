package com.api_sincdb.domain.operacao.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Service
public class AtualizarDadosService {

    @Autowired
    private InsertSqlBuilderService insertSqlBuilderService;

    public List<String> verificarConsistenciaRegistros(Connection conexaoLocal, Connection conexaoCloud, String tabela,
            String pkColumn) throws SQLException {

        DSLContext local = DSL.using(conexaoLocal, SQLDialect.POSTGRES);
        DSLContext cloud = DSL.using(conexaoCloud, SQLDialect.POSTGRES);

        // carrega apenas os IDs
        Set<Long> localIds = new HashSet<>(local
                .select(field(pkColumn))
                .from(table(tabela))
                .fetch()
                .map(rec -> ((Number) rec.get(0)).longValue()));

        Set<Long> cloudIds = new HashSet<>(cloud
                .select(field(pkColumn))
                .from(table(tabela))
                .fetch()
                .map(rec -> ((Number) rec.get(0)).longValue()));

        // falta no cloud → DELETE
        Set<Long> desconhecidos = new HashSet<>(localIds);
        desconhecidos.removeAll(cloudIds);

        // falta no local → INSERT
        Set<Long> extras = new HashSet<>(cloudIds);
        extras.removeAll(localIds);

        List<String> sql = new ArrayList<>();

        sql.addAll(registroDesconhecidoEmLote(conexaoLocal, tabela, desconhecidos, pkColumn));
        sql.addAll(registroExtraEmLote(conexaoCloud, tabela, extras, pkColumn));

        return sql;
    }

    public List<String> registroDesconhecidoEmLote(
            Connection conexaoLocal,
            String tabela,
            Set<Long> idsDesconhecidos,
            String pkColumn) throws SQLException {

        if (idsDesconhecidos.isEmpty())
            return List.of();

        String inClause = idsDesconhecidos.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        String sql = "SELECT * FROM " + tabela + " WHERE " + pkColumn + " IN (" + inClause + ")";

        List<String> deletes = new ArrayList<>();

        try (Statement stmt = conexaoLocal.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                deletes.add("DELETE FROM " + tabela + " WHERE " + pkColumn + " = " + rs.getLong(pkColumn));
            }
        }

        return deletes;
    }

    public List<String> registroExtraEmLote(
            Connection conexaoCloud,
            String tabela,
            Set<Long> idsExtras,
            String pkColumn) throws SQLException {

        if (idsExtras.isEmpty())
            return List.of();

        String inClause = idsExtras.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        String sql = "SELECT * FROM " + tabela + " WHERE " + pkColumn + " IN (" + inClause + ")";

        List<String> inserts = new ArrayList<>();

        try (Statement stmt = conexaoCloud.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                inserts.add(insertSqlBuilderService.construirInsertSQL(tabela, rs));
            }
        }

        return inserts;
    }

}
