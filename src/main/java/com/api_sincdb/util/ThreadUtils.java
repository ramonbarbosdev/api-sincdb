package com.api_sincdb.util;

import com.api_sincdb.excecoes.ProcessoCanceladoException;

public class ThreadUtils {
    private ThreadUtils() {
    }

    public static void verificarCancelamento() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new ProcessoCanceladoException();
        }
    }
}
