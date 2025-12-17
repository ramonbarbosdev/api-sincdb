package com.api_sincdb.domain.parametro.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.cdi.MongoRepositoryBean;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.empresa.model.Empresa;
import com.api_sincdb.domain.parametro.model.ParametroMaster;

@Repository
public interface ParametroMasterRepository extends MongoRepository<ParametroMaster, String> {
 
        Optional<ParametroMaster> findTopByOrderByCodigoDesc();

        Optional<ParametroMaster> findByCodigo(String codigo);

}
