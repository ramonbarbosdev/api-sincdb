package com.api_sincdb.domain.usuario.service;


import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api_sincdb.domain.role.model.Role;
import com.api_sincdb.domain.role.repository.RoleRepository;
import com.api_sincdb.util.MestreDetalheUtils;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RoleService {

    @Autowired
    private RoleRepository repository;



    @Transactional(rollbackFor = Exception.class)
    public Role salvar(Role objeto) throws Exception {

  

        validarObjeto(objeto);
        objeto = repository.save(objeto);


        objeto = repository.save(objeto);

        return objeto;
    }

   



    public void validarObjeto(Role objeto) throws Exception {

        String nomeRole =  converterParaRole(objeto.getNomeRole());

        // 1. Validar se começa com ROLE_
        if (nomeRole == null || !nomeRole.startsWith("ROLE_")) {
            throw new Exception("O nome do papel deve começar com 'ROLE_'. Exemplo: ROLE_GESTAO");
        }

        objeto.setNomeRole(nomeRole);

        Role nomeExistente = repository.findByNomeRole(objeto.getNomeRole());
        if (nomeExistente != null && objeto.getId() == null && objeto.getId() != nomeExistente.getId())
            throw new Exception("Esse papel já existe!");

    }

    public String converterParaRole(String nomeAmigavel) {
    if (nomeAmigavel == null || nomeAmigavel.isBlank()) {
        throw new IllegalArgumentException("O nome do papel não pode ser vazio");
    }

    // 1. Remove acentos
    String normalizado = Normalizer.normalize(nomeAmigavel, Normalizer.Form.NFD)
                          .replaceAll("\\p{M}", "");

    // 2. Remove caracteres inválidos (mantém letras, números e espaço)
    normalizado = normalizado.replaceAll("[^a-zA-Z0-9 ]", "");

    // 3. Substitui espaços por underline e transforma em maiúsculas
    normalizado = normalizado.trim().replaceAll("\\s+", "_").toUpperCase();

    // 4. Adiciona prefixo ROLE_
    return "ROLE_" + normalizado;
}

    @Transactional(rollbackFor = Exception.class)
    public void excluir(String id) {

       
        repository.deleteById(id);
    }

}
