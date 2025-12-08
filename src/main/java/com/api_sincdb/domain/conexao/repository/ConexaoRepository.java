package com.api_sincdb.domain.conexao.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.conexao.model.Conexao;

import jakarta.transaction.Transactional;

@Repository
public interface ConexaoRepository extends MongoRepository<Conexao, String> {

   Conexao findFirstByIdUsuario(String idUsuario);

}