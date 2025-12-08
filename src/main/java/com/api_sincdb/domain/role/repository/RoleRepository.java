package com.api_sincdb.domain.role.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.usuario.model.Role;

import jakarta.transaction.Transactional;

import org.springframework.data.mongodb.repository.MongoRepository;

@Repository
public interface RoleRepository extends MongoRepository<Role, String> {

    Role findByNomeRole(String nomeRole);
}