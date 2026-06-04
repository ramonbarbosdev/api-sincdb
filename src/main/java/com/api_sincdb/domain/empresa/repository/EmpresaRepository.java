package com.api_sincdb.domain.empresa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.empresa.model.Empresa;

import jakarta.transaction.Transactional;

@Repository
public interface EmpresaRepository extends MongoRepository<Empresa, String> {

        @Query(value = "{}", sort = "{ cd_empresa : -1 }")
        Optional<Empresa> findTopByOrderByCd_empresaDesc();

        @Query("{ 'cd_empresa': ?0 }")
        Optional<Empresa> findByCd_empresa(String cd_empresa);

        @Query("{ 'nm_empresa': ?0 }")
        Optional<Empresa> findByNm_empresa(String nm_empresa);

        @Query("{ 'id_tenant': ?0, 'fl_ativo': true }")
        Optional<Empresa> findById_tenantAndFl_ativoTrue(String id_tenant);

        @Query("{ 'id': { $in: ?0 }, 'fl_ativo': true }")
        List<Empresa> findById_empresaInAndFl_ativoTrue(List<String> ids);
}
