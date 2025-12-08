package com.api_sincdb.domain.usuario.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.usuario.model.Usuario;

public class UsuarioDTO implements Serializable {

	private String userId;
	private String userLogin;
	private String userNome;
	private String userSenha;
	private List<String> roles = new ArrayList<>();
	private List<UsuarioEmpresa> itensUsuarioEmpresa = new ArrayList<>();

	public UsuarioDTO(Usuario usuario) {
		this.userId = usuario.getId();
		this.userLogin = usuario.getLogin();
		this.userNome = usuario.getNome();
		this.userSenha = usuario.getSenha();

		if (usuario.getRoles() != null && !usuario.getRoles().isEmpty()) {
			this.roles = usuario.getRoles().stream()
					.map(role -> role.getNomeRole())
					.collect(Collectors.toList());
		}

		this.itensUsuarioEmpresa = usuario.getItensUsuarioEmpresa();
	}

	public List<UsuarioEmpresa> getItensUsuarioEmpresa() {
		return itensUsuarioEmpresa;
	}

	public void setItensUsuarioEmpresa(List<UsuarioEmpresa> itensUsuarioEmpresa) {
		this.itensUsuarioEmpresa = itensUsuarioEmpresa;
	}


	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	public String getUserLogin() {
		return userLogin;
	}

	public void setUserLogin(String userLogin) {
		this.userLogin = userLogin;
	}

	public String getUserNome() {
		return userNome;
	}

	public void setUserNome(String userNome) {
		this.userNome = userNome;
	}

	public String getUserSenha() {
		return userSenha;
	}

	public void setUserSenha(String userSenha) {
		this.userSenha = userSenha;
	}
}
