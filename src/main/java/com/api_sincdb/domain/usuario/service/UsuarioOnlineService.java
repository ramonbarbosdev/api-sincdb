package com.api_sincdb.domain.usuario.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.usuario.model.UsuarioOnline;
import com.api_sincdb.domain.usuario.protection.UsuarioOnlineDetalhadoProjection;
import com.api_sincdb.domain.usuario.repository.UsuarioOnlineRepository;

@Service
public class UsuarioOnlineService {

    @Autowired
    private UsuarioOnlineRepository repository;

    public void adicionarUsuario(String username) {
        Optional<UsuarioOnline> entity = repository.findByLogin(username);

        UsuarioOnline objeto;
        if (entity.isPresent()) {
            objeto = entity.get();
            objeto.setDt_ultimologin(LocalDateTime.now());
            objeto.setFl_ativo(true);
        } else {
            objeto = new UsuarioOnline(username);
        }

        repository.save(objeto);
    }

    /** Marca o usuário como offline (logout ou inatividade) */
    public void removerUsuario(String username) {
        repository.findByLogin(username).ifPresent(u -> {
            u.setFl_ativo(false);
            repository.save(u);
        });
    }

    /** Lista todos os usuários online */
    public List<UsuarioOnline> listarUsuariosOnline() {
        List<UsuarioOnline> lista = new ArrayList<>();
        repository.findAll().forEach(lista::add);
        return lista.stream()
                .filter(UsuarioOnline::getFl_ativo)
                .toList();
    }

    public List<UsuarioOnlineDetalhadoProjection> obterInformacoesUsuario() {
        List<UsuarioOnlineDetalhadoProjection> lista = new ArrayList<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {

            lista = repository.obterInformacoesUsuario(auth.getName());
        }

        return lista;
    }

    /** Verifica se está online */
    public boolean isOnline(String username) {
        return repository.findByLogin(username)
                .map(UsuarioOnline::getFl_ativo)
                .orElse(false);
    }
}
