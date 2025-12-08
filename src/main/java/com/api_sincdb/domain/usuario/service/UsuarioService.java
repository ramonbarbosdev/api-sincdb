package com.api_sincdb.domain.usuario.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api_sincdb.domain.role.repository.RoleRepository;
import com.api_sincdb.domain.sistema.service.ValidacaoService;
import com.api_sincdb.domain.usuario.model.Role;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.enums.TipoRole;
import com.api_sincdb.util.MestreDetalheUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class UsuarioService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UsuarioRepository repository;


    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Cadastra ou atualiza um usuário.
     * 
     * @param usuario Dados do usuário a serem salvos
     * @return usuário salvo
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public Usuario salvar(Usuario objeto) throws Exception {



        Usuario usuarioExistente = repository.findByLogin(objeto.getLogin());

        if (usuarioExistente == null) {
            objeto.setSenha(passwordEncoder.encode(objeto.getSenha()));
        } else {
            String senhaInformada = objeto.getSenha();
            String senhaBanco = usuarioExistente.getSenha();

            if (senhaInformada.equals(senhaBanco)) {
                objeto.setSenha(senhaBanco);
            } else if (!passwordEncoder.matches(senhaInformada, senhaBanco)) {
                objeto.setSenha(passwordEncoder.encode(senhaInformada));
            } else {
                objeto.setSenha(senhaBanco);
            }

            objeto.setId(usuarioExistente.getId());
        }

        validarObjeto(objeto);
        objeto = repository.save(objeto);



        return repository.save(objeto);
    }

    public void validarObjeto(Usuario objeto) throws Exception {

        String nomeRole = objeto.getRoles().iterator().next().getNomeRole();
        Role roleUser = roleRepository.findByNomeRole(nomeRole);
        if (roleUser == null) {
            roleUser = new Role();
            roleUser.setNomeRole(nomeRole);
            roleRepository.save(roleUser);
        }

        objeto.getRoles().clear();
        objeto.getRoles().add(roleUser);

    }

   


  
    public void inserirSenhaCriptografada(Usuario usuario, String novaSenha) throws Exception {

        if (novaSenha.isBlank()) {
            return;
        } else {
            String senhacriptografada = new BCryptPasswordEncoder().encode(novaSenha);
            usuario.setSenha(senhacriptografada);
        }

    }

    public Usuario obterPorId(String id) throws Exception {

        Optional<Usuario> objeto = repository.findById(id);

        if (!objeto.isPresent()) {
            return null;
        }

        return objeto.get();
    }

    public void excluir(String id) throws Exception {

        Usuario objeto = entityManager.getReference(Usuario.class, id);

        if (objeto.getRoles().iterator().next().getNomeRole().equals(TipoRole.ROLE_DEV.name())) {
            throw new Exception("Voce não tem permissão para excluir um desenvolvedor!");

        }
   

        repository.deleteById(id);
    }

}
