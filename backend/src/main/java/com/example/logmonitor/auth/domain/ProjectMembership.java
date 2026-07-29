package com.example.logmonitor.auth.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "project_memberships")
@CompoundIndex(name = "user_project_idx", def = "{'userId': 1, 'projectId': 1}", unique = true)
public class ProjectMembership {
    @Id
    private String id;
    private String userId;
    private String projectId;
    private Role role;

    public ProjectMembership() {}

    public ProjectMembership(String userId, String projectId, Role role) {
        this.userId = userId;
        this.projectId = projectId;
        this.role = role;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
