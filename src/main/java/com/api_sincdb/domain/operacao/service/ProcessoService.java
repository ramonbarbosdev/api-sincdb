package com.api_sincdb.domain.operacao.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProcessoService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Map<String, AtomicBoolean> processosCancelados = new ConcurrentHashMap<>();

    public void iniciarProcesso(String processoId) {
        processosCancelados.put(processoId, new AtomicBoolean(false));
    }

    public void cancelarProcesso(String processoId) {
        processosCancelados.computeIfPresent(processoId, (id, flag) -> {
            flag.set(true);
            return flag;
        });
    }

    public boolean isCancelado(String processoId) {
        return processosCancelados.getOrDefault(processoId, new AtomicBoolean(false)).get();
    }

    public void finalizarProcesso(String processoId) {
        processosCancelados.remove(processoId);
    }

    public void enviarProgresso(String status, int progresso, String mensagem, String tabelaAtual) {

        Map<String, Object> progressoMsg = new HashMap<>();
        progressoMsg.put("status", status);
        progressoMsg.put("progresso", progresso);
        progressoMsg.put("mensagem", mensagem);
        progressoMsg.put("tabelaAtual", tabelaAtual);
        progressoMsg.put("timestamp", System.currentTimeMillis());

        // System.out.println("Enviando progresso para /topic/sync/progress");
        messagingTemplate.convertAndSend("/topic/sync/progress", progressoMsg);
    }

    public static String progress(int atual, int total) {

        int percent = (int) ((atual / (double) total) * 100);

        int bars = percent / 5;

        String bar = "█".repeat(bars) + " ".repeat(20 - bars);

        return String.format("[PROG ] [%s] %d%% (%d/%d)",
                bar, percent, atual, total);
    }

}
