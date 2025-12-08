package com.api_sincdb.domain.usuario.dto;


import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registrar um novo usuário")
public class AuthRegisterDTO {

   @Schema(description = "Login do usuário", example = "admin123", required = true)
    private String login;

    @Schema(description = "Nome completo do usuário", example = "João Silva", required = true)
    private String nome;

    @Schema(description = "Senha do usuário", example = "senhaSegura123", required = true)
    private String senha;

    // Getters e Setters
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
}