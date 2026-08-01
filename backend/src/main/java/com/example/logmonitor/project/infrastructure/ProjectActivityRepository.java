package com.example.logmonitor.project.infrastructure;

import com.example.logmonitor.persistence.LogEventDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Repository
public class ProjectActivityRepository {

    private static final List<String> ERROR_LEVELS = List.of("ERROR", "FATAL");

    private final MongoTemplate mongoTemplate;

    public ProjectActivityRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ActivitySummary summarize(String projectId, Instant since) {
        Query recentQuery = Query.query(new Criteria()
            .andOperator(
                Criteria.where("project_id").is(projectId),
                Criteria.where("received_at").gte(since)
            ));
        long eventCount = mongoTemplate.count(recentQuery, LogEventDocument.class);

        Query errorQuery = Query.query(new Criteria()
            .andOperator(
                Criteria.where("project_id").is(projectId),
                Criteria.where("received_at").gte(since),
                Criteria.where("level").in(ERROR_LEVELS)
            ));
        long errorCount = mongoTemplate.count(errorQuery, LogEventDocument.class);

        List<String> services = mongoTemplate.getCollection(mongoTemplate.getCollectionName(LogEventDocument.class))
            .distinct("service", Query.query(Criteria.where("project_id").is(projectId)).getQueryObject(), String.class)
            .into(new ArrayList<>())
            .stream()
            .filter(service -> service != null && !service.isBlank())
            .sorted(Comparator.naturalOrder())
            .toList();

        Document latest = mongoTemplate.getCollection(mongoTemplate.getCollectionName(LogEventDocument.class))
            .find(Query.query(Criteria.where("project_id").is(projectId)).getQueryObject())
            .projection(new Document("received_at", 1))
            .sort(new Document("received_at", -1))
            .limit(1)
            .first();
        Instant lastReceivedAt = latest == null || latest.getDate("received_at") == null
            ? null
            : latest.getDate("received_at").toInstant();

        return new ActivitySummary(services, eventCount, errorCount, lastReceivedAt);
    }

    public record ActivitySummary(
        List<String> services,
        long eventsLast24Hours,
        long errorEventsLast24Hours,
        Instant lastReceivedAt
    ) {
        public ActivitySummary {
            services = services == null ? List.of() : List.copyOf(services);
        }
    }
}
