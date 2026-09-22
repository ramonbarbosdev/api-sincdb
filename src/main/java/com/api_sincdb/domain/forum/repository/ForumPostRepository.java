package com.api_sincdb.domain.forum.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.api_sincdb.domain.forum.model.ForumPost;
import com.api_sincdb.enums.ForumPostStatus;
import com.api_sincdb.enums.TipoFeedback;

public interface ForumPostRepository extends MongoRepository<ForumPost, String> {

    List<ForumPost> findAllByOrderByCreatedAtDesc();

    long countByTipoAndStatusPostNot(TipoFeedback tipo, ForumPostStatus status);

    long countByTipoAndStatusPost(TipoFeedback tipo, ForumPostStatus status);
}
