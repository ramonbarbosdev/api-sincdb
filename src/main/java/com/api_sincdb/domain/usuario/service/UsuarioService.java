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

import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.empresa.repository.UsuarioEmpresaRepository;
import com.api_sincdb.domain.role.model.Role;
import com.api_sincdb.domain.role.repository.RoleRepository;
import com.api_sincdb.domain.sistema.service.ValidacaoService;
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

    @Autowired
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

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

        List<UsuarioEmpresa> itensUsuarioEmpresas = objeto.getItensUsuarioEmpresa();
        objeto.setItensUsuarioEmpresa(null);

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

        salvarUsuarioEmpresaDetalhe(objeto, itensUsuarioEmpresas);

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

    public void salvarUsuarioEmpresaDetalhe(Usuario objeto,
            List<UsuarioEmpresa> itens) throws Exception {

        Function<Usuario, String> getIdFunctionMestre = Usuario::getId;
        Function<UsuarioEmpresa, String> getIdFunction = UsuarioEmpresa::getId;

        String idMestre = getIdFunctionMestre.apply(objeto);

        MestreDetalheUtils.removerItensGenerico(
                idMestre,
                itens,
                usuarioEmpresaRepository::findById_usuario,
                usuarioEmpresaRepository::deleteById_usuario,
                getIdFunction);

        if (itens != null && itens.size() > 0) {
            for (UsuarioEmpresa item : itens) {
                item.setId_usuario(idMestre);

                String idExistente = getIdFunction.apply(item);

                if (idExistente == null || idExistente.isBlank()) {
                    item.setId(null);
                }

                validarItemUsuarioEmpresa(item, itens, objeto);

                item = usuarioEmpresaRepository.save(item);
            }

            objeto.setItensUsuarioEmpresa(itens);
        }

        if(objeto.getItensUsuarioEmpresa() == null )
        {
            throw new Exception("É necessário existir um vínculo com uma empresa.");
        }
    }

    public void validarItemUsuarioEmpresa(UsuarioEmpresa item,
            List<UsuarioEmpresa> itens, Usuario objeto) throws Exception {

        if (item.getId_empresa() == null) {
            throw new Exception("A empresa vinculada não pode ser nula.");
        }

        boolean existeVinculoBanco = usuarioEmpresaRepository
                .existsById_usuarioAndId_empresa(objeto.getId(), item.getId_empresa());

        if (existeVinculoBanco && (item.getId() == null || item.getId().isBlank())) {
            throw new Exception("O usuário já está vinculado a esta empresa.");
        }

        long countMesmoTenant = itens.stream()
                .filter(i -> i.getId_empresa() != null &&
                        i.getId_empresa().equals(item.getId_empresa()))
                .count();

        if (countMesmoTenant > 1) {
            throw new Exception(
                    "Duplicidade detectada: o usuário não pode estar vinculado duas vezes à mesma empresa.");
        }
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

        Usuario objeto = repository.findById(id)
                .orElseThrow(() -> new Exception("Usuário não encontrado!"));

        if (objeto.getRoles() != null && !objeto.getRoles().isEmpty()) {

            boolean isDev = objeto.getRoles().stream()
                    .anyMatch(r -> r.getNomeRole().equals(TipoRole.ROLE_DEV.name()));

            if (isDev) {
                throw new Exception("Você não tem permissão para excluir um desenvolvedor!");
            }

        }

        repository.deleteById(id);
    }

}
