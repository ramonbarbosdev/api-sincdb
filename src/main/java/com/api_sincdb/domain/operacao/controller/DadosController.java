package com.api_sincdb.domain.operacao.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.operacao.dto.EstruturaResponse;
import com.api_sincdb.domain.operacao.dto.ResumoDTO;
import com.api_sincdb.domain.operacao.service.DadosService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;
import com.api_sincdb.util.ProcessoManager;
import com.api_sincdb.websocket.LogPublisher;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(value = "/dados")
public class DadosController {

	@Autowired
	private DadosService dadosService;

	@Autowired
	private ProcessoManager processoManager;

	@Autowired
	private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

	@Autowired
	private LogPublisher logPublisher;

	@GetMapping(value = "/verificar/{base}/{esquema}", produces = "application/json")
	public ResponseEntity<?> verificarDados(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema, HttpServletRequest request) throws InterruptedException {

		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
		AtomicReference<Exception> erroRef = new AtomicReference<>();

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() -> {
			Map<String, Object> resultado;
			try {
				resultado = dadosService.verificarDados(token, base, esquema, null);
				resultadoRef.set(resultado);

			} catch (Exception e) {
				erroRef.set(e);
			}
		});

		while (processoManager.isExecutando()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		if (erroRef.get() != null) {
			Exception e = erroRef.get();
			EstruturaResponse erroResponse = new EstruturaResponse();
			erroResponse.setSucesso(false);
			erroResponse.setBase(base);
			erroResponse.setEsquema(esquema);
			erroResponse.setGeradoEm(LocalDateTime.now());
			ResumoDTO resumo = new ResumoDTO();
			resumo.setMensagem(e.getMessage());
			resumo.setPodeExecutar(false);
			resumo.setPossuiOperacoesPerigosas(false);
			erroResponse.setResumo(resumo);
			return new ResponseEntity<>(erroResponse, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		Map<String, Object> resultado = resultadoRef.get();

		if (Boolean.TRUE.equals(resultado.get("sucesso")))
			return new ResponseEntity<>(resultado, HttpStatus.OK);

		return new ResponseEntity<>(resultado, HttpStatus.NOT_FOUND);

	}

	@GetMapping(value = "/verificar/{base}/{esquema}/{tabela}", produces = "application/json")
	public ResponseEntity<?> verificarDadosTabela(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema, @PathVariable(value = "tabela") String tabela,
			HttpServletRequest request) throws InterruptedException {

		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
		AtomicReference<Exception> erroRef = new AtomicReference<>();

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() -> {
			Map<String, Object> resultado;
			try {
				resultado = dadosService.verificarDados(token, base, esquema, tabela);
				resultadoRef.set(resultado);

			} catch (Exception e) {
				erroRef.set(e);
			}
		});

		while (processoManager.isExecutando()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		if (erroRef.get() != null) {
			Exception e = erroRef.get();
			EstruturaResponse erroResponse = new EstruturaResponse();
			erroResponse.setSucesso(false);
			erroResponse.setBase(base);
			erroResponse.setEsquema(esquema);
			erroResponse.setGeradoEm(LocalDateTime.now());
			ResumoDTO resumo = new ResumoDTO();
			resumo.setMensagem(e.getMessage());
			resumo.setPodeExecutar(false);
			resumo.setPossuiOperacoesPerigosas(false);
			erroResponse.setResumo(resumo);
			return new ResponseEntity<>(erroResponse, HttpStatus.INTERNAL_SERVER_ERROR);
		}

		Map<String, Object> resultado = resultadoRef.get();

		if (Boolean.TRUE.equals(resultado.get("sucesso")))
			return new ResponseEntity<>(resultado, HttpStatus.OK);

		return new ResponseEntity<>(resultado, HttpStatus.NOT_FOUND);
	}

	@GetMapping(value = "/cancelar", produces = "application/json")
	public ResponseEntity<Void> cancelar() {
		logPublisher.enviarLog("Processo Cancelado.");
		processoManager.cancelarProcesso();
		return ResponseEntity.ok().build();
	}

	@GetMapping(value = "/{base}/{esquema}", produces = "application/json")
	public ResponseEntity<?> sincronizacao(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema, HttpServletRequest request) {

		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		Map<String, Object> resultado = dadosService.sincronizarDados(token, base, esquema, null, false);

		if ((Boolean) resultado.get("sucesso")) {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.OK);

		} else {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping(value = "/{base}/{esquema}/{tabela}", produces = "application/json")
	public ResponseEntity<?> sincronizacaoTabela(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema,
			@PathVariable(value = "tabela") String tabela,
			HttpServletRequest request) {

		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		Map<String, Object> resultado = dadosService.sincronizarDados(token, base, esquema, tabela, false);

		if ((Boolean) resultado.get("sucesso")) {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.OK);

		} else {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.NOT_FOUND);
		}
	}

}