package com.api_sincdb.domain.usuario.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.usuario.model.Usuario;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String>, UsuarioRepositoryCustom {

    Usuario findByLogin(String login);



}