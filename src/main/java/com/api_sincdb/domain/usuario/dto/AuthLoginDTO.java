package com.api_sincdb.domain.usuario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Autenticar um  usuário")
public class AuthLoginDTO {
    
   @Schema(description = "Login do usuário", example = "85778905548", required = true)
    private String login;

    @Schema(description = "Senha do usuário", example = "ramon", required = true)
    private String senha;

    @Schema(description = "Inquilino", example = "Clinica", required = true)
    private String id_tenant;

    private Boolean isAreaDev;


       public String getId_tenant() {
        return id_tenant;
    }
    public void setId_tenant(String id_tenant) {
        this.id_tenant = id_tenant;
    }

    // Getters e Setters
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }


    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }


   public Boolean getIsAreaDev() {
       return isAreaDev;
   }

   public void setIsAreaDev(Boolean isAreaDev) {
       this.isAreaDev = isAreaDev;
   }



}