package com.api_sincdb.domain.conexao.model;


import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

@Document(collection = "conexao")
public class Conexao {

    @Id
    private String id;

    private String db_cloud_host;
    private String db_cloud_port;
    private String db_cloud_user;
    private String db_cloud_password;

    private Boolean db_cloud_ssh_enabled = false;
    private String db_cloud_ssh_host;
    private String db_cloud_ssh_port;
    private String db_cloud_ssh_user;
    private String db_cloud_ssh_password;

    private String db_local_host;
    private String db_local_port;
    private String db_local_user;
    private String db_local_password;

    private Boolean db_local_ssh_enabled = false;
    private String db_local_ssh_host;
    private String db_local_ssh_port;
    private String db_local_ssh_user;
    private String db_local_ssh_password;

    private Boolean fl_admin;

    private String idUsuario;
    private String id_empresa;
    private String id_tenant;
    private String nm_conexao;
    private Boolean fl_padrao = false;
    private Boolean fl_ativo = true;

    // Getters e Setters

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getDb_cloud_host() {
        return db_cloud_host;
    }
    public void setDb_cloud_host(String db_cloud_host) {
        this.db_cloud_host = db_cloud_host;
    }

    public String getDb_cloud_port() {
        return db_cloud_port;
    }
    public void setDb_cloud_port(String db_cloud_port) {
        this.db_cloud_port = db_cloud_port;
    }

    public String getDb_cloud_user() {
        return db_cloud_user;
    }
    public void setDb_cloud_user(String db_cloud_user) {
        this.db_cloud_user = db_cloud_user;
    }

    public String getDb_cloud_password() {
        return db_cloud_password;
    }
    public void setDb_cloud_password(String db_cloud_password) {
        this.db_cloud_password = db_cloud_password;
    }

    public Boolean getDb_cloud_ssh_enabled() {
        return db_cloud_ssh_enabled;
    }

    public void setDb_cloud_ssh_enabled(Boolean db_cloud_ssh_enabled) {
        this.db_cloud_ssh_enabled = db_cloud_ssh_enabled;
    }

    public String getDb_cloud_ssh_host() {
        return db_cloud_ssh_host;
    }

    public void setDb_cloud_ssh_host(String db_cloud_ssh_host) {
        this.db_cloud_ssh_host = db_cloud_ssh_host;
    }

    public String getDb_cloud_ssh_port() {
        return db_cloud_ssh_port;
    }

    public void setDb_cloud_ssh_port(String db_cloud_ssh_port) {
        this.db_cloud_ssh_port = db_cloud_ssh_port;
    }

    public String getDb_cloud_ssh_user() {
        return db_cloud_ssh_user;
    }

    public void setDb_cloud_ssh_user(String db_cloud_ssh_user) {
        this.db_cloud_ssh_user = db_cloud_ssh_user;
    }

    public String getDb_cloud_ssh_password() {
        return db_cloud_ssh_password;
    }

    public void setDb_cloud_ssh_password(String db_cloud_ssh_password) {
        this.db_cloud_ssh_password = db_cloud_ssh_password;
    }

    public String getDb_local_host() {
        return db_local_host;
    }
    public void setDb_local_host(String db_local_host) {
        this.db_local_host = db_local_host;
    }

    public String getDb_local_port() {
        return db_local_port;
    }
    public void setDb_local_port(String db_local_port) {
        this.db_local_port = db_local_port;
    }

    public String getDb_local_user() {
        return db_local_user;
    }
    public void setDb_local_user(String db_local_user) {
        this.db_local_user = db_local_user;
    }

    public String getDb_local_password() {
        return db_local_password;
    }
    public void setDb_local_password(String db_local_password) {
        this.db_local_password = db_local_password;
    }

    public Boolean getDb_local_ssh_enabled() {
        return db_local_ssh_enabled;
    }

    public void setDb_local_ssh_enabled(Boolean db_local_ssh_enabled) {
        this.db_local_ssh_enabled = db_local_ssh_enabled;
    }

    public String getDb_local_ssh_host() {
        return db_local_ssh_host;
    }

    public void setDb_local_ssh_host(String db_local_ssh_host) {
        this.db_local_ssh_host = db_local_ssh_host;
    }

    public String getDb_local_ssh_port() {
        return db_local_ssh_port;
    }

    public void setDb_local_ssh_port(String db_local_ssh_port) {
        this.db_local_ssh_port = db_local_ssh_port;
    }

    public String getDb_local_ssh_user() {
        return db_local_ssh_user;
    }

    public void setDb_local_ssh_user(String db_local_ssh_user) {
        this.db_local_ssh_user = db_local_ssh_user;
    }

    public String getDb_local_ssh_password() {
        return db_local_ssh_password;
    }

    public void setDb_local_ssh_password(String db_local_ssh_password) {
        this.db_local_ssh_password = db_local_ssh_password;
    }

    public Boolean getFl_admin() {
        return fl_admin;
    }
    public void setFl_admin(Boolean fl_admin) {
        this.fl_admin = fl_admin;
    }

    public String getIdUsuario() {
        return idUsuario;
    }
    public void setIdUsuario(String idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getId_empresa() {
        return id_empresa;
    }
    public void setId_empresa(String id_empresa) {
        this.id_empresa = id_empresa;
    }

    public String getId_tenant() {
        return id_tenant;
    }
    public void setId_tenant(String id_tenant) {
        this.id_tenant = id_tenant;
    }

    public String getNm_conexao() {
        return nm_conexao;
    }
    public void setNm_conexao(String nm_conexao) {
        this.nm_conexao = nm_conexao;
    }

    public Boolean getFl_padrao() {
        return fl_padrao;
    }
    public void setFl_padrao(Boolean fl_padrao) {
        this.fl_padrao = fl_padrao;
    }

    public Boolean getFl_ativo() {
        return fl_ativo;
    }
    public void setFl_ativo(Boolean fl_ativo) {
        this.fl_ativo = fl_ativo;
    }
}
