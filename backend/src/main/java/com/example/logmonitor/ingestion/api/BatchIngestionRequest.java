package com.example.logmonitor.ingestion.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchIngestionRequest(
    @NotEmpty List<@Valid IngestionRequest> events
) {
}
