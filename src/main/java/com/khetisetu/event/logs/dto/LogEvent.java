package com.khetisetu.event.logs.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Kafka event DTO for deserializing log entries from the 'application-logs' topic.
 * Published by the main khetisetu backend's LogService.
 *
 * <p>The enrichment fields (service, traceId, category, metadata) are optional so
 * events from older producers deserialize as null and are backfilled at persistence.</p>
 */
public record LogEvent(
        String actorId,
        String actorName,
        String action,
        String entityId,
        String entityName,
        String details,
        String level,
        Instant timestamp,
        String service,
        String traceId,
        String category,
        Map<String, String> metadata
) {
}
