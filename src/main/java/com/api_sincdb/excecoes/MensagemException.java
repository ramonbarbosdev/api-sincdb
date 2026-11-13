package com.api_sincdb.excecoes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.TransactionSystemException;

import com.api_sincdb.enums.TipoConexao;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

public class MensagemException extends RuntimeException{

	private final HttpStatus httpStatus;

	public MensagemException(String message)
	{
	        super(message);
		this.httpStatus = HttpStatus.BAD_REQUEST;
	}

	public MensagemException(String message, HttpStatus httpStatus)
	{
		super(message);
		this.httpStatus = httpStatus == null ? HttpStatus.BAD_REQUEST : httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public static MensagemException erroConexaoBanco(Throwable causa, String nomeBase, TipoConexao tipo) {
		String ambiente = tipo == TipoConexao.LOCAL ? "local" : "nuvem";
		String mensagem = traduzirErroConexaoPostgreSql(causa, nomeBase, ambiente);
		HttpStatus status = statusHttpErroConexao(causa);
		return new MensagemException(mensagem, status);
	}

	public static String traduzirErroConexaoPostgreSql(Throwable ex, String nomeBase, String ambiente) {
		String textoOriginal = coletarMensagensCadeia(ex);
		String textoCompleto = textoOriginal.toLowerCase();
		String baseInformada = nomeBase != null && !nomeBase.isBlank() ? nomeBase : extrairNomeBase(textoOriginal);

		if (textoCompleto.contains("does not exist") && textoCompleto.contains("database")) {
			String base = baseInformada != null ? baseInformada : "informada";
			if ("local".equalsIgnoreCase(ambiente)) {
				return "A base de dados \"" + base + "\" não existe no PostgreSQL local configurado na conexão. "
						+ "Crie essa base no ambiente local ou ajuste os dados da conexão local "
						+ "antes de verificar ou criar esquemas para sincronização.";
			}
			return "A base de dados \"" + base + "\" não existe no PostgreSQL da nuvem configurado na conexão. "
					+ "Confira o nome da base na nuvem ou ajuste os dados da conexão.";
		}

		if (textoCompleto.contains("does not exist") && textoCompleto.contains("role")) {
			String role = extrairNomeRole(textoOriginal);
			String usuario = role != null ? role : "informado";
			return "O usuário \"" + usuario + "\" da conexão " + ambiente
					+ " não existe no PostgreSQL. Ajuste usuário e senha no cadastro da conexão "
					+ "ou crie esse usuário (role) no servidor.";
		}

		if (textoCompleto.contains("password authentication failed")) {
			return "Falha ao autenticar no PostgreSQL " + ambiente + ". "
					+ "Verifique usuário e senha cadastrados na conexão" +
					(baseInformada != null ? " para a base \"" + baseInformada + "\"." : ".");
		}

		if (textoCompleto.contains("connection refused") || textoCompleto.contains("connect timed out")
				|| textoCompleto.contains("connection timed out")) {
			return "Não foi possível alcançar o PostgreSQL " + ambiente +
					(baseInformada != null ? " (base \"" + baseInformada + "\")" : "") + ". "
					+ "Verifique host, porta, rede, VPN ou túnel SSH da conexão.";
		}

		if (textoCompleto.contains("timeout") || textoCompleto.contains("timed out")) {
			return "Tempo esgotado ao conectar no PostgreSQL " + ambiente +
					(baseInformada != null ? " na base \"" + baseInformada + "\"." : ".")
					+ " Verifique se o servidor está disponível e se host/porta estão corretos.";
		}

		if (textoCompleto.contains("permission denied") || textoCompleto.contains("must be owner")) {
			return "O usuário da conexão " + ambiente + " não tem permissão para executar esta operação"
					+ (baseInformada != null ? " na base \"" + baseInformada + "\"." : ".");
		}

		String original = mensagemRaiz(ex);
		if (original != null && !original.isBlank()) {
			return "Erro ao conectar ou consultar o PostgreSQL " + ambiente +
					(baseInformada != null ? " (base \"" + baseInformada + "\")" : "") + ": " + original;
		}
		return "Erro ao conectar ou consultar o PostgreSQL " + ambiente + ".";
	}

	private static HttpStatus statusHttpErroConexao(Throwable ex) {
		String texto = coletarMensagensCadeia(ex).toLowerCase();
		if (texto.contains("does not exist") && (texto.contains("database") || texto.contains("role"))) {
			return HttpStatus.NOT_FOUND;
		}
		if (texto.contains("connection refused") || texto.contains("timed out")) {
			return HttpStatus.SERVICE_UNAVAILABLE;
		}
		return HttpStatus.BAD_REQUEST;
	}

	private static String coletarMensagensCadeia(Throwable ex) {
		StringBuilder sb = new StringBuilder();
		Throwable atual = ex;
		while (atual != null) {
			if (atual.getMessage() != null) {
				sb.append(atual.getMessage()).append(' ');
			}
			Throwable proximo = atual.getCause();
			if (proximo == atual) {
				break;
			}
			atual = proximo;
		}
		return sb.toString();
	}

	private static String mensagemRaiz(Throwable ex) {
		Throwable atual = ex;
		Throwable ultimoComMensagem = ex;
		while (atual != null) {
			if (atual.getMessage() != null && !atual.getMessage().isBlank()) {
				ultimoComMensagem = atual;
			}
			Throwable proximo = atual.getCause();
			if (proximo == atual) {
				break;
			}
			atual = proximo;
		}
		return ultimoComMensagem.getMessage();
	}

	private static String extrairNomeBase(String texto) {
		Pattern pattern = Pattern.compile("database \"([^\"]+)\" does not exist", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(texto);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private static String extrairNomeRole(String texto) {
		Pattern pattern = Pattern.compile("role \"([^\"]+)\" does not exist", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(texto);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	 public static String tratamentoTransacaoSistema(TransactionSystemException ex)
    {
        Throwable rootCause = ex.getRootCause();
        
        if (rootCause instanceof ConstraintViolationException)
        {
            return refinarValidacaoErro((ConstraintViolationException) rootCause);
        }

        return "Erro ao processar a transação: " + (rootCause != null ? rootCause.getMessage() : ex.getMessage());
    }

    public static String refinarValidacaoErro(ConstraintViolationException ex)
    {
        StringBuilder errorMessage = new StringBuilder("Erro de validação: ");

        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        
        for (ConstraintViolation<?> violation : violations)
        {
            errorMessage.append(violation.getMessage()).append("; ");
        }
        
        return errorMessage.toString();
    }

	public static String tratamentoMensagemHTTPIlegivel(HttpMessageNotReadableException ex)
	{
        String message = ex.getMessage();

        if (message.contains("Cannot deserialize value of type"))
		{
	
            return "Erro de deserialização: O valor fornecido não é válido. ";
        }
        
        return  message;
    }

	public static String tratamentoViolacaoIntegridadeDados(Exception ex)
	{
        String message = ex.getMessage();
		
        if (message.contains("null value in column"))
		{
			String nmColuna = extrairNomeColuna(message);
            return "A coluna "+nmColuna+ " não pode ser nulo.";
        }

		if(message.contains("duplicate key value violates"))
		{
			//ex: Key (cd_conta)=(1) already exists
			String regex = "Key \\((.*?)\\)=\\((.*?)\\) already exists";
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(message);

			if (matcher.find())
			{
				String chave = matcher.group(1); 
				String valor = matcher.group(2); 

				return "O valor '"+valor+"' já existe para o campo '"+chave+ "'. Por favor, escolha um valor diferente.";
			}
		
		}

		if(message.contains("still referenced from"))
		{

			//(.?) serve para pegar a informacao exata
			String regex = "table \"(.*?)\".]";
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(message);


			if (matcher.find())
			{
				String tabelaReferencia = matcher.group(1); 
				
				if(message.contains("delete from"))
				{
					return "Não é possivel deletar o registro porque ele está referenciado na tabela '" + tabelaReferencia+ "'.";

				}
				
			}

		}
        
		
        return  message;
    }

	public static String extrairNomeColuna(String message) {
        String[] parts = message.split("\"");

        if (parts.length > 1) {
            return parts[1]; // Retorna o nome da coluna
        }
        return "campo desconhecido"; // Retorna um valor padrão se não encontrar
    }

	
	
}
