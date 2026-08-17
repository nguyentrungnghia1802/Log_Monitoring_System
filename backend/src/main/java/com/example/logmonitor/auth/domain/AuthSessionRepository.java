package com.example.logmonitor.auth.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthSessionRepository extends MongoRepository<AuthSession, String> {
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
}
