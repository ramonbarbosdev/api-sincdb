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

    private String db_local_host;
    private String db_local_port;
    private String db_local_user;
    private String db_local_password;

    private Boolean fl_admin;

    private String idUsuario;

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
}
