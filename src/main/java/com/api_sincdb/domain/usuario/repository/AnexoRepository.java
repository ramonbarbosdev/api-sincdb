package com.api_sincdb.domain.usuario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.usuario.model.Anexo;

import jakarta.transaction.Transactional;


@Repository
public interface AnexoRepository extends JpaRepository<Anexo, Long> {

    @Query(value = "SELECT * FROM anexo WHERE nm_model = ?1 and id_model = ?2", nativeQuery = true)
    List<Anexo> findByModelByIdModel(String nome, Long id);

    @Query(value = "SELECT * FROM anexo WHERE nm_model = ?1", nativeQuery = true)
    List<Anexo> findByModel(String nome);
    
    @Query(value = "SELECT * FROM anexo WHERE id_model = ?1", nativeQuery = true)
    List<Anexo> findByIdModel(long id);
    
    @Query(value = "SELECT * FROM anexo WHERE nm_model = ?1 and id_model = ?2  and id_detalhe  = ?3", nativeQuery = true)
    List<Anexo> findByModelByIdModelByIdDetalhe(String nome, Long id, Long id_detalhe);

	@Query(nativeQuery = true, value = "select * from anexo where id_model = ?1 and nm_model =  ?2")
	List<Anexo> findByIdModelByNmModel(Long id_model, String nm_model);
}