package com.api_sincdb.controller.conexao;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.conexao.dto.ConexaoDTO;
import com.api_sincdb.domain.conexao.dto.ConexaoResponseDTO;
import com.api_sincdb.domain.conexao.model.Conexao;
import com.api_sincdb.domain.conexao.repository.ConexaoRepository;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.util.CriptoUtils;
import com.api_sincdb.util.LeitorConfigSegura;

@Service
public class ConexaoService {

    private final ConexaoRepository repository;
    private final UsuarioRepository usuarioRepository;

    public ConexaoService(
            ConexaoRepository repository,
            UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    public ConexaoDTO salvar(ConexaoDTO dto) {
        Usuario user = resolverUsuario();

        String idEmpresa = exigirIdEmpresa();
        String idTenant = exigirIdTenant();

        Conexao conexao = new Conexao();
        preencherConexao(conexao, dto);

        conexao.setIdUsuario(user.getId());
        conexao.setId_empresa(idEmpresa);
        conexao.setId_tenant(idTenant);
        conexao.setFl_ativo(dto.getFl_ativo() == null ? true : dto.getFl_ativo());

        boolean primeiraConexao = !repository.existsById_empresaAndFl_ativoTrue(idEmpresa);
        boolean marcarPadrao = primeiraConexao || Boolean.TRUE.equals(dto.getFl_padrao());

        conexao.setFl_padrao(marcarPadrao);

        if (marcarPadrao) {
            desmarcarPadrao(idEmpresa, null);
        }

        repository.save(conexao);
        ConexaoBanco.fecharTodos();

        return toDTO(conexao);
    }

    public ConexaoDTO atualizar(ConexaoDTO dto) {
        String idEmpresa = exigirIdEmpresa();

        Conexao conexao = buscarConexaoObrigatoria(dto.getId(), idEmpresa);

        Boolean flAdminNovo = dto.getCloud() != null ? dto.getCloud().getFl_admin() : null;
        Boolean flAdminAntigo = conexao.getFl_admin();

        preencherConexao(conexao, dto);

        if (Boolean.FALSE.equals(flAdminNovo) && Boolean.TRUE.equals(flAdminAntigo)) {
            conexao.setDb_cloud_user("");
            conexao.setDb_cloud_password("");
        }

        if (dto.getFl_ativo() != null) {
            conexao.setFl_ativo(dto.getFl_ativo());
        }

        if (Boolean.TRUE.equals(dto.getFl_padrao())) {
            desmarcarPadrao(conexao.getId_empresa(), conexao.getId());
            conexao.setFl_padrao(true);
        }

        repository.save(conexao);
        ConexaoBanco.fecharTodos();

        return toDTO(conexao);
    }

    public List<ConexaoResponseDTO> listarConexoes() {
        String idEmpresa = exigirIdEmpresa();

        return repository.findById_empresaAndFl_ativoTrue(idEmpresa)
                .stream()
                .map(ConexaoResponseDTO::fromEntity)
                .toList();
    }

    public ConexaoDTO recuperarConexao(String id) {
        String idEmpresa = exigirIdEmpresa();
        Conexao conexao = buscarConexaoObrigatoria(id, idEmpresa);

        return toDTO(conexao);
    }

    public ConexaoDTO definirPadrao(String id) {
        String idEmpresa = exigirIdEmpresa();
        Conexao conexao = buscarConexaoObrigatoria(id, idEmpresa);

        desmarcarPadrao(conexao.getId_empresa(), conexao.getId());

        conexao.setFl_padrao(true);
        repository.save(conexao);

        ConexaoBanco.fecharTodos();

        return toDTO(conexao);
    }

    public void remover(String id) {
        String idEmpresa = exigirIdEmpresa();
        Conexao conexao = buscarConexaoObrigatoria(id, idEmpresa);

        boolean eraPadrao = Boolean.TRUE.equals(conexao.getFl_padrao());

        conexao.setFl_ativo(false);
        conexao.setFl_padrao(false);

        repository.save(conexao);

        if (eraPadrao) {
            promoverPrimeiraConexaoAtiva(conexao.getId_empresa());
        }

        ConexaoBanco.fecharTodos();
    }

    public Map<String, Object> uploadCertificado(MultipartFile arquivo) {
        try {
            resolverUsuario();

            exigirIdEmpresa();
            exigirIdTenant();

            if (arquivo == null || arquivo.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Arquivo do certificado nao informado.");
            }

            if (!extensaoCertificadoPermitida(arquivo.getOriginalFilename())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Formato de certificado invalido. Envie um arquivo .pfx, .pem ou .enc.");
            }

            String segredo = "wD7#G2k!91zL*qpB3VmX8eTR";
            byte[] chave = CriptoUtils.gerarChave256(segredo);

            String conteudo = new String(
                    arquivo.getBytes(),
                    StandardCharsets.UTF_8);

            String jsonDescriptografado = CriptoUtils.descriptografar(conteudo, chave);

            JSONObject obj = new JSONObject(jsonDescriptografado);

            String cloudHost = valorJson(obj, "db_cloud_host", "cloud_host", "host");
            String cloudPort = valorJson(obj, "db_cloud_port", "cloud_port", "port");
            String cloudUser = valorJson(obj, "db_cloud_user", "cloud_user", "user");
            String cloudPassword = valorJson(obj, "db_cloud_password", "cloud_password", "password");

            if (campoVazio(cloudHost) ||
                    campoVazio(cloudPort) ||
                    campoVazio(cloudUser) ||
                    campoVazio(cloudPassword)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Certificado invalido ou sem dados obrigatorios da conexao cloud.");
            }

            Map<String, Object> cloud = Map.of(
                    "db_cloud_host", cloudHost,
                    "db_cloud_port", cloudPort,
                    "db_cloud_user", cloudUser,
                    "db_cloud_password", cloudPassword,
                    "fl_admin", true);

            Map<String, Object> local = Map.of(
                    "db_local_host", valorJsonOuVazio(obj, "db_local_host", "local_host"),
                    "db_local_port", valorJsonOuVazio(obj, "db_local_port", "local_port"),
                    "db_local_user", valorJsonOuVazio(obj, "db_local_user", "local_user"),
                    "db_local_password", valorJsonOuVazio(obj, "db_local_password", "local_password"));

            return Map.of(
                    "message", "Certificado processado com sucesso",
                    "cloud", cloud,
                    "local", local);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Falha ao processar o certificado: " + e.getMessage());
        }
    }

    public Properties obterCertificado() throws Exception {
        String segredo = "wD7#G2k!91zL*qpB3VmX8eTR";

        return LeitorConfigSegura.carregarConfiguracao(
                "./config.enc",
                segredo);
    }

    private boolean extensaoCertificadoPermitida(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return false;
        }

        String nome = nomeArquivo.toLowerCase();

        return nome.endsWith(".pfx")
                || nome.endsWith(".pem")
                || nome.endsWith(".enc");
    }

