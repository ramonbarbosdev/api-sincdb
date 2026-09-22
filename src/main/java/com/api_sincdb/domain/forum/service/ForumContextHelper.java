package com.api_sincdb.domain.forum.service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.forum.model.ForumPost;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.enums.TipoRole;

@Component
public class ForumContextHelper {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public String idUsuarioAtual() {
        String id = TenantRuntimeContext.getIdUsuario();
        if (id != null && !id.isBlank()) {
            return id;
        }
        return TenantRuntimeContext.getLogin();
    }

    public Set<String> chavesUsuarioAtual() {
        Set<String> chaves = new LinkedHashSet<>();
        String idUsuario = TenantRuntimeContext.getIdUsuario();
        String login = TenantRuntimeContext.getLogin();
        if (idUsuario != null && !idUsuario.isBlank()) {
            chaves.add(idUsuario);
        }
        if (login != null && !login.isBlank()) {
            chaves.add(login);
        }
        return chaves;
    }

    public boolean isDev(String role) {
        return TipoRole.ROLE_DEV.name().equals(role);
    }

    public boolean podeGerenciarPost(ForumPost post, String role) {
        if (post == null) {
            return false;
        }
        if (isDev(role)) {
            return true;
        }
        Set<String> chaves = chavesUsuarioAtual();
        return post.getIdUsuario() != null && chaves.contains(post.getIdUsuario());
    }

    public Optional<Usuario> buscarUsuarioAtual() {
        String idUsuario = TenantRuntimeContext.getIdUsuario();
        if (idUsuario != null && !idUsuario.isBlank()) {
            Optional<Usuario> porId = usuarioRepository.findById(idUsuario);
            if (porId.isPresent()) {
                return porId;
            }
        }
        String login = TenantRuntimeContext.getLogin();
        if (login != null && !login.isBlank()) {
            Usuario porLogin = usuarioRepository.findByLogin(login);
            if (porLogin != null) {
                return Optional.of(porLogin);
            }
        }
        return Optional.empty();
    }

    public Optional<Usuario> buscarPorChaveArmazenada(String chave) {
        if (chave == null || chave.isBlank()) {
            return Optional.empty();
        }
        Optional<Usuario> porId = usuarioRepository.findById(chave);
        if (porId.isPresent()) {
            return porId;
        }
        return Optional.ofNullable(usuarioRepository.findByLogin(chave));
    }

    public String chaveAutor(Usuario autor) {
        if (autor != null && autor.getId() != null && !autor.getId().isBlank()) {
            return autor.getId();
        }
        String idUsuario = TenantRuntimeContext.getIdUsuario();
        if (idUsuario != null && !idUsuario.isBlank()) {
            return idUsuario;
        }
        return TenantRuntimeContext.getLogin();
    }

    public String nomeExibicao(Usuario usuario, String fallback) {
        if (usuario != null && usuario.getNome() != null && !usuario.getNome().isBlank()) {
            return usuario.getNome().trim();
        }
        if (fallback != null && !fallback.isBlank() && !pareceIdMongo(fallback)) {
            return fallback;
        }
        return "Usuário";
    }

    public String imgPerfil(Usuario usuario) {
        if (usuario == null || usuario.getImg() == null) {
            return null;
        }
        String img = usuario.getImg().trim();
        return img.isEmpty() ? null : img;
    }

    public boolean pareceIdMongo(String value) {
        return value != null && value.matches("[a-f0-9A-F]{24}");
    }
}
