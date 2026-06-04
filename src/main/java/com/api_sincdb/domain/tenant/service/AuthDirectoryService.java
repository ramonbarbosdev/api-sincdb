package com.api_sincdb.domain.tenant.service;

import java.util.Collections;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.empresa.model.Empresa;
import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.empresa.repository.EmpresaRepository;
import com.api_sincdb.domain.empresa.repository.UsuarioEmpresaRepository;
import com.api_sincdb.domain.usuario.dto.OrganizacaoLoginDTO;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;

@Service
public class AuthDirectoryService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioEmpresaRepository usuarioEmpresaRepository;

    public AuthDirectoryService(
            AuthenticationManager authenticationManager,
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            UsuarioEmpresaRepository usuarioEmpresaRepository) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioEmpresaRepository = usuarioEmpresaRepository;
    }

    public Usuario autenticarCredenciais(String login, String senha) throws Exception {
        var usernamePassword = new UsernamePasswordAuthenticationToken(login, senha);
        var auth = authenticationManager.authenticate(usernamePassword);

        if (auth == null || !auth.isAuthenticated()) {
            throw new Exception("Usuario ou senha invalidos!");
        }

        Usuario usuario = usuarioRepository.findByLogin(login);

        if (usuario == null) {
            throw new Exception("Usuario ou senha invalidos!");
        }

        return usuario;
    }

    public List<Empresa> listarOrganizacoesAtivasDoUsuario(String idUsuario) {
        List<UsuarioEmpresa> vinculos = usuarioEmpresaRepository.findById_usuario(idUsuario);

        if (vinculos.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> idsEmpresas = vinculos.stream()
                .map(UsuarioEmpresa::getId_empresa)
                .toList();

        return empresaRepository.findById_empresaInAndFl_ativoTrue(idsEmpresas);
    }

    public List<OrganizacaoLoginDTO> listarOrganizacoesLogin(String idUsuario, String dsRole) {
        return listarOrganizacoesAtivasDoUsuario(idUsuario).stream()
                .map(empresa -> new OrganizacaoLoginDTO(
                        empresa.getId(),
                        empresa.getNm_empresa(),
                        dsRole))
                .toList();
    }

    public Empresa validarOrganizacaoSelecionadaPorId(Usuario usuario, String idOrganizacao) throws Exception {
        if (idOrganizacao == null || idOrganizacao.isBlank()) {
            throw new Exception("Organizacao nao informada.");
        }

        Empresa empresa = empresaRepository.findById(idOrganizacao)
                .filter(Empresa::isFl_ativo)
                .orElseThrow(() -> new Exception("Organizacao nao encontrada ou inativa."));

        validarVinculo(usuario, empresa);

        return empresa;
    }

    public Empresa validarOrganizacaoSelecionada(Usuario usuario, String idTenant) throws Exception {
        if (idTenant == null || idTenant.isBlank()) {
            throw new Exception("Organizacao/tenant nao informado.");
        }

        Empresa empresa = empresaRepository.findById_tenantAndFl_ativoTrue(idTenant)
                .orElseThrow(() -> new Exception("Organizacao/tenant nao encontrado ou inativo."));

        validarVinculo(usuario, empresa);

        return empresa;
    }

    private void validarVinculo(Usuario usuario, Empresa empresa) throws Exception {
        boolean possuiVinculo = usuarioEmpresaRepository.existsById_usuarioAndId_empresa(
                usuario.getId(),
                empresa.getId());

        if (!possuiVinculo) {
            throw new Exception("Usuario nao possui acesso a organizacao selecionada.");
        }
    }
}
