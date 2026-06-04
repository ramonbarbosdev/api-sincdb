package com.api_sincdb.domain.conexao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.conexao.model.Conexao;

@Repository
public interface ConexaoRepository extends MongoRepository<Conexao, String> {

   Conexao findFirstByIdUsuario(String idUsuario);

   List<Conexao> findByIdUsuario(String idUsuario);

   @Query("{ 'id_empresa': ?0, 'fl_ativo': true }")
   List<Conexao> findById_empresaAndFl_ativoTrue(String id_empresa);

   @Query("{ 'id_empresa': ?0, 'fl_padrao': true, 'fl_ativo': true }")
   Optional<Conexao> findFirstById_empresaAndFl_padraoTrueAndFl_ativoTrue(String id_empresa);

   @Query("{ '_id': ?0, 'id_empresa': ?1 }")
   Optional<Conexao> findByIdAndId_empresa(String id, String id_empresa);

   @Query(value = "{ 'id_empresa': ?0, 'fl_ativo': true }", exists = true)
   boolean existsById_empresaAndFl_ativoTrue(String id_empresa);

}
