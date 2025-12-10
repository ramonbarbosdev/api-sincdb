package com.api_sincdb.domain.operacao.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.operacao.service.EstruturaService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;
import com.api_sincdb.util.ProcessoManager;
import com.api_sincdb.websocket.LogPublisher;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping(value = "/estrutura")
public class EstruturaController {

	@Autowired
	private EstruturaService estruturaService;

	@Autowired
	private ProcessoManager processoManager;

	@Autowired
	private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

	@Autowired
	private LogPublisher logPublisher;

	@GetMapping(value = "/verificar/{base}/{esquema}", produces = "application/json")
	public ResponseEntity<?> verificarEstrutura(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema, HttpServletRequest request) throws InterruptedException {
		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() -> {
			Map<String, Object> resultado = estruturaService.verificarEstrutura(token, base, esquema, null);
			resultadoRef.set(resultado);
		});

		while (processoManager.isExecutando()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		Map<String, Object> resultado = resultadoRef.get();

		if (Boolean.TRUE.equals(resultado.get("sucesso")))
			return new ResponseEntity<>(resultado, HttpStatus.OK);

		return new ResponseEntity<>(resultado, HttpStatus.NOT_FOUND);

	}

	@GetMapping(value = "/verificar/{base}/{esquema}/{tabela}", produces = "application/json")
	public ResponseEntity<?> verificarEstruturaTabela(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema, @PathVariable(value = "tabela") String tabela,
			HttpServletRequest request) throws InterruptedException {

		// String token = request.getHeader("Authorization");
		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() -> {
			Map<String, Object> resultado = estruturaService.verificarEstrutura(token, base, esquema, tabela);
			resultadoRef.set(resultado);
		});

		while (processoManager.isExecutando()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
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

	@GetMapping(value = "/{base}", produces = "application/json")
	public ResponseEntity<?> sincronizacao(@PathVariable(value = "base") String base, HttpServletRequest request) {

		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		Map<String, Object> resultado = estruturaService.sincronizarEstrutura(token, base);

		if ((Boolean) resultado.get("sucesso")) {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.OK);
		} else {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.NOT_FOUND);
		}

	}

}
