package com.api_sincdb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MetadadosColunaPostgres {

    private MetadadosColunaPostgres() {
    }

    public static Set<String> carregarColunasIdentity(Connection conexao, String schema, String tabela)
            throws SQLException {
        Set<String> resultado = new HashSet<>();

        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND (
                      is_identity = 'YES'
                      OR column_default LIKE 'nextval(%'
                  )
                """;

        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tabela);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(rs.getString("column_name"));
                }
            }
        }

        return resultado;
    }

    public static Map<String, String> carregarExpressoesGeradas(
            Connection conexao,
            String schema,
            String tabela) throws SQLException {
        Map<String, String> expressoes = new HashMap<>();

        String sql = """
                SELECT column_name, generation_expression
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND is_generated = 'ALWAYS'
                """;

        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tabela);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nome = rs.getString("column_name");
                    String expressao = rs.getString("generation_expression");
                    if (nome != null && expressao != null && !expressao.isBlank()) {
                        expressoes.put(nome, expressao.trim());
                    }
                }
            }
        }

        return expressoes;
    }

    public static String clausulaGeradaArmazenada(String expressao) {
        if (expressao == null || expressao.isBlank()) {
            return "";
        }
        return " GENERATED ALWAYS AS (" + expressao.trim() + ") STORED";
    }
}
