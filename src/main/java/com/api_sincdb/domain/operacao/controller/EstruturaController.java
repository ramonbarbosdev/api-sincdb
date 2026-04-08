package com.api_sincdb.domain.operacao.controller;

import java.time.LocalDateTime;
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

import com.api_sincdb.domain.operacao.dto.EstruturaResponse;
import com.api_sincdb.domain.operacao.dto.ResumoDTO;
import com.api_sincdb.domain.operacao.service.EstruturaService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;
import com.api_sincdb.util.MontarEstruturaResponseUtils;
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
			Map<String, Object> resultado;
			try {
				resultado = estruturaService.verificarEstrutura(token, base, esquema, null);
				resultadoRef.set(resultado);

			} catch (Exception e) {
				e.printStackTrace();
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

		Map<String, Object> resultado = resultadoRef.get();
		EstruturaResponse response = MontarEstruturaResponseUtils.montarEstruturaResponse(resultadoRef.get(), base,
				esquema);

		if (Boolean.TRUE.equals(resultado.get("sucesso")))
			return new ResponseEntity<>(response, HttpStatus.OK);

		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);

	}

	@GetMapping(value = "/verificar/{base}/{esquema}/{tabela}", produces = "application/json")
	public ResponseEntity<?> verificarEstruturaTabela(@PathVariable(value = "base") String base,
			@PathVariable(value = "esquema") String esquema, @PathVariable(value = "tabela") String tabela,
			HttpServletRequest request) throws InterruptedException {

		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
		AtomicReference<Exception> erroRef = new AtomicReference<>();

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() -> {
			Map<String, Object> resultado;
			try {
				resultado = estruturaService.verificarEstrutura(token, base, esquema, tabela);
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
		EstruturaResponse response = MontarEstruturaResponseUtils.montarEstruturaResponse(resultadoRef.get(), base,
				esquema);

		if (Boolean.TRUE.equals(resultado.get("sucesso")))
			return new ResponseEntity<>(response, HttpStatus.OK);

		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);

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

		Map<String, Object> resultado = estruturaService.sincronizarEstrutura(token, base, esquema);

		if ((Boolean) resultado.get("sucesso")) {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.OK);
		} else {
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.NOT_FOUND);
		}

	}

}
