package com.api_sincdb.controller.conexao;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import javax.swing.Spring;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

        Conexao conexaoModel = new Conexao();


        Usuario user = usuarioRepository.findByLogin(conexaoDTO.getLogin());

        if (user == null)  return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario não encontrado."));
   
        conexaoModel.setDb_cloud_host(conexaoDTO.getCloud().getDb_cloud_host());
        conexaoModel.setDb_cloud_port(conexaoDTO.getCloud().getDb_cloud_port());
        conexaoModel.setDb_cloud_user(conexaoDTO.getCloud().getDb_cloud_user());
        conexaoModel.setDb_cloud_password(conexaoDTO.getCloud().getDb_cloud_password());

        conexaoModel.setDb_local_host(conexaoDTO.getLocal().getDb_local_host());
        conexaoModel.setDb_local_port(conexaoDTO.getLocal().getDb_local_port());
        conexaoModel.setDb_local_user(conexaoDTO.getLocal().getDb_local_user());
        conexaoModel.setDb_local_password(conexaoDTO.getLocal().getDb_local_password());

        conexaoModel.setFl_admin(conexaoDTO.getCloud().getFl_admin());
        conexaoModel.setIdUsuario(user.getId());

        repository.save(conexaoModel);

        return new ResponseEntity<Conexao>(conexaoModel, HttpStatus.OK);
    }

    @PutMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> atualizar(@RequestBody ConexaoDTO conexaoDTO) {

        Usuario user = usuarioRepository.findByLogin(conexaoDTO.getLogin());

        if (user == null)  return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario não encontrado."));

        conexaoDTO.setIdUsuario(user.getId());

        Optional<Conexao> conexaoModelOptional = repository.findById(conexaoDTO.getId());

        if (!conexaoModelOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"erro\": \"Conexão não encontrada para atualização!\"}");
        }

        Boolean fl_admin = conexaoDTO.getCloud().getFl_admin();
        Boolean fl_adminantigo = conexaoModelOptional.get().getFl_admin();
        Conexao conexaoModel = conexaoModelOptional.get();

        conexaoModel.setDb_cloud_host(conexaoDTO.getCloud().getDb_cloud_host());
        conexaoModel.setDb_cloud_port(conexaoDTO.getCloud().getDb_cloud_port());
        conexaoModel.setDb_cloud_user(conexaoDTO.getCloud().getDb_cloud_user());
        conexaoModel.setDb_cloud_password(conexaoDTO.getCloud().getDb_cloud_password());
        conexaoModel.setFl_admin(fl_admin);

        if (fl_admin == false && fl_adminantigo == true) {

            if (conexaoModelOptional.get().getDb_cloud_user().contains(conexaoDTO.getCloud().getDb_cloud_user())
                    || conexaoModelOptional.get().getDb_cloud_password()
                            .contains(conexaoDTO.getCloud().getDb_cloud_password())) {
                conexaoModel.setDb_cloud_user("");
                conexaoModel.setDb_cloud_password("");
            }

        }

        conexaoModel.setDb_local_host(conexaoDTO.getLocal().getDb_local_host());
        conexaoModel.setDb_local_port(conexaoDTO.getLocal().getDb_local_port());
        conexaoModel.setDb_local_user(conexaoDTO.getLocal().getDb_local_user());
        conexaoModel.setDb_local_password(conexaoDTO.getLocal().getDb_local_password());

        repository.save(conexaoModel);

        ConexaoBanco.fecharTodos();

        return new ResponseEntity<Conexao>(conexaoModel, HttpStatus.OK);
    }

    @GetMapping(value = "/{login}", produces = "application/json")
    public ResponseEntity<?> recuperarConexao(@PathVariable String login) {

        Usuario user = usuarioRepository.findByLogin(login);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario não encontrado."));
        }

        Conexao objeto = repository.findFirstByIdUsuario(user.getId());

        if (objeto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"erro\": \"Conexao não encontrada!\"}");
        }

        ConexaoDTO conexaoDTO = new ConexaoDTO();

        ConexaoDTO.CloudConnection cloud = new ConexaoDTO.CloudConnection();
        cloud.setDb_cloud_host(objeto.getDb_cloud_host());
        cloud.setDb_cloud_port(objeto.getDb_cloud_port());
        cloud.setDb_cloud_user(objeto.getDb_cloud_user());
        cloud.setDb_cloud_password(objeto.getDb_cloud_password());
        cloud.setFl_admin(objeto.getFl_admin());

        ConexaoDTO.LocalConnection local = new ConexaoDTO.LocalConnection();
        local.setDb_local_host(objeto.getDb_local_host());
        local.setDb_local_port(objeto.getDb_local_port());
        local.setDb_local_user(objeto.getDb_local_user());
        local.setDb_local_password(objeto.getDb_local_password());

        conexaoDTO.setId(objeto.getId());
        conexaoDTO.setCloud(cloud);
        conexaoDTO.setLocal(local);
        return ResponseEntity.ok(conexaoDTO);
    }

    @PostMapping("/certificado/upload/{login}")
    public ResponseEntity<?> uploadCertificado(@PathVariable String login,
            @RequestParam("arquivo") MultipartFile arquivo) {
        try {

            Usuario user = usuarioRepository.findByLogin(login);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", "Usuario não encontrado."));
            }
            String idUsuario = user.getId();

            // byte[] chave = CriptoUtils.gerarChave256(System.getenv("SEGREDO_CONFIG"));
            String segredo = "wD7#G2k!91zL*qpB3VmX8eTR";

            byte[] chave = CriptoUtils.gerarChave256(segredo);
            String conteudo = new String(arquivo.getBytes(), StandardCharsets.UTF_8);

            String jsonDescriptografado = CriptoUtils.descriptografar(conteudo, chave);

            JSONObject obj = new JSONObject(jsonDescriptografado);

            if (!obj.has("user") || !obj.has("password")) {
                return ResponseEntity.badRequest().body("Certificado inválido.");
            }

            Optional<Conexao> conexaoModelOptional = Optional
                    .ofNullable(repository.findFirstByIdUsuario(idUsuario));

            if (!conexaoModelOptional.isPresent()) {

                Conexao conexaoModel = new Conexao();
                conexaoModel.setDb_cloud_host(obj.getString("host"));
                conexaoModel.setDb_cloud_port(obj.getString("port"));
                conexaoModel.setDb_cloud_user(obj.getString("user"));
                conexaoModel.setDb_cloud_password(obj.getString("password"));
                conexaoModel.setFl_admin(true);
                conexaoModel.setIdUsuario(idUsuario);
                repository.save(conexaoModel);

            } else {
                Conexao conexaoModel = conexaoModelOptional.get();
                conexaoModel.setDb_cloud_host(obj.getString("host"));
                conexaoModel.setDb_cloud_port(obj.getString("port"));
                conexaoModel.setDb_cloud_user(obj.getString("user"));
                conexaoModel.setDb_cloud_password(obj.getString("password"));
                conexaoModel.setFl_admin(true);
                conexaoModel.setIdUsuario(idUsuario);
                repository.save(conexaoModel);
            }

            return ResponseEntity.ok("Certificado válido e processado com sucesso.");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Falha ao processar o certificado: " + e.getMessage());
        }
    }

    @GetMapping(value = "/certificado", produces = "application/json")
    public ResponseEntity<?> obterCertificado() throws Exception {

        String segredo = "wD7#G2k!91zL*qpB3VmX8eTR";

        Properties props = LeitorConfigSegura.carregarConfiguracao("./config.enc", segredo);

        ConexaoDTO.CloudConnection cloud = new ConexaoDTO.CloudConnection();
        cloud.setDb_cloud_host(props.getProperty("host"));
        cloud.setDb_cloud_port(props.getProperty("port"));
        cloud.setDb_cloud_user(props.getProperty("user"));
        cloud.setDb_cloud_password(props.getProperty("password"));

        return ResponseEntity.ok(props);
    }

}
