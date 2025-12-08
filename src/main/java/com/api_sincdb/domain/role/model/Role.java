package com.api_sincdb.domain.role.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;

@Document(collection = "role")
public class Role implements GrantedAuthority {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    private String nomeRole;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getNomeRole() {
        return nomeRole;
    }
    public void setNomeRole(String nomeRole) {
        this.nomeRole = nomeRole;
    }

    @Override
    public String getAuthority() {
        return nomeRole;
    }
    
}
