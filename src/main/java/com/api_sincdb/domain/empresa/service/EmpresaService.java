package com.api_sincdb.domain.empresa.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api_sincdb.domain.empresa.model.Empresa;
import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.empresa.repository.EmpresaRepository;
import com.api_sincdb.domain.empresa.repository.UsuarioEmpresaRepository;
import com.api_sincdb.domain.sistema.service.ValidacaoService;
import com.api_sincdb.util.TenantUtil;

@Service
public class EmpresaService {

    @Autowired
    private EmpresaRepository repository;

    @Autowired
    private UsuarioEmpresaRepository usuarioEmpresaRepository;

    @Autowired
    private ValidacaoService validacaoService;

    public static final Function<Empresa, String> ID_FUNCTION = Empresa::getId;

    public static final Function<Empresa, String> SEQUENCIA_FUNCTION = Empresa::getCd_empresa;

    @Transactional(rollbackFor = Exception.class)
    public Empresa salvar(Empresa objeto) throws Exception {

        validarObjeto(objeto);

        return repository.save(objeto);
    }

    public void validarObjeto(Empresa objeto) throws Exception {
        validacaoService.validarCodigoExistente(
                ID_FUNCTION.apply(objeto),
                repository.findByCd_empresa(SEQUENCIA_FUNCTION.apply(objeto)),
                ID_FUNCTION);

        if (objeto.getId_tenant() == null || objeto.getId_tenant().isEmpty()) {
            objeto.setId_tenant(TenantUtil.generateTenantId());
        }
    }

    public Empresa buscarPorId(String id) throws Exception {

        Optional<Empresa> objeto = repository.findById(id);

        return objeto.isPresent() ? objeto.get() : null;
    }

    public List<Empresa> buscarListagemVinculoPorUsuario(String id_usuario) {
        List<Empresa> list = buscarEmpresasPorUsuario(id_usuario);
        return list == null ? Collections.emptyList() : list;

    }

    public List<Empresa> buscarEmpresasPorUsuario(String id_usuario) {

        // 1️⃣ Buscar os vínculos do usuário
        List<UsuarioEmpresa> vinculos = usuarioEmpresaRepository.findById_usuario(id_usuario);

        if (vinculos.isEmpty()) {
            return List.of(); // Nenhuma empresa vinculada
        }

        // 2️⃣ Extrair só os ids de empresas
        List<String> idsEmpresas = vinculos.stream()
                .map(UsuarioEmpresa::getId_empresa)
                .toList();

        // 3️⃣ Buscar as empresas ativas vinculadas ao usuário
        return repository.findById_empresaInAndFl_ativoTrue(idsEmpresas);
    }

    public Empresa verificarExistenciaPorNome(String nome) throws Exception {

        Optional<Empresa> objeto = repository.findByNm_empresa(nome);

        return objeto.isPresent() ? objeto.get() : null;
    }

    public String sequencia() throws Exception {

      String ultimoCodigo = repository.findTopByOrderByCd_empresaDesc()
            .map(Empresa::getCd_empresa)
            .orElse("0"); // ← valor padrão quando o banco está sem registros

        String sq_sequencia = validacaoService.gerarSequencia(ultimoCodigo);

        return sq_sequencia;
    }

    public String obterSequencial() {
        return repository.findTopByOrderByCd_empresaDesc()
                .map(Empresa::getCd_empresa)
                .orElse("0");
    }
}
