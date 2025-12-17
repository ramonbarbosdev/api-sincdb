package com.api_sincdb.controller.base;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

public abstract class BaseController<T, D, ID> {

    protected CrudRepository<T, ID> repository;

    public BaseController(CrudRepository<T, ID> repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/", produces = "application/json")
    public ResponseEntity<List<?>> obterTodos() {
        List<T> entidades = (List<T>) repository.findAll();

        return new ResponseEntity<>(entidades, HttpStatus.OK);
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<?> obterPorId(@PathVariable ID id) {
        Optional<T> objeto = repository.findById(id);

        return new ResponseEntity<>(objeto, HttpStatus.OK);
    }

    @PostMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> cadastrar(@RequestBody T objeto) throws Exception {
        T objetoSalvo = repository.save(objeto);

        return new ResponseEntity<>(Map.of("message", "Registro salvo com sucesso"), HttpStatus.CREATED);
    }

    @PutMapping(value = "/", produces = "application/json")
    public ResponseEntity<?> atualizar(@RequestBody T objeto) throws Exception {
        T objetoSalvo = repository.save(objeto);

        return new ResponseEntity<>(objetoSalvo, HttpStatus.OK);
    }

    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<?> delete(@PathVariable Long id) throws Exception {
        repository.deleteById((ID) id);

        return new ResponseEntity<>(Map.of("message", "Registro deletado com sucesso"), HttpStatus.OK);
    }

}