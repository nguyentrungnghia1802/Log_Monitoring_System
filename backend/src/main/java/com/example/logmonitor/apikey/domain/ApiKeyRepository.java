package com.example.logmonitor.apikey.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
    Optional<ApiKey> findByPublicId(String publicId);
    List<ApiKey> findByKeyPrefix(String keyPrefix);
    List<ApiKey> findByProjectIdOrderByCreatedAtDesc(String projectId);
    Optional<ApiKey> findByIdAndProjectId(String id, String projectId);
}
