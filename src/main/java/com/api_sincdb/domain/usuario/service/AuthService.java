package com.api_sincdb.domain.usuario.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api_sincdb.domain.empresa.model.Empresa;
import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.empresa.repository.UsuarioEmpresaRepository;
import com.api_sincdb.domain.empresa.service.EmpresaService;
import com.api_sincdb.domain.role.model.Role;
import com.api_sincdb.domain.role.repository.RoleRepository;
import com.api_sincdb.domain.tenant.service.AuthDirectoryService;
import com.api_sincdb.domain.usuario.dto.AuthRegisterDTO;
import com.api_sincdb.domain.usuario.dto.LoginRequestDTO;
import com.api_sincdb.domain.usuario.dto.LoginResponseDTO;
import com.api_sincdb.domain.usuario.dto.MeResponseDTO;
import com.api_sincdb.domain.usuario.dto.OrganizacaoLoginDTO;
import com.api_sincdb.domain.usuario.dto.SelecionarOrganizacaoResponseDTO;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.enums.TipoRole;
import com.api_sincdb.security.JWTTokenAutenticacaoService;
import com.api_sincdb.context.TenantRuntimeContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

    @Autowired
    private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    @Autowired
    private AuthDirectoryService authDirectoryService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UsuarioOnlineService onlineService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public LoginResponseDTO login(LoginRequestDTO request) throws Exception {
        Usuario usuario = authDirectoryService.autenticarCredenciais(request.nuCpf(), request.dsSenha());
        String dsRole = obterRolePrincipal(usuario);
        List<OrganizacaoLoginDTO> organizacoes = authDirectoryService.listarOrganizacoesLogin(usuario.getId(), dsRole);
        String token = jwtTokenAutenticacaoService.gerarTokenSemTenant(usuario, "DEFAULT");

        return new LoginResponseDTO(
                token,
                "DEFAULT",
                true,
                false,
                organizacoes);
    }

    public SelecionarOrganizacaoResponseDTO selecionarOrganizacao(String idOrganizacao) throws Exception {
        String idUsuario = TenantRuntimeContext.getIdUsuario();

        if (idUsuario == null || idUsuario.isBlank()) {
            throw new Exception("Token temporario ausente ou invalido.");
        }

        Usuario usuario = usuarioService.obterPorId(idUsuario);

        if (usuario == null) {
            throw new Exception("Usuario autenticado nao encontrado.");
        }

        Empresa empresa = authDirectoryService.validarOrganizacaoSelecionadaPorId(usuario, idOrganizacao);
        String dsRole = obterRolePrincipal(usuario);
        List<String> permissoes = List.of();

        String token = jwtTokenAutenticacaoService.gerarTokenComTenant(
                usuario,
                empresa.getId(),
                empresa.getId_tenant(),
                dsRole,
                permissoes);

        return new SelecionarOrganizacaoResponseDTO(
                token,
                empresa.getId(),
                dsRole,
                permissoes);
    }

    public MeResponseDTO me() throws Exception {
        String idUsuario = TenantRuntimeContext.getIdUsuario();

        if (idUsuario == null || idUsuario.isBlank()) {
            throw new Exception("Usuario autenticado nao encontrado.");
        }

        Usuario usuario = usuarioService.obterPorId(idUsuario);

        if (usuario == null) {
            throw new Exception("Usuario autenticado nao encontrado.");
        }

        return new MeResponseDTO(
                usuario.getId(),
                "DEFAULT",
                TenantRuntimeContext.getIdEmpresa(),
                TenantRuntimeContext.getIdEmpresa() == null ? null : obterRolePrincipal(usuario),
                usuario.getNome(),
                usuario.getLogin(),
                List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map efetuarCadastro(AuthRegisterDTO obj, HttpServletResponse response) throws Exception {
        String login = obj.getLogin();
        String nome = obj.getNome();
        String senha = obj.getSenha();

        Map<String, String> erros = validarCadastro(login, senha, nome);
        if (erros != null) {
            return erros;
        }

        Usuario usuario = new Usuario();
        usuario.setLogin(login);
        usuario.setNome(nome);
        usuario.setSenha(senha);

        criarRoleDev(usuario);

        usuario = usuarioService.salvar(usuario);

        criarEmpresaBase(usuario);

        usuario = usuarioService.salvar(usuario);

        return Map.of("usuario", usuario, "message", "Usuario criado com sucesso!");
    }

    public Map<String, String> validarCadastro(String login, String senha, String nome) {
        if (login.isEmpty()) {
            return Map.of("message", "O Login nao pode ser vazio!");
        }

        if (nome.isEmpty()) {
            return Map.of("message", "O Nome nao pode ser vazio!");
        }

        if (senha.isEmpty()) {
            return Map.of("message", "A Senha nao pode ser vazia!");
        }

        if (repository.findByLogin(login) != null) {
            return Map.of("message", "Usuario ja existe!");
        }

        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public void criarEmpresaBase(Usuario usuario) throws Exception {
        String nomeBase = "Desenvolvimento";
        Empresa empresa = empresaService.verificarExistenciaPorNome(nomeBase);

        if (empresa == null) {
            empresa = new Empresa();
            empresa.setNm_empresa(nomeBase);
            empresa.setFl_ativo(true);
            empresa.setCd_empresa(empresaService.sequencia());
            empresa = empresaService.salvar(empresa);
        }

        boolean jaTemVinculo = usuarioEmpresaRepository.existsById_usuarioAndId_empresa(
                usuario.getId(),
                empresa.getId());

        if (!jaTemVinculo) {
            UsuarioEmpresa usuarioEmpresa = new UsuarioEmpresa();
            usuarioEmpresa.setId_usuario(usuario.getId());
            usuarioEmpresa.setId_empresa(empresa.getId());

            usuarioEmpresa = usuarioEmpresaRepository.save(usuarioEmpresa);
            usuario.getItensUsuarioEmpresa().add(usuarioEmpresa);
        }
    }

    public void criarRoleDev(Usuario usuario) throws Exception {
        Role roleUser = roleRepository.findByNomeRole(TipoRole.ROLE_DEV.name());

        if (roleUser == null) {
            roleUser = new Role();
            roleUser.setNomeRole(TipoRole.ROLE_DEV.name());
            roleRepository.save(roleUser);
        }

        if (usuario.getRoles() == null) {
            usuario.setRoles(new ArrayList<>());
        }

        usuario.getRoles().clear();
        usuario.getRoles().add(roleUser);
    }

    public Boolean logout(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
            onlineService.removerUsuario(auth.getName());

            if (messagingTemplate != null) {
                messagingTemplate.convertAndSend("/topic/online", Map.of("login", auth.getName()));
            }

            return true;
        }

        return false;
    }

    private String obterRolePrincipal(Usuario usuario) {
        if (usuario.getRoles() == null || usuario.getRoles().isEmpty()) {
            return TipoRole.ROLE_USER.name();
        }

        return usuario.getRoles().iterator().next().getNomeRole();
    }
}
