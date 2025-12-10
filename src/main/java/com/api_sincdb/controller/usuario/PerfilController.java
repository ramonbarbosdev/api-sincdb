package com.api_sincdb.controller.usuario;

import java.io.IOException;
import java.util.Map;

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

import com.api_sincdb.domain.usuario.dto.PerfilDTO;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.domain.usuario.service.AnexoService;
import com.api_sincdb.domain.usuario.service.UsuarioService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/perfil")
@Tag(name = "Perfil")
public class PerfilController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService service;

    @Autowired
    private AnexoService anexoService;

    @GetMapping(value = "/{login}", produces = "application/json")
    public ResponseEntity<?> obterPerfil(@PathVariable String login) throws Exception {

        Usuario userTemporario = usuarioRepository.findByLogin(login);

        if (userTemporario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"message\": \"Usuário não encontrado.\"}");
        }

        String permissao = userTemporario.getRoles().iterator().next().getNomeRole();

        PerfilDTO perfilDTO = new PerfilDTO();
        perfilDTO.setId(userTemporario.getId());
        perfilDTO.setLogin(userTemporario.getLogin());
        perfilDTO.setNome(userTemporario.getNome());
        // perfilDTO.setSenha(userTemporario.getSenha());
        perfilDTO.setRole(permissao);
        perfilDTO.setCargo("");

        return new ResponseEntity<>(perfilDTO, HttpStatus.OK);
    }

    @PutMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> atualizarPerfil(@RequestBody PerfilDTO usuario) throws Exception {

        Usuario userTemporario = usuarioRepository.findByLogin(usuario.getLogin());

        if (userTemporario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"message\": \"Usuário não encontrado.\"}");
        }

        userTemporario.setNome(usuario.getNome());
        service.inserirSenhaCriptografada(userTemporario, usuario.getSenha());

        Usuario usuarioSalvo = usuarioRepository.save(userTemporario);

        return new ResponseEntity<>(Map.of("message", "Atualizado com sucesso!"), HttpStatus.OK);
    }

    // @PostMapping("/upload-foto/{id}")
    // public ResponseEntity<?> uploadFotoPerfil(@PathVariable Long id, @RequestParam MultipartFile file) {
    //     try {

    //         String urlCompleta = anexoService.uploadFoto(id, "perfil", file);

    //         Usuario usuario = usuarioRepository.findById(id)
    //                 .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    //         usuario.setImg(urlCompleta);
    //         usuarioRepository.save(usuario);

    //         return new ResponseEntity<>(Map.of("url", urlCompleta, "nome", usuario.getNome()), HttpStatus.OK);

    //     } catch (IOException e) {
    //         return new ResponseEntity<>(Map.of("message", "Erro ao salvar imagem"), HttpStatus.INTERNAL_SERVER_ERROR);
    //     }
    // }

    // @DeleteMapping("/remover-foto/{id}")
    // public ResponseEntity<?> deleteFotoPerfil(@PathVariable Long id) {

    //     Usuario usuario = usuarioRepository.findById(id)
    //             .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

    //     if(usuario.getImg() ==null) return null;

    //     String caminhoImage = "perfil";
    //     anexoService.deletarPorIdModel(id, caminhoImage);

    //     usuario.setImg(null);
    //     usuarioRepository.save(usuario);

    //     return new ResponseEntity<>(Map.of("message", "Deletado com sucesso"), HttpStatus.OK);
    // }
}
