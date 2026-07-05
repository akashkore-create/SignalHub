package com.khetisetu.event.notifications.model.logs;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a log entry in the system.
 * This class is used to store logs related to various actions performed by actors on entities.
 *
 * <p>Indexes (timestamp TTL, category, action, level, actor.id) are created at startup by
 * {@link com.khetisetu.event.notifications.config.LogsMongoConfig}.</p>
 */
@Document(collection = "logs")
@Data
@NoArgsConstructor
public class Log {
    private String id;
    private Instant timestamp;
    private String level; // e.g., "INFO", "WARN", "ERROR"
    private Actor actor;
    private String action; // e.g., "USER_LOGIN_SUCCESS", "ECOM_PRODUCT_CREATE"
    private Entity entity;
    private String details;
    private String category; // e.g., "USER", "ORDER", "PAYMENT" — see LogCategory
    private String service;  // originating service, e.g., "khetisetu-core"
    private String traceId;  // request correlation id propagated from the producer
    private Map<String, String> metadata; // optional structured context

    public Log(String id, Instant timestamp, String level, Actor actor, String action, Entity entity, String details) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.actor = actor;
        this.action = action;
        this.entity = entity;
        this.details = details;
    }
}
