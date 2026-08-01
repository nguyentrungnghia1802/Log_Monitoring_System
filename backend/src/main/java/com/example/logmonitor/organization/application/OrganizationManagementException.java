package com.example.logmonitor.organization.application;

public class OrganizationManagementException extends RuntimeException {

    public enum Kind {
        VALIDATION,
        NOT_FOUND,
        CONFLICT
    }

    private final String code;
    private final Kind kind;

    public OrganizationManagementException(String code, Kind kind, String message) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String getCode() { return code; }
    public Kind getKind() { return kind; }
}
