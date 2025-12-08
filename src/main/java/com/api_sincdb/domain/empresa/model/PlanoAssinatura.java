package com.api_sincdb.domain.empresa.model;


import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "plano_assinatura")
public class PlanoAssinatura {

    @Id
    private String id; 

    @NotBlank(message = "O nome é obrigatorio!")
    private String nm_planoassinatura;

    private Double vl_mensal;

    private int nu_limitemensagens;

    private int nu_limiteatendentes;

    private LocalDateTime dt_cadastro = LocalDateTime.now();

 
}
