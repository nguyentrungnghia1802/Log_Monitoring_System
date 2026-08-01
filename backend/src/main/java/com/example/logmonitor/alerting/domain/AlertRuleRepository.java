package com.example.logmonitor.alerting.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends MongoRepository<AlertRule, String> {
    List<AlertRule> findByProjectId(String projectId);
    List<AlertRule> findByProjectIdAndEnabled(String projectId, boolean enabled);
    List<AlertRule> findByEnabled(boolean enabled);
    Optional<AlertRule> findByIdAndProjectId(String id, String projectId);
}
