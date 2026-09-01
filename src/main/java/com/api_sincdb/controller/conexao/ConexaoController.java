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
import com.api_sincdb.domain.conexao.dto.ConexaoResponseDTO;
import com.api_sincdb.domain.conexao.model.Conexao;
import com.api_sincdb.domain.conexao.repository.ConexaoRepository;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.util.CriptoUtils;
import com.api_sincdb.util.LeitorConfigSegura;
@RestController
@RequestMapping("/conexao")
public class ConexaoController {

    private final ConexaoService conexaoService;

    public ConexaoController(ConexaoService conexaoService) {
        this.conexaoService = conexaoService;
    }

    @PostMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> salvar(@RequestBody ConexaoDTO dto) {
        return ResponseEntity.ok(conexaoService.salvar(dto));
    }

    @PutMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> atualizar(@RequestBody ConexaoDTO dto) {
        return ResponseEntity.ok(conexaoService.atualizar(dto));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<?> listarConexoes() {
        return ResponseEntity.ok(conexaoService.listarConexoes());
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<?> recuperarConexao(@PathVariable String id) {
        return ResponseEntity.ok(conexaoService.recuperarConexao(id));
    }

    @PutMapping(value = "/{id}/padrao", produces = "application/json")
    public ResponseEntity<?> definirPadrao(@PathVariable String id) {
        return ResponseEntity.ok(conexaoService.definirPadrao(id));
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<?> remover(@PathVariable String id) {
        conexaoService.remover(id);
        return ResponseEntity.ok(Map.of("message", "Conexao removida com sucesso."));
    }

    @PostMapping(value = "/testar", produces = "application/json")
    public ResponseEntity<?> testar(@RequestBody ConexaoDTO dto) {
        return ResponseEntity.ok(conexaoService.testarConexao(dto));
    }

    @PostMapping("/certificado/upload")
    public ResponseEntity<?> uploadCertificado(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(conexaoService.uploadCertificado(arquivo));
    }

    @GetMapping(value = "/certificado", produces = "application/json")
    public ResponseEntity<?> obterCertificado() throws Exception {
        return ResponseEntity.ok(conexaoService.obterCertificado());
    }
}