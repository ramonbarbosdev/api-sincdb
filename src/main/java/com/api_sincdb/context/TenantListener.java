package com.api_sincdb.context;


import com.api_sincdb.domain.sistema.model.MultiTenantEntity;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class TenantListener {

    @PrePersist
    @PreUpdate
    public void setTenant(MultiTenantEntity entity) {
        entity.setId_tenant(TenantContext.getTenantId());
    }
}
