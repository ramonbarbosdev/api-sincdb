package com.api_sincdb.domain.dashboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.api_sincdb.domain.dashboard.model.SincronizacaoSchema;
import com.api_sincdb.enums.TipoOperacao;

public interface SincronizacaoSchemaRepository extends MongoRepository<SincronizacaoSchema, String> {

    Optional<SincronizacaoSchema> findByBaseNomeAndSchemaNomeAndUsuarioAndOperacao(String base, String schema, String usuario,TipoOperacao tipo);

    List<SincronizacaoSchema> findAllByUsuario(String usuario);
}
