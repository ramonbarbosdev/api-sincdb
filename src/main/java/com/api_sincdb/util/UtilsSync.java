package com.api_sincdb.util;

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

    public void tratarErroSincronizacao(Map<String, Object> response, Exception e) {
        logPublisher.enviarLog( e.getMessage());

        String errorType = e.getClass().getSimpleName();
        String details = e.getMessage();

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
