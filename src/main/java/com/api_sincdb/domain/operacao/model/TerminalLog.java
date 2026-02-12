package com.api_sincdb.domain.operacao.model;

public class TerminalLog {
    public static String info(String mensagem) {
        return "[INFO] " + mensagem;
    }

    public static String ok(String mensagem) {
        return "[ OK ] " + mensagem;
    }

    public static String warn(String mensagem) {
        return "\n[WARN] " + mensagem;
    }

    public static String skip(String mensagem) {
        return "[SKIP] " + mensagem;
    }

    public static String error(String mensagem) {
        return "[ERROR] " + mensagem;
    }

    public static String done(String mensagem) {
        return "\n[DONE] " + mensagem;
    }

    public static String tabela(String tabela) {
        return "\n[TABLE] " + tabela;
    }

}
