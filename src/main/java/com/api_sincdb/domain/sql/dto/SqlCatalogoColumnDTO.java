package com.api_sincdb.domain.sql.dto;

public class SqlCatalogoColumnDTO {

    private String name;
    private String type;

    public SqlCatalogoColumnDTO() {
    }

    public SqlCatalogoColumnDTO(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
