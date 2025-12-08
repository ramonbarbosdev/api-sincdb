package com.api_sincdb.domain.empresa.model;

import java.beans.Transient;
import java.time.LocalDateTime;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Document(collection = "empresa")
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Empresa {

    @Id
    private String id;

    @NotBlank(message = "Tenant obrigatório!")
    private String id_tenant;

    @Field("cd_empresa")
    @NotBlank(message = "Código da empresa obrigatório!")
    private String cd_empresa;

    @Field("nm_empresa")
    @NotBlank(message = "Nome é obrigatório!")
    private String nm_empresa;

    @DBRef(lazy = true)
    private PlanoAssinatura planoAssinatura;

    private String id_planoassinatura;

    private String ds_email;

    private String nu_telefone;

    private String accessToken;

    private String webhookUrl;

    private boolean fl_ativo = true;

    private LocalDateTime dt_cadastro = LocalDateTime.now();

    @JsonProperty("nm_planoassinatura")
    public String getNm_planoassinatura() {
        return planoAssinatura != null ? planoAssinatura.getNm_planoassinatura() : null;
    }
    
    

}
