package com.api_sincdb.domain.sql.dto;

public class SqlColumnDTO {

    private String name;
    private String type;

    public SqlColumnDTO() {
    }

    public SqlColumnDTO(String name, String type) {
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
