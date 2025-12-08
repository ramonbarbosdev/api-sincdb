package com.api_sincdb.domain.usuario.model;


import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ForeignKey;

@Entity
@Table(name = "usuario_online")
public class UsuarioOnline {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_usuarioonline")
	@SequenceGenerator(name = "seq_usuarioonline", sequenceName = "seq_usuarioonline", allocationSize = 1)
	private Long id_usuarioonline;

	@NotBlank(message = "O login é obrigatorio!")
	private String login;

	private LocalDateTime dt_ultimologin;

	private Boolean fl_ativo = true;


	    // construtor padrão
    public UsuarioOnline() {}

    public UsuarioOnline(String login) {
        this.login = login;
        this.fl_ativo = true;
        this.dt_ultimologin = LocalDateTime.now();
    }

	public Long getId_usuarioonline() {
		return id_usuarioonline;
	}

	public void setId_usuarioonline(Long id_usuarioonline) {
		this.id_usuarioonline = id_usuarioonline;
	}

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public LocalDateTime getDt_ultimologin() {
		return dt_ultimologin;
	}

	public void setDt_ultimologin(LocalDateTime dt_ultimologin) {
		this.dt_ultimologin = dt_ultimologin;
	}

	public Boolean getFl_ativo() {
		return fl_ativo;
	}

	public void setFl_ativo(Boolean fl_ativo) {
		this.fl_ativo = fl_ativo;
	}
}
