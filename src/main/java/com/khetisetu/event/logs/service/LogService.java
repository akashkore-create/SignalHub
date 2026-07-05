package com.khetisetu.event.logs.service;

import com.khetisetu.event.logs.LogCategory;
import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import com.khetisetu.event.notifications.model.logs.Log;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for persisting log entries to the khetisetu-logs MongoDB database.
 * Called by {@link com.khetisetu.event.logs.consumer.LogConsumer} for Kafka-consumed logs.
 */
@Service
@Slf4j
public class LogService {

    private final MongoTemplate mongoTemplate;

    public LogService(@Qualifier("logsMongoTemplate") MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Persists a fully-populated log entry. The id is left null so MongoDB assigns
     * a unique ObjectId (the previous "LOG" + currentTimeMillis scheme collided
     * under concurrent writes).
     */
    public Log store(Log entry) {
        if (entry.getTimestamp() == null) entry.setTimestamp(Instant.now());
        if (entry.getCategory() == null) entry.setCategory(LogCategory.fromAction(entry.getAction()));
        if (entry.getService() == null) entry.setService("notification-event-service");
        if (entry.getTraceId() == null) entry.setTraceId(MDC.get("traceId"));
        Log saved = mongoTemplate.save(entry, "logs");
        log.debug("Log stored: id={}, action={}, level={}", saved.getId(), entry.getAction(), entry.getLevel());
        return saved;
    }

    /**
     * Legacy convenience overload for internal callers.
     */
    public void storeLog(Actor actor, String action, Entity entity, String logDetails, String level) {
        Log entry = new Log(null, Instant.now(), level, actor, action, entity, logDetails);
        store(entry);
    }
}
