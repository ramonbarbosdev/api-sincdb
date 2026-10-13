package com.api_sincdb.util;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

public final class SyncErrorFormatter {

    private SyncErrorFormatter() {
    }

    public static String formatarErroSql(String etapa, String objeto, SQLException e) {
        String mensagem = e.getMessage() != null ? e.getMessage().trim() : "Erro SQL";
        return montarResumo(etapa, objeto, mensagem, e.getSQLState());
    }

    public static String formatarErroGenerico(String etapa, String objeto, String mensagem, String sqlState) {
        return montarResumo(etapa, objeto, mensagem != null ? mensagem.trim() : "Erro desconhecido", sqlState);
    }

    public static String formatarFalhaTransacao(SQLException e, String etapa, String objeto) {
        if (etapa != null && !etapa.isBlank()) {
            return formatarErroSql(etapa, objeto, e);
        }
        String mensagem = e.getMessage() != null ? e.getMessage().trim() : "Falha na sincronização";
        return montarResumo("Sincronização", objeto, mensagem, e.getSQLState());
    }

    public static String mensagemDeDetalhe(Map<String, String> detalhe) {
        if (detalhe == null) {
            return "";
        }
        String erro = detalhe.get("erro");
        if (erro == null || erro.isBlank()) {
            return "";
        }
        if (erro.contains("Etapa ") && erro.contains("Tabela ")) {
            return erro;
        }
        String tabela = detalhe.get("tabela");
        String acao = detalhe.get("acao");
        if (acao != null && !acao.isBlank()) {
            return montarResumo(acao, tabela, erro, extrairSqlStateDoTexto(erro));
        }
        return erro;
    }

    public static String truncarSqlParaLog(String sql, int maxChars) {
        if (sql == null) {
            return "";
        }
        String compact = sql.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxChars) {
            return compact;
        }
        return compact.substring(0, maxChars) + "…";
    }

    private static String montarResumo(String etapa, String objeto, String mensagem, String sqlState) {
        StringBuilder sb = new StringBuilder();
        if (objeto != null && !objeto.isBlank() && !"desconhecida".equalsIgnoreCase(objeto)) {
            sb.append("Tabela ").append(objeto).append(" · ");
        }
        sb.append("Etapa ").append(etapa != null && !etapa.isBlank() ? etapa : "Sincronização");
        sb.append(": ").append(mensagem);
        if (sqlState != null && !sqlState.isBlank() && !mensagem.contains(sqlState)) {
            sb.append(" (").append(sqlState).append(")");
        }
        String hint = hintParaErro(sqlState, mensagem);
        if (hint != null) {
            sb.append(" — ").append(hint);
        }
        return sb.toString();
    }

    private static String hintParaErro(String sqlState, String mensagem) {
        String msg = mensagem != null ? mensagem.toLowerCase(Locale.ROOT) : "";
        if ("0A000".equals(sqlState) && msg.contains("default")) {
            if (msg.contains("column reference")) {
                return "coluna computada (GENERATED) foi exportada como DEFAULT; verifique colunas como ds_nu_acervo_digitos — use GENERATED ALWAYS AS (...) STORED";
            }
            return "o DEFAULT da coluna usa referência inválida (ex.: outra coluna), o que o PostgreSQL não aceita";
        }
        if ("42P01".equals(sqlState)) {
            return "objeto referenciado não existe no banco de destino";
        }
        if ("42701".equals(sqlState)) {
            return "coluna duplicada na definição da tabela";
        }
        if ("42P07".equals(sqlState)) {
            return "relação (tabela) já existe";
        }
        if ("23505".equals(sqlState)) {
            return "violação de chave única ou primária";
        }
        if ("23503".equals(sqlState)) {
            return "violação de chave estrangeira";
        }
        return null;
    }

    private static String extrairSqlStateDoTexto(String texto) {
        if (texto == null) {
            return null;
        }
        int idx = texto.indexOf("SQLState:");
        if (idx < 0) {
            return null;
        }
        String rest = texto.substring(idx + "SQLState:".length()).trim();
        int pipe = rest.indexOf('|');
        if (pipe >= 0) {
            rest = rest.substring(0, pipe).trim();
        }
        int space = rest.indexOf(' ');
        if (space >= 0) {
            rest = rest.substring(0, space).trim();
        }
        return rest.isEmpty() ? null : rest;
    }
}
