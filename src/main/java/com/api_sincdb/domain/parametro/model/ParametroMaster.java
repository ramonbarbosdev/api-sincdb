package com.api_sincdb.domain.parametro.model;

import java.beans.Transient;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api_sincdb.enums.TipoParametro;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "parametro_master")
public class ParametroMaster {
    @Id
    private String id;

    private String codigo;

    private String nomeChave;

    private String valor;

    @Enumerated(EnumType.STRING)
    private TipoParametro tipo;

    private String observacao;

    private LocalDateTime dt_cadastro = LocalDateTime.now();

    @Transient
	public Object getValue() {
		if (valor == null || valor.isEmpty())
			return null;

		try {
			switch (tipo) {
				case bool:
					return Boolean.parseBoolean(valor);
				case number:
					return Double.parseDouble(valor);
				case date:
					return LocalDate.parse(valor);
				case string:
				default:
					return valor;
			}
		} catch (Exception e) {
			return null;
		}
	}

	@Transient
	public void setValue(Object value) {
		if (value == null) {
			this.valor = null;
			return;
		}

		switch (tipo) {
			case bool:
				this.valor = Boolean.parseBoolean(value.toString()) ? "true" : "false";
				break;
			case number:
				try {
					this.valor = Double.valueOf(value.toString()).toString();
				} catch (NumberFormatException e) {
					this.valor = "0";
				}
				break;
			case date:
				if (value instanceof LocalDate) {
					this.valor = value.toString();
				} else {
					this.valor = LocalDate.parse(value.toString()).toString();
				}
				break;
			case string:
			default:
				this.valor = value.toString();
				break;
		}
	}
}
