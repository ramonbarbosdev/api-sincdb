package com.api_sincdb.domain.sql.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.api_sincdb.domain.sql.model.SqlExecutionHistory;

@Repository
public interface SqlExecutionHistoryRepository extends MongoRepository<SqlExecutionHistory, String> {
}
