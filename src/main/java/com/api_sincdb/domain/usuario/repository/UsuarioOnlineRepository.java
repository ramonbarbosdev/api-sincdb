package com.api_sincdb.domain.usuario.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.model.UsuarioOnline;
import com.api_sincdb.domain.usuario.protection.UsuarioOnlineDetalhadoProjection;

import jakarta.transaction.Transactional;

@Repository
@Transactional
public interface UsuarioOnlineRepository extends JpaRepository<UsuarioOnline, Long> {

        Optional<UsuarioOnline> findByLogin(String login);

        @Query(value = """
                        select u.login, u.nome, uo.fl_ativo, uo.dt_ultimologin  from usuario_online uo
                                join usuario u on uo.login = u.login
                                where u.login <> ?1

                        """, nativeQuery = true)
        List<UsuarioOnlineDetalhadoProjection> obterInformacoesUsuario(String login);
}
