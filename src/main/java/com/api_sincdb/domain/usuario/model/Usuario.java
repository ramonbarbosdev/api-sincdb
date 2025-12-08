package com.api_sincdb.domain.usuario.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.role.model.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Document(collection = "usuarios")
public class Usuario implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    private String login;
    private String senha;
    private String nome;
    private String token = "";

    @DBRef(lazy = true) // lazy evita carregar tudo imediatamente, como FetchType.LAZY
    private List<Role> roles = new ArrayList<>();

    @DBRef(lazy = true)
    private List<UsuarioEmpresa> itensUsuarioEmpresa = new ArrayList<>();

    public List<UsuarioEmpresa> getItensUsuarioEmpresa() {
        return itensUsuarioEmpresa;
    }

    public void setItensUsuarioEmpresa(List<UsuarioEmpresa> itensUsuarioEmpresa) {
        this.itensUsuarioEmpresa = itensUsuarioEmpresa;
    }

    // Getters e Setters

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    // Equals e HashCode

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Usuario other = (Usuario) obj;
        return Objects.equals(id, other.id);
    }

    // Implementação de UserDetails (Spring Security)

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return this.senha;
    }

    @JsonIgnore
    @Override
    public String getUsername() {
        return this.login;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isEnabled() {
        return true;
    }
}
