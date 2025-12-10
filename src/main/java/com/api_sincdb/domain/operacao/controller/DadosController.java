package com.api_sincdb.domain.operacao.controller;

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

import com.api_sincdb.domain.operacao.service.DadosService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;
import com.api_sincdb.util.ProcessoManager;

import jakarta.servlet.http.HttpServletRequest;

@RestController 
@RequestMapping(value = "/dados")
public class DadosController
{

	@Autowired
	private DadosService dadosService;

	@Autowired
	private ProcessoManager processoManager;

	    @Autowired
    private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;


	@GetMapping(value = "/verificar/{base}/{esquema}", produces = "application/json")
	public ResponseEntity<?> verificarDados (@PathVariable (value = "base") String base, @PathVariable (value = "esquema") String esquema,  HttpServletRequest request ) throws InterruptedException 
	{

		// String token = request.getHeader("Authorization");
		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() ->
		{
			Map<String, Object>  resultado = dadosService.verificarDados(token, base,  null);
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

		if (Boolean.TRUE.equals(resultado.get("sucesso"))) return new ResponseEntity<>(resultado, HttpStatus.OK);
				
		return new ResponseEntity<>(resultado, HttpStatus.NOT_FOUND);
		
	}
	 
	@GetMapping(value = "/verificar/{base}/{esquema}/{tabela}", produces = "application/json")
	public ResponseEntity<?> verificarDadosTabela (@PathVariable (value = "base") String base, @PathVariable (value = "esquema") String esquema,  @PathVariable (value = "tabela") String tabela,  HttpServletRequest request ) throws InterruptedException  
	{

		// String token = request.getHeader("Authorization");
		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		AtomicReference<Map<String, Object>> resultadoRef = new AtomicReference<>(new LinkedHashMap<>());

		processoManager.iniciarProcesso(() ->
		{
			Map<String, Object>  resultado = dadosService.verificarDados(token, base,  tabela);
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

		if (Boolean.TRUE.equals(resultado.get("sucesso"))) return new ResponseEntity<>(resultado, HttpStatus.OK);
				
		return new ResponseEntity<>(resultado, HttpStatus.NOT_FOUND);
	}

	@GetMapping(value = "/cancelar", produces = "application/json")
    public ResponseEntity<Void> cancelar()
	{
        processoManager.cancelarProcesso();
        return ResponseEntity.ok().build();
    }
	
 
    @GetMapping(value = "/{base}", produces = "application/json")
	public ResponseEntity<?> sincronizacao ( @PathVariable (value = "base") String base,  HttpServletRequest request ) 
	{

		// String token = request.getHeader("Authorization");
		String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

		
		Map<String, Object> resultado = dadosService.sincronizarDados(token,base,  null, false);

		if ((Boolean) resultado.get("sucesso"))
		{
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.OK);
											
		}
		else
		{
			return new ResponseEntity<Map<String, Object>>(resultado, HttpStatus.NOT_FOUND);
		}
	}
	

}