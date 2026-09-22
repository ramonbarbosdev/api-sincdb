package com.api_sincdb.domain.forum.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.api_sincdb.domain.forum.model.ForumSystemStatus;

public interface ForumSystemStatusRepository extends MongoRepository<ForumSystemStatus, String> {
}
