package com.khetisetu.event.logs.consumer;

import com.khetisetu.event.logs.dto.LogEvent;
import com.khetisetu.event.logs.service.LogService;
import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for application log events published by the main khetisetu backend.
 * Persists log entries to the khetisetu-logs MongoDB database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogConsumer {

    private final LogService logService;

    /**
     * Consumes log events from the 'application-logs' Kafka topic and persists them
     * to the khetisetu-logs database via {@link LogService}.
     *
     * @param event the log event to persist
     * @param ack   manual acknowledgment handle
     */
    @KafkaListener(
            topics = "application-logs",
            groupId = "log-consumer-group",
            containerFactory = "logFactory"
    )
    public void consumeLogEvent(LogEvent event, Acknowledgment ack) {
        try {
            log.debug("Received log event: action={}, level={}", event.action(), event.level());

            var actor = new Actor(event.actorId(), event.actorName());
            var entity = new Entity(event.entityId(), event.entityName());

            logService.storeLog(actor, event.action(), entity, event.details(), event.level());
            ack.acknowledge();

            log.debug("Persisted log event: action={}", event.action());
        } catch (Exception e) {
            log.error("Failed to process log event: action={}, error={}", event.action(), e.getMessage(), e);
            // Don't ack — message will be retried by Kafka
        }
    }
}
