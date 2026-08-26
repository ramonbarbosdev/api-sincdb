package com.api_sincdb.util;

import org.springframework.stereotype.Component;

/**
 * Garante que verify/sync não se sobreponham (ProcessoManager é global no servidor).
 */
@Component
public class SyncExecutionGuard {

    private final Object lock = new Object();

    public void run(Runnable task) {
        synchronized (lock) {
            task.run();
        }
    }
}
