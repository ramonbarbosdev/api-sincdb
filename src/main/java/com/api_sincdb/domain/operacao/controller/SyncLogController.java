package com.api_sincdb.domain.operacao.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/sync")
public class SyncLogController {

    private final Sinks.Many<String> logSink =
            Sinks.many().multicast().onBackpressureBuffer();

    @GetMapping(value = "/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamLogs() {

        // Envia um "ping" inicial para manter a conexão aberta
        return Flux.concat(
            Flux.just("connected"),
            logSink.asFlux()
        );
    }

    public void enviarLog(String msg) {
        logSink.tryEmitNext(msg);
    }
}