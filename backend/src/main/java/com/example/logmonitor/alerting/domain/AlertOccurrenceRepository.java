package com.example.logmonitor.alerting.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertOccurrenceRepository extends MongoRepository<AlertOccurrence, String> {
    List<AlertOccurrence> findByProjectId(String projectId);
    List<AlertOccurrence> findByProjectIdAndStatus(String projectId, String status);
}
