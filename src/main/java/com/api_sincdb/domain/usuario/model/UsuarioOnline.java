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

import org.springframework.data.mongodb.core.mapping.Document;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ForeignKey;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuario_online")
public class UsuarioOnline {

	@Id
	private String id;

	@NotBlank(message = "O login é obrigatorio!")
	private String login;

	private LocalDateTime dt_ultimologin;

	private Boolean fl_ativo = true;

    public UsuarioOnline(String login) {
        this.login = login;
        this.fl_ativo = true;
        this.dt_ultimologin = LocalDateTime.now();
    }

	


}
