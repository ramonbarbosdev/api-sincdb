package com.api_sincdb.domain.sistema.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
// @EntityListeners(TenantListener.class)
public abstract class MultiTenantEntity {

    @Column(name = "id_tenant")
    private String id_tenant;

    public String getId_tenant() {
        return id_tenant;
    }

    public void setId_tenant(String id_tenant) {
        this.id_tenant = id_tenant;
    }
}
