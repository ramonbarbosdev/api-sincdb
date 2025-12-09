
package com.api_sincdb.domain.usuario.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.api_sincdb.domain.usuario.model.Usuario;

@Component
public class UsuarioRepositoryCustomImpl implements UsuarioRepositoryCustom {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void atualizarTokenUser(String token, String login) {
        Query query = new Query(Criteria.where("login").is(login));
        Update update = new Update().set("token", token);
        mongoTemplate.updateFirst(query, update, Usuario.class);
    }
}