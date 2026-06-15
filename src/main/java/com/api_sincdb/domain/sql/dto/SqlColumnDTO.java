package com.api_sincdb.domain.sql.dto;

public class SqlColumnDTO {

    private String name;
    private String type;
    private Integer width;

    public SqlColumnDTO() {
    }

    public SqlColumnDTO(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public SqlColumnDTO(String name, String type, Integer width) {
        this.name = name;
        this.type = type;
        this.width = width;
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

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }
}
