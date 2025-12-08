package com.api_sincdb.domain.usuario.model;


import java.time.LocalDateTime;

import org.springframework.security.core.GrantedAuthority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "anexo")
public class Anexo {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_anexo")
	@SequenceGenerator(name = "seq_anexo", sequenceName = "seq_anexo", allocationSize = 1)
	private Long id_anexo;

	@NotNull(message = "O identificador modulo é obrigatorio!")
	private Long id_model;

	private Long id_detalhe;

	@NotBlank(message = "O nome modulo é obrigatorio!")
	private String nm_model;

	@NotBlank(message = "O nome anexo é obrigatorio!")
	private String nm_anexo;

	@NotBlank(message = "A url  é obrigatorio!")
	private String url;

	@NotBlank(message = "O type é obrigatorio!")
	private String content_type;

	@NotNull(message = "O tamanho é obrigatorio!")
	private Long nu_tamanho;

	@Column(name = "dt_cadastro", nullable = false, updatable = false)
	private LocalDateTime dt_cadastro;

	@PrePersist
	protected void onCreate() {
		this.dt_cadastro = LocalDateTime.now();
	}

	public Long getId_anexo() {
		return id_anexo;
	}

	public void setId_anexo(Long id_anexo) {
		this.id_anexo = id_anexo;
	}

	public Long getId_model() {
		return id_model;
	}

	public void setId_model(Long id_model) {
		this.id_model = id_model;
	}

	public Long getId_detalhe() {
		return id_detalhe;
	}
	public void setId_detalhe(Long id_detalhe) {
		this.id_detalhe = id_detalhe;
	}

	public String getNm_model() {
		return nm_model;
	}

	public void setNm_model(String nm_model) {
		this.nm_model = nm_model;
	}

	public String getNm_anexo() {
		return nm_anexo;
	}

	public void setNm_anexo(String nm_anexo) {
		this.nm_anexo = nm_anexo;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getContent_type() {
		return content_type;
	}

	public void setContent_type(String content_type) {
		this.content_type = content_type;
	}

	public Long getNu_tamanho() {
		return nu_tamanho;
	}

	public void setNu_tamanho(Long nu_tamanho) {
		this.nu_tamanho = nu_tamanho;
	}

	public LocalDateTime getDt_cadatros() {
		return dt_cadastro;
	}

	public void setDt_cadatros(LocalDateTime dt_cadastro) {
		this.dt_cadastro = dt_cadastro;
	}

}
