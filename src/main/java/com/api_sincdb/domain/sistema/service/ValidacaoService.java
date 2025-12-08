package com.api_sincdb.domain.sistema.service;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hibernate.mapping.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.enums.TipoRole;

@Service
public class ValidacaoService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public <T> String gerarSequencia(String sequencia) throws Exception {
        Long ultima_sequencia = Optional.ofNullable(sequencia)
                .map(Long::valueOf)
                .orElse(0L);

        Long sq_sequencia = ultima_sequencia + 1;
        String resposta = "%03d".formatted(sq_sequencia);

        return resposta;
    }

    public <T> String validarCodigoExistenteProjeto(
            Long idAtual,
            Optional<T> objetoExistente,
            Function<T, Long> getIdFunction,
            Supplier<String> gerarProximoCodigo) throws Exception {

        if (objetoExistente.isEmpty()) {
            return null; // não existe duplicidade
        }

        Long idExistente = getIdFunction.apply(objetoExistente.get());

        boolean idsDiferentes = (idExistente == null && idAtual != null)
                || (idExistente != null && !idExistente.equals(idAtual));

        if (idsDiferentes) {
            // ⚙️ Gera o próximo código disponível (por ex: incrementa o número)
            return gerarProximoCodigo.get();
        }

        return null;
    }

    public <T> void validarCodigoExistente(
            String idAtual,
            Optional<T> objetoExistente,
            Function<T, String> getIdFunction) throws Exception {

        if (!objetoExistente.isPresent())
            return;

        String idExistente = getIdFunction.apply(objetoExistente.get());

        boolean idsDiferentes = (idExistente == null && idAtual != null)
                || (idExistente != null && !idExistente.equals(idAtual));

        if (idsDiferentes) {
            throw new Exception("Código já existente!");
        }
    }

    public <T> Boolean verificarEquivalencia(
            Long idAtual,
            Optional<T> objetoExistente,
            Function<T, Long> getIdFunction) throws Exception {

        if (!objetoExistente.isPresent())
            return false;

        Long idExistente = getIdFunction.apply(objetoExistente.get());

        boolean idsDiferentes = (idExistente == null && idAtual != null)
                || (idExistente != null && !idExistente.equals(idAtual));

        if (idsDiferentes) {
            return true;
        }
        return false;
    }

    public Boolean verificarAdministrador() throws Exception {

        Boolean isAdmin = false;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null)
            throw new Exception("Falha na authenticação de usuario, tente novamente");

        var loginAuth = auth.getPrincipal();
        Usuario usuario = usuarioRepository.findByLogin(String.valueOf(loginAuth));
        String nomeRole = usuario.getRoles().iterator().next().getNomeRole();

        if (nomeRole.equals(TipoRole.ROLE_ADMIN.name())) {
            isAdmin = true;
        }

        return isAdmin;

    }

    public String obterLoginUsuario() throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null)
            throw new Exception("Falha na authenticação de usuario, tente novamente");

        var loginAuth = auth.getPrincipal();

        return String.valueOf(loginAuth);

    }

    public Usuario obterUsuarioLogado() throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null)
            throw new Exception("Falha na authenticação de usuario, tente novamente");

        var loginAuth = auth.getPrincipal();

        Usuario usuario = usuarioRepository.findByLogin(String.valueOf(loginAuth));
        return usuario;

    }

    public <T> String obterSequencial(Long obterSequencial) throws Exception {

        Long ultima_sequencia = obterSequencial;

        Long sq_sequencia = ultima_sequencia + 1;
        String resposta = "%03d".formatted(sq_sequencia);

        return resposta;

    }

}
