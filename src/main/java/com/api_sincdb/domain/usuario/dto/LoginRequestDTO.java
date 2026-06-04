package com.api_sincdb.domain.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequestDTO(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 numeros") String nuCpf,
        @NotBlank String dsSenha) {
}
