package com.api_sincdb.domain.operacao.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstruturaResponse {
    public boolean sucesso;
    public String base;
    public String esquema;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    public LocalDateTime geradoEm;
    public ResumoDTO resumo;
    public List<CategoriaDDLDTO> categorias;

}
