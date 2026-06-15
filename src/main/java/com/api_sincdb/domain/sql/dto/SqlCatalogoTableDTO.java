package com.api_sincdb.domain.sql.dto;

import java.util.ArrayList;
import java.util.List;

public class SqlCatalogoTableDTO {

    private String name;
    private List<SqlCatalogoColumnDTO> columns = new ArrayList<>();

    public SqlCatalogoTableDTO() {
    }

    public SqlCatalogoTableDTO(String name, List<SqlCatalogoColumnDTO> columns) {
        this.name = name;
        this.columns = columns;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SqlCatalogoColumnDTO> getColumns() {
        return columns;
    }

    public void setColumns(List<SqlCatalogoColumnDTO> columns) {
        this.columns = columns;
    }
}
