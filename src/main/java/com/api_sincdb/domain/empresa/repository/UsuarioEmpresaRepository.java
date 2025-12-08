package com.api_sincdb.domain.empresa.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.empresa.model.UsuarioEmpresa;

import jakarta.transaction.Transactional;

@Repository
public interface UsuarioEmpresaRepository extends MongoRepository<UsuarioEmpresa, String> {

        @Query("{ 'id_usuario': ?0 }")
        List<UsuarioEmpresa> findById_usuario(String id_usuario);

        // REMOVER TODOS VÍNCULOS DO USUÁRIO
        @Query(value = "{ 'id_usuario': ?0 }", delete = true)
        void deleteById_usuario(String id_usuario);

        @Query(value = "{ 'id_usuario': ?0, 'id_empresa': ?1 }", exists = true)
        boolean existsById_usuarioAndId_empresa(String id_usuario, String id_empresa);

}