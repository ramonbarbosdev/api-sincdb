package com.api_sincdb.controller.conexao;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.context.TenantRuntimeContext;
import com.api_sincdb.domain.conexao.dto.ConexaoDTO;
import com.api_sincdb.domain.conexao.model.Conexao;
import com.api_sincdb.domain.conexao.repository.ConexaoRepository;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.util.CriptoUtils;
import com.api_sincdb.util.LeitorConfigSegura;

@RestController
@RequestMapping("/conexao")
public class ConexaoController {

    @Autowired
    private ConexaoRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> salvar(@RequestBody ConexaoDTO conexaoDTO) {
        Usuario user = resolverUsuario(conexaoDTO.getLogin());

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario nao encontrado."));
        }

        String idEmpresa = resolverIdEmpresa(conexaoDTO);
        String idTenant = resolverIdTenant(conexaoDTO);

        if (idEmpresa == null || idEmpresa.isBlank() || idTenant == null || idTenant.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Organizacao ativa nao encontrada no token."));
        }

        Conexao conexaoModel = new Conexao();
        preencherConexao(conexaoModel, conexaoDTO);
        conexaoModel.setIdUsuario(user.getId());
        conexaoModel.setId_empresa(idEmpresa);
        conexaoModel.setId_tenant(idTenant);
        conexaoModel.setFl_ativo(conexaoDTO.getFl_ativo() == null ? true : conexaoDTO.getFl_ativo());

        boolean primeiraConexao = !repository.existsById_empresaAndFl_ativoTrue(idEmpresa);
        boolean marcarPadrao = primeiraConexao || Boolean.TRUE.equals(conexaoDTO.getFl_padrao());
        conexaoModel.setFl_padrao(marcarPadrao);

        if (marcarPadrao) {
            desmarcarPadrao(idEmpresa, null);
        }

        repository.save(conexaoModel);
        ConexaoBanco.fecharTodos();

