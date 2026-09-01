package com.api_sincdb.controller.conexao;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

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
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.util.CriptoUtils;
import com.api_sincdb.util.LeitorConfigSegura;

@Service
public class ConexaoService {

    private final ConexaoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final ConexaoBanco conexaoBanco;
    private final Map<String, String> certificadoCloudSenhaPendente = new ConcurrentHashMap<>();

    public ConexaoService(
            ConexaoRepository repository,
            UsuarioRepository usuarioRepository,
            ConexaoBanco conexaoBanco) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.conexaoBanco = conexaoBanco;
    }

    public ConexaoDTO salvar(ConexaoDTO dto) {
        Usuario user = resolverUsuario();

        String idEmpresa = exigirIdEmpresa();
        String idTenant = exigirIdTenant();

        aplicarSenhaCloudPendente(dto);

        Conexao conexao = new Conexao();
        preencherConexao(conexao, dto);

        conexao.setIdUsuario(user.getId());
        conexao.setId_empresa(idEmpresa);
        conexao.setId_tenant(idTenant);
        conexao.setFl_ativo(dto.getFl_ativo() == null ? true : dto.getFl_ativo());

        boolean primeiraConexao = !repository.existsById_empresaAndIdUsuarioAndFl_ativoTrue(idEmpresa, user.getId());
        boolean marcarPadrao = primeiraConexao || Boolean.TRUE.equals(dto.getFl_padrao());

        conexao.setFl_padrao(marcarPadrao);

        if (marcarPadrao) {
            desmarcarPadrao(idEmpresa, user.getId(), null);
        }

        repository.save(conexao);
        conexaoBanco.fecharTodos();

        return toDTO(conexao);
    }

    public ConexaoDTO atualizar(ConexaoDTO dto) {
        Usuario user = resolverUsuario();
        String idEmpresa = exigirIdEmpresa();

        aplicarSenhaCloudPendente(dto);

        Conexao conexao = buscarConexaoObrigatoria(dto.getId(), idEmpresa, user.getId());

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
            desmarcarPadrao(conexao.getId_empresa(), user.getId(), conexao.getId());
            conexao.setFl_padrao(true);
        }

        repository.save(conexao);
        conexaoBanco.fecharTodos();

        return toDTO(conexao);
    }

    public List<ConexaoResponseDTO> listarConexoes() {
        Usuario user = resolverUsuario();
        String idEmpresa = exigirIdEmpresa();

        return repository.findById_empresaAndIdUsuarioAndFl_ativoTrue(idEmpresa, user.getId())
                .stream()
                .map(ConexaoResponseDTO::fromEntity)
                .toList();
    }

    public ConexaoDTO recuperarConexao(String id) {
        Usuario user = resolverUsuario();
        String idEmpresa = exigirIdEmpresa();
        Conexao conexao = buscarConexaoObrigatoria(id, idEmpresa, user.getId());

        return toDTO(conexao);
    }

    public ConexaoDTO definirPadrao(String id) {
        Usuario user = resolverUsuario();
        String idEmpresa = exigirIdEmpresa();
        Conexao conexao = buscarConexaoObrigatoria(id, idEmpresa, user.getId());

        desmarcarPadrao(conexao.getId_empresa(), user.getId(), conexao.getId());

        conexao.setFl_padrao(true);
        repository.save(conexao);

        conexaoBanco.fecharTodos();

        return toDTO(conexao);
    }

    public void remover(String id) {
        Usuario user = resolverUsuario();
        String idEmpresa = exigirIdEmpresa();
        Conexao conexao = buscarConexaoObrigatoria(id, idEmpresa, user.getId());

        boolean eraPadrao = Boolean.TRUE.equals(conexao.getFl_padrao());

        conexao.setFl_ativo(false);
        conexao.setFl_padrao(false);

        repository.save(conexao);

        if (eraPadrao) {
            promoverPrimeiraConexaoAtiva(conexao.getId_empresa(), user.getId());
        }

        conexaoBanco.fecharTodos();
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

            registrarSenhaCloudPendente(cloudPassword);

            Map<String, Object> cloud = Map.of(
                    "db_cloud_host", cloudHost,
                    "db_cloud_port", cloudPort,
                    "db_cloud_user", cloudUser,
                    "fl_admin", true,
                    "fl_cloud_password_defined", true);

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

    public Map<String, Object> testarConexao(ConexaoDTO dto) {
        Usuario user = resolverUsuario();
        String idEmpresa = exigirIdEmpresa();

        Conexao conexao = montarConexaoParaTeste(dto, idEmpresa, user.getId());

        Map<String, Object> cloud = conexaoBanco.testarConexaoJdbc(conexao, TipoConexao.CLOUD);
        Map<String, Object> local = conexaoBanco.testarConexaoJdbc(conexao, TipoConexao.LOCAL);

        boolean cloudOk = Boolean.TRUE.equals(cloud.get("ok"));
        boolean localOk = Boolean.TRUE.equals(local.get("ok"));

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("cloud", cloud);
        resultado.put("local", local);
        resultado.put("ok", cloudOk && localOk);
        resultado.put("message", montarMensagemTeste(cloud, local, cloudOk, localOk));

        return resultado;
    }

    private Conexao montarConexaoParaTeste(ConexaoDTO dto, String idEmpresa, String idUsuario) {
        Conexao conexao;

        if (dto.getId() != null && !dto.getId().isBlank()) {
            conexao = buscarConexaoObrigatoria(dto.getId(), idEmpresa, idUsuario);
        } else {
            conexao = new Conexao();
        }

        preencherConexaoParaTeste(conexao, dto);
        aplicarSenhaCloudPendenteParaTeste(conexao);
        return conexao;
    }

    private void aplicarSenhaCloudPendente(ConexaoDTO dto) {
        if (dto.getCloud() == null || senhaInformada(dto.getCloud().getDb_cloud_password())) {
            return;
        }

        String senhaPendente = consumirSenhaCloudPendente();
        if (senhaPendente != null) {
            dto.getCloud().setDb_cloud_password(senhaPendente);
        }
    }

    private void aplicarSenhaCloudPendenteParaTeste(Conexao conexao) {
        if (senhaDefinida(conexao.getDb_cloud_password())) {
            return;
        }

        String senhaPendente = obterSenhaCloudPendente();
        if (senhaPendente != null) {
            conexao.setDb_cloud_password(senhaPendente);
        }
    }

    private void registrarSenhaCloudPendente(String senha) {
        certificadoCloudSenhaPendente.put(chaveCertificadoPendente(), senha);
    }

    private String consumirSenhaCloudPendente() {
        return certificadoCloudSenhaPendente.remove(chaveCertificadoPendente());
    }

    private String obterSenhaCloudPendente() {
        return certificadoCloudSenhaPendente.get(chaveCertificadoPendente());
    }

    private String chaveCertificadoPendente() {
        return exigirIdEmpresa() + ":" + resolverUsuario().getId();
    }

    private void preencherConexaoParaTeste(Conexao conexao, ConexaoDTO dto) {
        if (dto.getCloud() != null) {
            if (dto.getCloud().getDb_cloud_host() != null) {
                conexao.setDb_cloud_host(dto.getCloud().getDb_cloud_host());
            }
            if (dto.getCloud().getDb_cloud_port() != null) {
                conexao.setDb_cloud_port(dto.getCloud().getDb_cloud_port());
            }
            if (dto.getCloud().getDb_cloud_user() != null) {
                conexao.setDb_cloud_user(dto.getCloud().getDb_cloud_user());
            }
            if (senhaInformada(dto.getCloud().getDb_cloud_password())) {
                conexao.setDb_cloud_password(dto.getCloud().getDb_cloud_password());
            }
            if (dto.getCloud().getFl_admin() != null) {
                conexao.setFl_admin(dto.getCloud().getFl_admin());
            }
            conexao.setDb_cloud_ssh_enabled(Boolean.TRUE.equals(dto.getCloud().getDb_cloud_ssh_enabled()));
            if (dto.getCloud().getDb_cloud_ssh_host() != null) {
                conexao.setDb_cloud_ssh_host(dto.getCloud().getDb_cloud_ssh_host());
            }
            if (dto.getCloud().getDb_cloud_ssh_port() != null) {
                conexao.setDb_cloud_ssh_port(dto.getCloud().getDb_cloud_ssh_port());
            }
            if (dto.getCloud().getDb_cloud_ssh_user() != null) {
                conexao.setDb_cloud_ssh_user(dto.getCloud().getDb_cloud_ssh_user());
            }
            if (senhaInformada(dto.getCloud().getDb_cloud_ssh_password())) {
                conexao.setDb_cloud_ssh_password(dto.getCloud().getDb_cloud_ssh_password());
            }
        }

        if (dto.getLocal() != null) {
            if (dto.getLocal().getDb_local_host() != null) {
                conexao.setDb_local_host(dto.getLocal().getDb_local_host());
            }
            if (dto.getLocal().getDb_local_port() != null) {
                conexao.setDb_local_port(dto.getLocal().getDb_local_port());
            }
            if (dto.getLocal().getDb_local_user() != null) {
                conexao.setDb_local_user(dto.getLocal().getDb_local_user());
            }
            if (senhaInformada(dto.getLocal().getDb_local_password())) {
                conexao.setDb_local_password(dto.getLocal().getDb_local_password());
            }
            conexao.setDb_local_ssh_enabled(Boolean.TRUE.equals(dto.getLocal().getDb_local_ssh_enabled()));
            if (dto.getLocal().getDb_local_ssh_host() != null) {
                conexao.setDb_local_ssh_host(dto.getLocal().getDb_local_ssh_host());
            }
            if (dto.getLocal().getDb_local_ssh_port() != null) {
                conexao.setDb_local_ssh_port(dto.getLocal().getDb_local_ssh_port());
            }
            if (dto.getLocal().getDb_local_ssh_user() != null) {
                conexao.setDb_local_ssh_user(dto.getLocal().getDb_local_ssh_user());
            }
            if (senhaInformada(dto.getLocal().getDb_local_ssh_password())) {
                conexao.setDb_local_ssh_password(dto.getLocal().getDb_local_ssh_password());
            }
        }
    }

    private boolean senhaInformada(String senha) {
        return senha != null && !senha.isBlank() && !"*****".equals(senha.trim());
    }

    private String montarMensagemTeste(
            Map<String, Object> cloud,
            Map<String, Object> local,
            boolean cloudOk,
            boolean localOk) {
        if (cloudOk && localOk) {
            return "Cloud e Local conectados com sucesso.";
        }

        if (cloudOk) {
            return "Cloud OK. Local: " + local.get("message");
        }

        if (localOk) {
            return "Local OK. Cloud: " + cloud.get("message");
        }

        return "Cloud: " + cloud.get("message") + " | Local: " + local.get("message");
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

    private Conexao buscarConexaoObrigatoria(String id, String idEmpresa, String idUsuario) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id da conexao nao informado.");
        }

        return repository.findByIdAndId_empresaAndIdUsuario(id, idEmpresa, idUsuario)
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
            if (dto.getCloud().getDb_cloud_password() != null
                    && !dto.getCloud().getDb_cloud_password().isBlank()) {
                conexao.setDb_cloud_password(dto.getCloud().getDb_cloud_password());
            }
            conexao.setFl_admin(dto.getCloud().getFl_admin());
            conexao.setDb_cloud_ssh_enabled(Boolean.TRUE.equals(dto.getCloud().getDb_cloud_ssh_enabled()));
            conexao.setDb_cloud_ssh_host(dto.getCloud().getDb_cloud_ssh_host());
            conexao.setDb_cloud_ssh_port(dto.getCloud().getDb_cloud_ssh_port());
            conexao.setDb_cloud_ssh_user(dto.getCloud().getDb_cloud_ssh_user());
            if (dto.getCloud().getDb_cloud_ssh_password() != null
                    && !dto.getCloud().getDb_cloud_ssh_password().isBlank()) {
                conexao.setDb_cloud_ssh_password(dto.getCloud().getDb_cloud_ssh_password());
            }
        }

        if (dto.getLocal() != null) {
            conexao.setDb_local_host(dto.getLocal().getDb_local_host());
            conexao.setDb_local_port(dto.getLocal().getDb_local_port());
            conexao.setDb_local_user(dto.getLocal().getDb_local_user());
            if (dto.getLocal().getDb_local_password() != null
                    && !dto.getLocal().getDb_local_password().isBlank()) {
                conexao.setDb_local_password(dto.getLocal().getDb_local_password());
            }
            conexao.setDb_local_ssh_enabled(Boolean.TRUE.equals(dto.getLocal().getDb_local_ssh_enabled()));
            conexao.setDb_local_ssh_host(dto.getLocal().getDb_local_ssh_host());
            conexao.setDb_local_ssh_port(dto.getLocal().getDb_local_ssh_port());
            conexao.setDb_local_ssh_user(dto.getLocal().getDb_local_ssh_user());
            if (dto.getLocal().getDb_local_ssh_password() != null
                    && !dto.getLocal().getDb_local_ssh_password().isBlank()) {
                conexao.setDb_local_ssh_password(dto.getLocal().getDb_local_ssh_password());
            }
        }
    }

    private void desmarcarPadrao(String idEmpresa, String idUsuario, String idIgnorado) {
        List<Conexao> conexoes = repository.findById_empresaAndIdUsuarioAndFl_ativoTrue(idEmpresa, idUsuario);

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

    private void promoverPrimeiraConexaoAtiva(String idEmpresa, String idUsuario) {
        List<Conexao> conexoes = repository.findById_empresaAndIdUsuarioAndFl_ativoTrue(idEmpresa, idUsuario);

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
        cloud.setDb_cloud_password(null);
        cloud.setFl_cloud_password_defined(senhaDefinida(conexao.getDb_cloud_password()));
        cloud.setFl_admin(conexao.getFl_admin());
        cloud.setDb_cloud_ssh_enabled(conexao.getDb_cloud_ssh_enabled());
        cloud.setDb_cloud_ssh_host(conexao.getDb_cloud_ssh_host());
        cloud.setDb_cloud_ssh_port(conexao.getDb_cloud_ssh_port());
        cloud.setDb_cloud_ssh_user(conexao.getDb_cloud_ssh_user());
        cloud.setDb_cloud_ssh_password(null);
        cloud.setFl_cloud_ssh_password_defined(senhaDefinida(conexao.getDb_cloud_ssh_password()));

        dto.setCloud(cloud);

        ConexaoDTO.LocalConnection local = new ConexaoDTO.LocalConnection();
        local.setDb_local_host(conexao.getDb_local_host());
        local.setDb_local_port(conexao.getDb_local_port());
        local.setDb_local_user(conexao.getDb_local_user());
        local.setDb_local_password(conexao.getDb_local_password());
        local.setDb_local_ssh_enabled(conexao.getDb_local_ssh_enabled());
        local.setDb_local_ssh_host(conexao.getDb_local_ssh_host());
        local.setDb_local_ssh_port(conexao.getDb_local_ssh_port());
        local.setDb_local_ssh_user(conexao.getDb_local_ssh_user());
        local.setDb_local_ssh_password(null);
        local.setFl_local_ssh_password_defined(senhaDefinida(conexao.getDb_local_ssh_password()));

        dto.setLocal(local);

        return dto;
    }

    private boolean senhaDefinida(String senha) {
        return senha != null && !senha.isBlank();
    }

}
