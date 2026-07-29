package com.example.logmonitor.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface LogEventRepository extends MongoRepository<LogEventDocument, String> {
}
