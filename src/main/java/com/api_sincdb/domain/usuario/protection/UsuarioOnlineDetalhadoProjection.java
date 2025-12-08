package com.api_sincdb.domain.usuario.protection;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public interface UsuarioOnlineDetalhadoProjection {

    String getLogin();

    String getNome();

    Boolean getFl_ativo();

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    LocalDateTime getDt_ultimologin();

}
