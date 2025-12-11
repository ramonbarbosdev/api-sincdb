package com.api_sincdb.domain.usuario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.usuario.model.UsuarioOnline;
import com.api_sincdb.domain.usuario.protection.UsuarioOnlineDetalhadoProjection;

import jakarta.transaction.Transactional;

@Repository
@Transactional
public interface UsuarioOnlineRepository extends MongoRepository<UsuarioOnline, String> {

        Optional<UsuarioOnline> findByLogin(String login);

        @Aggregation(pipeline = {
                        "{ $match: { login: { $ne: ?0 } } }",
                        "{ $lookup: { from: 'usuario', localField: 'login', foreignField: 'login', as: 'usuarioInfo' } }",
                        "{ $unwind: '$usuarioInfo' }",
                        "{ $project: { login: 1, fl_ativo: 1, dt_ultimologin: 1, nome: '$usuarioInfo.nome' } }"
        })
        List<UsuarioOnlineDetalhadoProjection> obterInformacoesUsuario(String login);

}