    private String valorJsonOuVazio(JSONObject obj, String... chaves) {
        String valor = valorJson(obj, chaves);
        return valor == null ? "" : valor;
    }

    private String valorJson(JSONObject obj, String... chaves) {
        for (String chave : chaves) {
            if (obj.has(chave) && !obj.isNull(chave)) {
                return obj.optString(chave, "");
            }
        }

        return null;
    }

    private boolean campoVazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private Usuario resolverUsuario() {
        String idUsuario = TenantRuntimeContext.getIdUsuario();

        if (idUsuario == null || idUsuario.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado.");
        }

        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario nao encontrado."));
    }

    private String exigirIdEmpresa() {
        String idEmpresa = TenantRuntimeContext.getIdEmpresa();

        if (idEmpresa == null || idEmpresa.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organizacao ativa nao encontrada no token.");
        }

        return idEmpresa;
    }

    private String exigirIdTenant() {
        String idTenant = TenantRuntimeContext.getIdTenant();

        if (idTenant == null || idTenant.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant ativo nao encontrado no token.");
        }

        return idTenant;
    }

    private Conexao buscarConexaoObrigatoria(String id, String idEmpresa) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id da conexao nao informado.");
        }

        return repository.findByIdAndId_empresa(id, idEmpresa)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conexao nao encontrada."));
    }

    private void preencherConexao(Conexao conexao, ConexaoDTO dto) {
        if (dto.getNm_conexao() != null) {
            conexao.setNm_conexao(dto.getNm_conexao());
        }

        if (dto.getCloud() != null) {
            conexao.setDb_cloud_host(dto.getCloud().getDb_cloud_host());
            conexao.setDb_cloud_port(dto.getCloud().getDb_cloud_port());
            conexao.setDb_cloud_user(dto.getCloud().getDb_cloud_user());
            conexao.setDb_cloud_password(dto.getCloud().getDb_cloud_password());
            conexao.setFl_admin(dto.getCloud().getFl_admin());
        }

        if (dto.getLocal() != null) {
            conexao.setDb_local_host(dto.getLocal().getDb_local_host());
            conexao.setDb_local_port(dto.getLocal().getDb_local_port());
            conexao.setDb_local_user(dto.getLocal().getDb_local_user());
            conexao.setDb_local_password(dto.getLocal().getDb_local_password());
        }
    }

    private void desmarcarPadrao(String idEmpresa, String idIgnorado) {
        List<Conexao> conexoes = repository.findById_empresaAndFl_ativoTrue(idEmpresa);

        for (Conexao conexao : conexoes) {
            if (idIgnorado != null && idIgnorado.equals(conexao.getId())) {
                continue;
            }

            if (Boolean.TRUE.equals(conexao.getFl_padrao())) {
                conexao.setFl_padrao(false);
                repository.save(conexao);
            }
        }
    }

    private void promoverPrimeiraConexaoAtiva(String idEmpresa) {
        List<Conexao> conexoes = repository.findById_empresaAndFl_ativoTrue(idEmpresa);

        if (conexoes.isEmpty()) {
            return;
        }

        Conexao primeira = conexoes.get(0);
        primeira.setFl_padrao(true);

        repository.save(primeira);
    }

    private ConexaoDTO toDTO(Conexao conexao) {
        ConexaoDTO dto = new ConexaoDTO();

        dto.setId(conexao.getId());
        dto.setNm_conexao(conexao.getNm_conexao());
        dto.setFl_padrao(conexao.getFl_padrao());
        dto.setFl_ativo(conexao.getFl_ativo());
        dto.setIdUsuario(conexao.getIdUsuario());
        dto.setId_empresa(conexao.getId_empresa());
        dto.setId_tenant(conexao.getId_tenant());

        ConexaoDTO.CloudConnection cloud = new ConexaoDTO.CloudConnection();
        cloud.setDb_cloud_host(conexao.getDb_cloud_host());
        cloud.setDb_cloud_port(conexao.getDb_cloud_port());
        cloud.setDb_cloud_user(conexao.getDb_cloud_user());
        cloud.setDb_cloud_password(conexao.getDb_cloud_password());
        cloud.setFl_admin(conexao.getFl_admin());

        dto.setCloud(cloud);

        ConexaoDTO.LocalConnection local = new ConexaoDTO.LocalConnection();
        local.setDb_local_host(conexao.getDb_local_host());
        local.setDb_local_port(conexao.getDb_local_port());
        local.setDb_local_user(conexao.getDb_local_user());
        local.setDb_local_password(conexao.getDb_local_password());

        dto.setLocal(local);

        return dto;
    }

}