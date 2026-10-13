package com.api_sincdb.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.api_sincdb.websocket.LogPublisher;

@Component
public class UtilsSync {

    @Autowired
    private LogPublisher logPublisher;

    public String extrairSchema(String nomeTabela) {
        if (nomeTabela.contains("."))
            return nomeTabela.split("\\.")[0];
        return null;
    }

    public String extrairTabela(String nomeTabela) {
        if (nomeTabela.contains("."))
            return nomeTabela.split("\\.")[1];
        return null;
    }

    public void anexarErrosDeDetalhes(
            Map<String, Object> response,
            List<Map<String, String>> detalhes,
            Exception e) {
        List<String> listaErro = new ArrayList<>();
        List<Map<String, String>> comErro = new ArrayList<>();
        if (detalhes != null) {
            for (Map<String, String> item : detalhes) {
                String msg = SyncErrorFormatter.mensagemDeDetalhe(item);
                if (msg != null && !msg.isBlank()) {
                    listaErro.add(msg);
                    comErro.add(item);
                }
            }
        }
        if (listaErro.isEmpty()) {
            String fallback = e.getMessage() != null ? e.getMessage() : "Falha na sincronização";
            listaErro.add(fallback);
        }
        response.put("errors", listaErro);
        response.put("tabelas_afetadas", comErro.isEmpty() && detalhes != null ? detalhes : comErro);
    }

    public void tratarErroSincronizacao(Map<String, Object> response, Exception e) {
        tratarErroSincronizacao(response, e, null);
    }

    public void tratarErroSincronizacao(
            Map<String, Object> response,
            Exception e,
            List<Map<String, String>> detalhes) {
        if (detalhes != null) {
            anexarErrosDeDetalhes(response, detalhes, e);
        }

        logPublisher.enviarLog(e.getMessage());

        String errorType = e.getClass().getSimpleName();
        String details = e.getMessage();
        Object errors = response.get("errors");
        if (errors instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            details = String.valueOf(list.get(0));
        }

        response.put("sucesso", false);
        response.put("erro", errorType);
        response.put("error", "Erro durante sincronização");
        response.put("detalhes", details);
    }

    public void tratarErroCancelamento(Map<String, Object> response, Exception e) {
        response.put("sucesso", false);
        response.put("message", "Processo cancelado.");
    }
}
