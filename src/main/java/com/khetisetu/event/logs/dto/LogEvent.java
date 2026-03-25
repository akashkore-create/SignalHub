package com.khetisetu.event.logs.dto;

import java.time.Instant;

/**
 * Kafka event DTO for deserializing log entries from the 'application-logs' topic.
 * Published by the main khetisetu backend's LogService.
 */
public record LogEvent(
        String actorId,
        String actorName,
        String action,
        String entityId,
        String entityName,
        String details,
        String level,
        Instant timestamp
) {
}
