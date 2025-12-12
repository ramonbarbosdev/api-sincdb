package com.api_sincdb.domain.info.model;

import java.time.LocalDateTime;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api_sincdb.enums.StatusSincronizacao;
import com.api_sincdb.enums.TipoOperacao;
import com.fasterxml.jackson.annotation.JsonFormat;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sincronizacao_schema")
public class SincronizacaoSchema {

	@Id
	private String id;

	private String usuario;

	private String baseNome;
	
	private String schemaNome;

	private TipoOperacao operacao;
	
	private StatusSincronizacao status;

	private String detalhes;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
	private LocalDateTime ultimaExecucao;

}
