package com.khetisetu.event.logs.service;

import com.khetisetu.event.logs.repository.LogRepository;
import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import com.khetisetu.event.notifications.model.logs.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final LogRepository logRepository;
    private final MongoTemplate mongoTemplate;

    public LogService(LogRepository logRepository,
                      @Qualifier("logsMongoTemplate") MongoTemplate mongoTemplate) {
        this.logRepository = logRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Stores a log entry in the khetisetu-logs database.
     *
     * @param actor      Actor performing the action
     * @param action     Action type
     * @param entity     Affected entity
     * @param logDetails Log details
     * @param level      Log level (INFO, WARN, ERROR)
     */
    public void storeLog(Actor actor, String action, Entity entity, String logDetails, String level) {
        String id = "LOG" + System.currentTimeMillis();
        Log logEntry = new Log(id, Instant.now(), level, actor, action, entity, logDetails);
        Log saved = mongoTemplate.save(logEntry, "logs");
        log.debug("Log stored: id={}, action={}, level={}", saved.getId(), action, level);
    }
}