package com.api_sincdb.controller.usuario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

import com.api_sincdb.domain.role.model.Role;
import com.api_sincdb.domain.role.repository.RoleRepository;
import com.api_sincdb.domain.usuario.dto.UsuarioDTO;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.domain.usuario.service.UsuarioService;
import com.api_sincdb.enums.TipoRole;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController /* ARQUITETURA REST */
@RequestMapping(value = "/usuario")
@Tag(name = "Usuarios")
public class UsuarioController {
	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UsuarioService service;

	@GetMapping("/listar")
	public Page<Usuario> listar(Pageable pageable) {
		return usuarioRepository.findAll(pageable);
	}

	@GetMapping(value = "/obter-por-login/{login}", produces = "application/json")
	public ResponseEntity<?> usuarioPorLogin(@PathVariable String login) {

		Usuario usuario = usuarioRepository.findByLogin(login);

		return new ResponseEntity<>(usuario, HttpStatus.OK);
	}

	@GetMapping(value = "/{id}", produces = "application/json")
	@CacheEvict(value = "cacheuser", allEntries = true)
	@CachePut("cacheuser")
	public ResponseEntity<UsuarioDTO> init(@PathVariable String id) {

		Optional<Usuario> usuario = usuarioRepository.findById(id);
		return new ResponseEntity<UsuarioDTO>(new UsuarioDTO(usuario.get()), HttpStatus.OK);
	}

	@GetMapping(value = "/", produces = "application/json")
	@CacheEvict(value = "cacheusuario", allEntries = true) // remover cache nao utilizado
	@CachePut("cacheusuario") // atualizar cache
	public ResponseEntity<List<?>> usuario() throws InterruptedException {
		List<Usuario> usuarios = (List<Usuario>) usuarioRepository.findAll(); // Consulta todos os usuários

		// Mapeia cada objeto Usuario para UsuarioDTO
		List<UsuarioDTO> usuariosDTO = usuarios.stream()
				.map(usuario -> new UsuarioDTO(usuario)) // Usando o construtor para mapear
				.collect(Collectors.toList()); // Coleta todos os DTOs em uma lista

		return new ResponseEntity<>(usuariosDTO, HttpStatus.OK);
	}

	@PostMapping(value = "/", produces = "application/json")
	// @PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> cadastrar(@RequestBody Usuario usuario) throws Exception {

		service.salvar(usuario);
		return ResponseEntity.ok(Map.of("message", "Registro salvo com sucesso!"));

	}

	@PutMapping(value = "/", produces = "application/json")
	// @PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> atualizar(@RequestBody Usuario usuario) throws Exception {

		Usuario userTemporario = usuarioRepository.findByLogin(usuario.getLogin());

		if (userTemporario == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\": \"Usuário não encontrado.\"}");
		}

		if (usuario.getSenha() == null || usuario.getSenha().trim().isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("{\"error\": \"Senha inválida. Não pode ser vazia.\"}");
		}

		if (!userTemporario.getSenha().equals(usuario.getSenha())) {
			String senhacriptografada = new BCryptPasswordEncoder().encode(usuario.getSenha());
			usuario.setSenha(senhacriptografada);

		}

		String nomeRole = usuario.getRoles().iterator().next().getNomeRole();
		Role roleUser = roleRepository.findByNomeRole(nomeRole);
		if (roleUser == null) {
			roleUser = new Role();
			roleUser.setNomeRole(nomeRole);
			roleRepository.save(roleUser);
		}

		usuario.getRoles().clear();
		usuario.getRoles().add(roleUser);

		service.salvar(usuario);

		return ResponseEntity.ok(Map.of("message", "Registro atualizado com sucesso!"));
	}

	@DeleteMapping(value = "/{id}", produces = "application/json")
	// @PreAuthorize("hasAnyRole('ADMIN')")
	public ResponseEntity<?> delete(@PathVariable String id) throws Exception {

		service.excluir(id);

		return ResponseEntity.ok(Map.of("message", "Removido com sucesso!"));
	}

}