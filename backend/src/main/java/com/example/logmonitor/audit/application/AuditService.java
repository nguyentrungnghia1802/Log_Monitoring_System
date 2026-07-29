package com.example.logmonitor.audit.application;

import com.example.logmonitor.audit.domain.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

@Repository
interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByProjectId(String projectId);
}

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logAction(String actor, String orgId, String projectId, String action, String resourceType, String resourceId, String summary) {
        AuditLog entry = new AuditLog(actor, orgId, projectId, action, resourceType, resourceId, summary);
        auditLogRepository.save(entry);
    }

    public List<AuditLog> getAuditLogs(String projectId) {
        return auditLogRepository.findByProjectId(projectId);
    }
}
