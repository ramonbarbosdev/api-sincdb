package com.api_sincdb.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogPublisher {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void enviarLog(String mensagem) {
        System.out.println(mensagem);
        messagingTemplate.convertAndSend("/topic/logs", mensagem);
    }
}