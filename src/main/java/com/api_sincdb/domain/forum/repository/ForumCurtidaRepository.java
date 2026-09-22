package com.api_sincdb.domain.forum.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.api_sincdb.domain.forum.model.ForumCurtida;

public interface ForumCurtidaRepository extends MongoRepository<ForumCurtida, String> {

    Optional<ForumCurtida> findByPostIdAndIdUsuario(String postId, String idUsuario);

    void deleteByPostId(String postId);

    long countByPostId(String postId);
}
