package com.api_sincdb.excecoes;

public class ProcessoCanceladoException extends RuntimeException {
    public ProcessoCanceladoException() {
        super("Processo cancelado pelo usuário.");
    }
}
