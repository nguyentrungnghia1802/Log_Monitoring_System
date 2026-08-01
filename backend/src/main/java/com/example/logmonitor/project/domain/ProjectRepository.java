package com.example.logmonitor.project.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByOrganizationIdOrderByCreatedAtAsc(String organizationId);
    List<Project> findByIdInAndOrganizationIdOrderByCreatedAtAsc(List<String> ids, String organizationId);
    Optional<Project> findByIdAndOrganizationId(String id, String organizationId);
    Optional<Project> findByOrganizationIdAndKey(String organizationId, String key);
}
