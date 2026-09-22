package com.api_sincdb.domain.forum.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.forum.dto.ForumStatusUpdateRequest;
import com.api_sincdb.domain.forum.model.ForumSystemStatus;
import com.api_sincdb.domain.forum.repository.ForumSystemStatusRepository;
import com.api_sincdb.enums.ForumEstadoGeral;

@Service
public class ForumStatusService {

    @Autowired
    private ForumSystemStatusRepository repository;

    @Autowired
    private ForumContextHelper context;

    public ForumSystemStatus obter() {
        return repository.findById(ForumSystemStatus.GLOBAL_ID).orElseGet(this::criarPadrao);
    }

    public ForumSystemStatus atualizar(ForumStatusUpdateRequest request) {
        ForumSystemStatus status = obter();
        if (request.getEstadoGeral() != null) {
            status.setEstadoGeral(request.getEstadoGeral());
        }
        if (request.getTitulo() != null) {
            status.setTitulo(request.getTitulo().trim());
        }
        if (request.getMensagem() != null) {
            status.setMensagem(request.getMensagem().trim());
        }
        if (request.getProximaVersao() != null) {
            status.setProximaVersao(request.getProximaVersao().trim());
        }
        if (request.getPrevisaoRelease() != null) {
            status.setPrevisaoRelease(request.getPrevisaoRelease().trim());
        }
        status.setAtualizadoEm(LocalDateTime.now());
        status.setAtualizadoPor(context.nomeExibicao(context.buscarUsuarioAtual().orElse(null), context.idUsuarioAtual()));
        return repository.save(status);
    }

    private ForumSystemStatus criarPadrao() {
        ForumSystemStatus status = new ForumSystemStatus();
        status.setId(ForumSystemStatus.GLOBAL_ID);
        status.setEstadoGeral(ForumEstadoGeral.OPERACIONAL);
        status.setTitulo("SyncDB operacional");
        status.setMensagem("Todos os serviços estão funcionando normalmente.");
        status.setAtualizadoEm(LocalDateTime.now());
        return repository.save(status);
    }
}
