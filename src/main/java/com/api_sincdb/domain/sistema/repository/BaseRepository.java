package com.api_sincdb.domain.sistema.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;


@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {
    Page<T> findByIdTenant(String idTenant, Pageable pageable);
        List<T> findAllByIdTenant(String idTenant);

}