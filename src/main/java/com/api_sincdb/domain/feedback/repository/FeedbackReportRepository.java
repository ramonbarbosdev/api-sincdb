package com.api_sincdb.domain.feedback.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.api_sincdb.domain.feedback.model.FeedbackReport;

public interface FeedbackReportRepository extends MongoRepository<FeedbackReport, String> {

    List<FeedbackReport> findByUsuarioOrderByCreatedAtDesc(String usuario);

    List<FeedbackReport> findByUsuarioInOrderByCreatedAtDesc(Collection<String> usuarios);

    List<FeedbackReport> findAllByOrderByCreatedAtDesc();
}