        return new ResponseEntity<Conexao>(conexaoModel, HttpStatus.OK);
    }

    @PutMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> atualizar(@RequestBody ConexaoDTO conexaoDTO) {
        String idEmpresa = resolverIdEmpresa(conexaoDTO);
        Optional<Conexao> conexaoModelOptional = buscarConexaoDaOrganizacao(conexaoDTO.getId(), idEmpresa);

        if (conexaoModelOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("erro", "Conexao nao encontrada para atualizacao."));
        }

        Conexao conexaoModel = conexaoModelOptional.get();
        Boolean flAdminNovo = conexaoDTO.getCloud().getFl_admin();
        Boolean flAdminAntigo = conexaoModel.getFl_admin();

        preencherConexao(conexaoModel, conexaoDTO);

        if (Boolean.FALSE.equals(flAdminNovo) && Boolean.TRUE.equals(flAdminAntigo)) {
            boolean mesmoUsuarioCloud = conexaoModelOptional.get().getDb_cloud_user() != null
                    && conexaoDTO.getCloud().getDb_cloud_user() != null
                    && conexaoModelOptional.get().getDb_cloud_user().contains(conexaoDTO.getCloud().getDb_cloud_user());

            boolean mesmaSenhaCloud = conexaoModelOptional.get().getDb_cloud_password() != null
                    && conexaoDTO.getCloud().getDb_cloud_password() != null
                    && conexaoModelOptional.get().getDb_cloud_password()
                            .contains(conexaoDTO.getCloud().getDb_cloud_password());

            if (mesmoUsuarioCloud || mesmaSenhaCloud) {
                conexaoModel.setDb_cloud_user("");
                conexaoModel.setDb_cloud_password("");
            }
        }

        if (conexaoDTO.getFl_ativo() != null) {
            conexaoModel.setFl_ativo(conexaoDTO.getFl_ativo());
        }

        if (Boolean.TRUE.equals(conexaoDTO.getFl_padrao())) {
            desmarcarPadrao(conexaoModel.getId_empresa(), conexaoModel.getId());
            conexaoModel.setFl_padrao(true);
        }

        repository.save(conexaoModel);
        ConexaoBanco.fecharTodos();

        return new ResponseEntity<Conexao>(conexaoModel, HttpStatus.OK);
    }

    @GetMapping(value = "/{login}", produces = "application/json")
    public ResponseEntity<?> listarConexoes(@PathVariable String login) {
        Usuario user = usuarioRepository.findByLogin(login);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario nao encontrado."));
        }

        String idEmpresa = TenantRuntimeContext.getIdEmpresa();

        if (idEmpresa != null && !idEmpresa.isBlank()) {
            return ResponseEntity.ok(repository.findById_empresaAndFl_ativoTrue(idEmpresa));
        }

        return ResponseEntity.ok(repository.findByIdUsuario(user.getId()));
    }

    @GetMapping(value = "/{login}/{id}", produces = "application/json")
    public ResponseEntity<?> recuperarConexao(@PathVariable String login, @PathVariable String id) {
        Usuario user = usuarioRepository.findByLogin(login);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario nao encontrado."));
        }

        String idEmpresa = TenantRuntimeContext.getIdEmpresa();
        Optional<Conexao> conexao = buscarConexaoDaOrganizacao(id, idEmpresa);

        if (conexao.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Conexao nao encontrada."));
        }

        return ResponseEntity.ok(conexao.get());
    }

    @PutMapping(value = "/{login}/{id}/padrao", produces = "application/json")
    public ResponseEntity<?> definirPadrao(@PathVariable String login, @PathVariable String id) {
        Usuario user = usuarioRepository.findByLogin(login);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario nao encontrado."));
        }

        String idEmpresa = TenantRuntimeContext.getIdEmpresa();
        Optional<Conexao> conexaoOptional = buscarConexaoDaOrganizacao(id, idEmpresa);

        if (conexaoOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Conexao nao encontrada."));
        }

        Conexao conexao = conexaoOptional.get();
        desmarcarPadrao(conexao.getId_empresa(), conexao.getId());
        conexao.setFl_padrao(true);
        repository.save(conexao);
        ConexaoBanco.fecharTodos();

        return ResponseEntity.ok(conexao);
    }

    @DeleteMapping(value = "/{login}/{id}", produces = "application/json")
    public ResponseEntity<?> remover(@PathVariable String login, @PathVariable String id) {
        Usuario user = usuarioRepository.findByLogin(login);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario nao encontrado."));
        }

        String idEmpresa = TenantRuntimeContext.getIdEmpresa();
        Optional<Conexao> conexaoOptional = buscarConexaoDaOrganizacao(id, idEmpresa);

        if (conexaoOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Conexao nao encontrada."));
        }

        Conexao conexao = conexaoOptional.get();
        boolean eraPadrao = Boolean.TRUE.equals(conexao.getFl_padrao());
        conexao.setFl_ativo(false);
        conexao.setFl_padrao(false);
        repository.save(conexao);

        if (eraPadrao) {
            promoverPrimeiraConexaoAtiva(conexao.getId_empresa());
        }

        ConexaoBanco.fecharTodos();
        return ResponseEntity.ok(Map.of("message", "Conexao removida com sucesso."));
    }

    @PostMapping("/certificado/upload/{login}")
    public ResponseEntity<?> uploadCertificado(@PathVariable String login,
            @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            Usuario user = usuarioRepository.findByLogin(login);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario nao encontrado."));
            }

            String idEmpresa = TenantRuntimeContext.getIdEmpresa();
            String idTenant = TenantRuntimeContext.getIdTenant();

            if (idEmpresa == null || idEmpresa.isBlank() || idTenant == null || idTenant.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("erro", "Organizacao ativa nao encontrada no token."));
            }

            String segredo = "wD7#G2k!91zL*qpB3VmX8eTR";
            byte[] chave = CriptoUtils.gerarChave256(segredo);
            String conteudo = new String(arquivo.getBytes(), StandardCharsets.UTF_8);
            String jsonDescriptografado = CriptoUtils.descriptografar(conteudo, chave);

            JSONObject obj = new JSONObject(jsonDescriptografado);

            if (!obj.has("user") || !obj.has("password")) {
                return ResponseEntity.badRequest().body("Certificado invalido.");
            }

            Conexao conexaoModel = repository.findFirstById_empresaAndFl_padraoTrueAndFl_ativoTrue(idEmpresa)
                    .orElseGet(Conexao::new);

            conexaoModel.setDb_cloud_host(obj.getString("host"));
            conexaoModel.setDb_cloud_port(obj.getString("port"));
            conexaoModel.setDb_cloud_user(obj.getString("user"));
            conexaoModel.setDb_cloud_password(obj.getString("password"));
            conexaoModel.setFl_admin(true);
            conexaoModel.setIdUsuario(user.getId());
            conexaoModel.setId_empresa(idEmpresa);
            conexaoModel.setId_tenant(idTenant);
            conexaoModel.setFl_ativo(true);
            conexaoModel.setFl_padrao(true);

            if (conexaoModel.getNm_conexao() == null || conexaoModel.getNm_conexao().isBlank()) {
                conexaoModel.setNm_conexao("Conexao principal");
            }

            desmarcarPadrao(idEmpresa, conexaoModel.getId());
            repository.save(conexaoModel);
            ConexaoBanco.fecharTodos();

            return ResponseEntity.ok("Certificado valido e processado com sucesso.");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Falha ao processar o certificado: " + e.getMessage());
        }
    }

    @GetMapping(value = "/certificado", produces = "application/json")
    public ResponseEntity<?> obterCertificado() throws Exception {
        String segredo = "wD7#G2k!91zL*qpB3VmX8eTR";
        Properties props = LeitorConfigSegura.carregarConfiguracao("./config.enc", segredo);

        return ResponseEntity.ok(props);
    }

    private Usuario resolverUsuario(String login) {
        String idUsuarioContexto = TenantRuntimeContext.getIdUsuario();

        if (idUsuarioContexto != null && !idUsuarioContexto.isBlank()) {
            return usuarioRepository.findById(idUsuarioContexto).orElse(null);
        }

        if (login == null || login.isBlank()) {
            return null;
        }

        return usuarioRepository.findByLogin(login);
    }

    private String resolverIdEmpresa(ConexaoDTO conexaoDTO) {
        String idEmpresa = TenantRuntimeContext.getIdEmpresa();
        return idEmpresa != null && !idEmpresa.isBlank() ? idEmpresa : conexaoDTO.getId_empresa();
    }

    private String resolverIdTenant(ConexaoDTO conexaoDTO) {
        String idTenant = TenantRuntimeContext.getIdTenant();
        return idTenant != null && !idTenant.isBlank() ? idTenant : conexaoDTO.getId_tenant();
    }

    private Optional<Conexao> buscarConexaoDaOrganizacao(String id, String idEmpresa) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        if (idEmpresa != null && !idEmpresa.isBlank()) {
            return repository.findByIdAndId_empresa(id, idEmpresa);
        }

        return repository.findById(id);
    }

    private void preencherConexao(Conexao conexaoModel, ConexaoDTO conexaoDTO) {
        if (conexaoDTO.getNm_conexao() != null) {
            conexaoModel.setNm_conexao(conexaoDTO.getNm_conexao());
        }

        if (conexaoDTO.getCloud() != null) {
            conexaoModel.setDb_cloud_host(conexaoDTO.getCloud().getDb_cloud_host());
            conexaoModel.setDb_cloud_port(conexaoDTO.getCloud().getDb_cloud_port());
            conexaoModel.setDb_cloud_user(conexaoDTO.getCloud().getDb_cloud_user());
            conexaoModel.setDb_cloud_password(conexaoDTO.getCloud().getDb_cloud_password());
            conexaoModel.setFl_admin(conexaoDTO.getCloud().getFl_admin());
        }

        if (conexaoDTO.getLocal() != null) {
            conexaoModel.setDb_local_host(conexaoDTO.getLocal().getDb_local_host());
            conexaoModel.setDb_local_port(conexaoDTO.getLocal().getDb_local_port());
            conexaoModel.setDb_local_user(conexaoDTO.getLocal().getDb_local_user());
            conexaoModel.setDb_local_password(conexaoDTO.getLocal().getDb_local_password());
        }
    }

    private void desmarcarPadrao(String idEmpresa, String idIgnorado) {
        if (idEmpresa == null || idEmpresa.isBlank()) {
            return;
        }

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
}
