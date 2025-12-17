package com.api_sincdb.domain.info.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.info.model.SincronizacaoSchema;
import com.api_sincdb.domain.info.repository.SincronizacaoSchemaRepository;
import com.api_sincdb.enums.StatusSincronizacao;
import com.api_sincdb.enums.TipoOperacao;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import io.jsonwebtoken.JwtHandler;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class SincronizacaoSchemaService {

    @Autowired
    private SincronizacaoSchemaRepository repository;


    // -------------------------------------------------------
    // INICIAR PROCESSO
    // -------------------------------------------------------
    public SincronizacaoSchema iniciar(String baseNome, String schema, String usuario, TipoOperacao tipo) {

        SincronizacaoSchema sync = repository
                .findByBaseNomeAndSchemaNomeAndUsuarioAndOperacao(baseNome, schema, usuario, tipo)
                .orElse(new SincronizacaoSchema());

        sync.setBaseNome(baseNome);
        sync.setSchemaNome(schema);
        sync.setUsuario(usuario);
        sync.setOperacao(tipo);
        sync.setStatus(StatusSincronizacao.PROCESSANDO);
        sync.setUltimaExecucao(LocalDateTime.now());
        sync.setDetalhes("Processo iniciado.");

        return repository.save(sync);
    }


    // -------------------------------------------------------
    // FINALIZAR SUCESSO
    // -------------------------------------------------------
    public SincronizacaoSchema finalizarSucesso(
            String baseNome,
            String schema,
            String usuario,
            String detalhes,
            TipoOperacao tipo) {

        SincronizacaoSchema sync = getOrCreate(baseNome, schema, usuario, tipo);

        sync.setStatus(StatusSincronizacao.SINCRONIZADO);
        sync.setUltimaExecucao(LocalDateTime.now());
        sync.setDetalhes(detalhes != null ? detalhes : "Sincronização concluída com sucesso.");

        return repository.save(sync);
    }


    // -------------------------------------------------------
    // FINALIZAR ERRO
    // -------------------------------------------------------
    public SincronizacaoSchema finalizarErro(
            String baseNome,
            String schema,
            String usuario,
            String mensagemErro,
            TipoOperacao tipo) {

        SincronizacaoSchema sync = getOrCreate(baseNome, schema, usuario, tipo);

        sync.setStatus(StatusSincronizacao.ERRO);
        sync.setUltimaExecucao(LocalDateTime.now());
        sync.setDetalhes(mensagemErro);

        return repository.save(sync);
    }


    // -------------------------------------------------------
    // FINALIZAR CANCELADO
    // -------------------------------------------------------
    public SincronizacaoSchema finalizarCancelado(
            String baseNome,
            String schema,
            String usuario,
            String motivo,
            TipoOperacao tipo) {

        SincronizacaoSchema sync = getOrCreate(baseNome, schema, usuario, tipo);

        sync.setStatus(StatusSincronizacao.CANCELADO);
        sync.setUltimaExecucao(LocalDateTime.now());
        sync.setDetalhes(motivo != null ? motivo : "Processo cancelado pelo usuário.");

        return repository.save(sync);
    }


    // -------------------------------------------------------
    // MARCAR DESATUALIZADO (scripts gerados porém não aplicados)
    // -------------------------------------------------------
    public SincronizacaoSchema marcarComoDesatualizado(
            String baseNome,
            String schema,
            String usuario,
            String detalhes,
            TipoOperacao tipo) {

        SincronizacaoSchema sync = getOrCreate(baseNome, schema, usuario, tipo);

        sync.setStatus(StatusSincronizacao.DESATUALIZADO);
        sync.setUltimaExecucao(LocalDateTime.now());
        sync.setDetalhes(detalhes);

        return repository.save(sync);
    }


    // -------------------------------------------------------
    // CONSULTA ÚNICA
    // -------------------------------------------------------
    public Optional<SincronizacaoSchema> buscar(String baseNome, String schema, String usuario, TipoOperacao tipo) {
        return repository.findByBaseNomeAndSchemaNomeAndUsuarioAndOperacao(baseNome, schema, usuario, tipo);
    }


    // -------------------------------------------------------
    // LISTAR TODAS AS OPERAÇÕES DO USUÁRIO
    // -------------------------------------------------------
    public List<SincronizacaoSchema> listarPorUsuario(String usuario) {
        return repository.findAllByUsuarioOrderByUltimaExecucaoDesc(usuario);
    }

    // -------------------------------------------------------
    // CRIAR OU RECUPERAR PROCESSO
    // -------------------------------------------------------
    private SincronizacaoSchema getOrCreate(
            String baseNome,
            String schema,
            String usuario,
            TipoOperacao tipo) {

        return repository
                .findByBaseNomeAndSchemaNomeAndUsuarioAndOperacao(baseNome, schema, usuario, tipo)
                .orElse(
                    new SincronizacaoSchema(
                        null,                   // id
                        baseNome,
                        schema,
                        usuario,
                        tipo,
                        StatusSincronizacao.NAO_SINCRONIZADO,
                        null,                   // detalhes
                        null                    // ultimaExecucao
                    )
                );
    }

}