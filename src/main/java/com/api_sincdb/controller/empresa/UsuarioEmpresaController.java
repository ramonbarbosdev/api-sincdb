package com.api_sincdb.controller.empresa;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.controller.base.BaseControllerMongo;
import com.api_sincdb.domain.empresa.model.Empresa;
import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;
import com.api_sincdb.domain.empresa.service.EmpresaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/usuarioempresa")
@Tag(name = "Usuario de Empresa")
public class UsuarioEmpresaController extends BaseControllerMongo<UsuarioEmpresa, String> {


  

}
