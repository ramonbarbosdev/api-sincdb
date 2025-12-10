package com.api_sincdb.domain.usuario.dto;


public class PerfilDTO {

    private String id;
    private String login;
    private String nome;
    private String senha;
    private String img;
    private String role;
    private String cargo;

    public PerfilDTO() {
    
    }

    public PerfilDTO(String id,String login, String nome, String img, String role, String cargo,String senha) {
        this.id = id;
        this.login = login;
        this.nome = nome;
        this.senha = senha;
        this.img = img;
        this.role = role;
        this.cargo = cargo;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
