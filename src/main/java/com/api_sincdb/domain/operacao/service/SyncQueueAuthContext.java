package com.api_sincdb.domain.operacao.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

@Component
public class SyncQueueAuthContext {

    @Autowired
    private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public void bind(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Token de autenticação não informado para processar a fila.");
        }

        String idTenant = jwtTokenAutenticacaoService.extractTenantId(token);
        String idEmpresa = jwtTokenAutenticacaoService.extractEmpresaId(token);
        String idUsuario = jwtTokenAutenticacaoService.extractLogin(token);
        String login = jwtTokenAutenticacaoService.extractSubject(token);

        TenantRuntimeContext.set(idUsuario, idEmpresa, idTenant, login);

        Usuario usuario = usuarioRepository.findByLogin(login);
        if (usuario == null) {
            throw new IllegalStateException("Usuário não encontrado para processar a fila.");
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                login,
                null,
                usuario.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public void clear() {
        SecurityContextHolder.clearContext();
        TenantRuntimeContext.clear();
    }
}
