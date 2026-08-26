package com.api_sincdb.util;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class ProcessoManager {

    private Thread threadAtual;
    private volatile boolean executando = false;

    public synchronized void iniciarProcesso(Runnable tarefa) throws InterruptedException {
        while (executando) {
            Thread.sleep(50);
        }

        executando = true;

        threadAtual = new Thread(() -> {
            try {
                tarefa.run();
            } finally {
                executando = false;
            }
        });

        threadAtual.start();
    }

    public synchronized void cancelarProcesso() {
        if (threadAtual != null && threadAtual.isAlive()) {
            threadAtual.interrupt();
        }
    }

    public boolean isExecutando() {
        return executando;
    }
}
