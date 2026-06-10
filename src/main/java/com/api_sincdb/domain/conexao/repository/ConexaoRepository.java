package com.api_sincdb.domain.conexao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.conexao.model.Conexao;

@Repository
public interface ConexaoRepository extends MongoRepository<Conexao, String> {

   @Query("{ 'idUsuario': ?0, 'fl_ativo': true }")
   Conexao findFirstByIdUsuarioAndFl_ativoTrue(String idUsuario);

   @Query("{ 'id_empresa': ?0, 'idUsuario': ?1, 'fl_ativo': true }")
   List<Conexao> findById_empresaAndIdUsuarioAndFl_ativoTrue(String id_empresa, String idUsuario);

   @Query("{ 'id_empresa': ?0, 'idUsuario': ?1, 'fl_padrao': true, 'fl_ativo': true }")
   Optional<Conexao> findFirstById_empresaAndIdUsuarioAndFl_padraoTrueAndFl_ativoTrue(
         String id_empresa,
         String idUsuario);

   @Query("{ '_id': ?0, 'id_empresa': ?1, 'idUsuario': ?2 }")
   Optional<Conexao> findByIdAndId_empresaAndIdUsuario(String id, String id_empresa, String idUsuario);

   @Query(value = "{ 'id_empresa': ?0, 'idUsuario': ?1, 'fl_ativo': true }", exists = true)
   boolean existsById_empresaAndIdUsuarioAndFl_ativoTrue(String id_empresa, String idUsuario);

}
