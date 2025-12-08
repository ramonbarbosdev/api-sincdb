package com.api_sincdb.controller.base;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public abstract class BaseControllerMongo<T, String> {

    @Autowired
    protected MongoRepository<T, String> repository;

    @GetMapping("/listar")
    public Page<T> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @GetMapping("/")
    public ResponseEntity<List<T>> obterTodos() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<T> obterPorId(@PathVariable String id) {

        Optional<T> resultado = repository.findById(id);

        if (resultado.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        return ResponseEntity.ok(resultado.get());
    }

    @PostMapping("/")
    public ResponseEntity<?> cadastrar(@RequestBody T objeto) throws Exception {
        repository.save(objeto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Registro salvo com sucesso!"));
    }

    @PutMapping("/")
    public ResponseEntity<T> atualizar(@RequestBody T objeto) {
        return ResponseEntity.ok(repository.save(objeto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Registro deletado com sucesso!"));
    }
}
