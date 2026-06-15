package com.api_sincdb.domain.sql.dto;

import java.util.ArrayList;
import java.util.List;

public class SqlCatalogoSchemaDTO {

    private String name;
    private List<SqlCatalogoTableDTO> tables = new ArrayList<>();

    public SqlCatalogoSchemaDTO() {
    }

    public SqlCatalogoSchemaDTO(String name, List<SqlCatalogoTableDTO> tables) {
        this.name = name;
        this.tables = tables;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SqlCatalogoTableDTO> getTables() {
        return tables;
    }

    public void setTables(List<SqlCatalogoTableDTO> tables) {
        this.tables = tables;
    }
}
