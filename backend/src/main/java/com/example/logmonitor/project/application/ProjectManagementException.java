package com.example.logmonitor.project.application;

public class ProjectManagementException extends RuntimeException {

    private final Kind kind;
    private final String code;

    public ProjectManagementException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind getKind() { return kind; }
    public String getCode() { return code; }

    public enum Kind {
        VALIDATION,
        NOT_FOUND,
        CONFLICT
    }
}
