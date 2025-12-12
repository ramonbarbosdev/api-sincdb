package com.api_sincdb.domain.usuario.model;


import java.time.LocalDateTime;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuario_online")
public class UsuarioOnline {

	@Id
	private String id;

	@NotBlank(message = "O login é obrigatorio!")
	private String login;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
	private LocalDateTime dt_ultimologin;

	private Boolean fl_ativo = true;

	public UsuarioOnline(String login) {
		this.login = login;
		this.fl_ativo = true;
		this.dt_ultimologin = LocalDateTime.now();
	}

}
