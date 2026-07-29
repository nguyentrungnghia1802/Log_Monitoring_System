package com.example.logmonitor.auth.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMembershipRepository extends MongoRepository<ProjectMembership, String> {
    Optional<ProjectMembership> findByUserIdAndProjectId(String userId, String projectId);
    List<ProjectMembership> findByUserId(String userId);
}
