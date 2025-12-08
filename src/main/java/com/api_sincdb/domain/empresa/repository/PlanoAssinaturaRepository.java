package com.api_sincdb.domain.empresa.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.empresa.model.PlanoAssinatura;

import jakarta.transaction.Transactional;

@Repository
public interface PlanoAssinaturaRepository extends MongoRepository<PlanoAssinatura, String> {

   

}