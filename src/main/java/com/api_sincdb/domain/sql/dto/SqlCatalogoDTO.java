package com.api_sincdb.domain.sql.dto;

import java.util.ArrayList;
import java.util.List;

public class SqlCatalogoDTO {

    private List<SqlCatalogoSchemaDTO> schemas = new ArrayList<>();

    public SqlCatalogoDTO() {
    }

    public SqlCatalogoDTO(List<SqlCatalogoSchemaDTO> schemas) {
        this.schemas = schemas;
    }

    public List<SqlCatalogoSchemaDTO> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<SqlCatalogoSchemaDTO> schemas) {
        this.schemas = schemas;
    }
}
