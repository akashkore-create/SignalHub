package com.khetisetu.event.logs.consumer;

import com.khetisetu.event.logs.LogCategory;
import com.khetisetu.event.logs.dto.LogEvent;
import com.khetisetu.event.logs.service.LogService;
import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import com.khetisetu.event.notifications.model.logs.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Kafka consumer for application log events published by the main khetisetu backend.
 * Persists log entries to the khetisetu-logs MongoDB database.
 *
 * <p>Failures are rethrown so the container's error handler (configured on
 * {@code logFactory}) retries with backoff and finally skips + logs poison messages,
 * instead of blocking the partition forever.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogConsumer {

    private final LogService logService;

    @KafkaListener(
            topics = "application-logs",
            groupId = "log-consumer-group",
            containerFactory = "logFactory"
    )
    public void consumeLogEvent(LogEvent event, Acknowledgment ack) {
        log.debug("Received log event: action={}, level={}", event.action(), event.level());

        Log entry = new Log();
        // Prefer the producer's timestamp so the log reflects when the event actually
        // happened, not when it was consumed (consumer lag would otherwise skew it).
        entry.setTimestamp(event.timestamp() != null ? event.timestamp() : Instant.now());
        entry.setLevel(event.level());
        entry.setActor(new Actor(event.actorId(), event.actorName()));
        entry.setAction(event.action());
        entry.setEntity(new Entity(event.entityId(), event.entityName()));
        entry.setDetails(event.details());
        entry.setCategory(event.category() != null ? event.category() : LogCategory.fromAction(event.action()));
        entry.setService(event.service() != null ? event.service() : "khetisetu-core");
        entry.setTraceId(event.traceId());
        entry.setMetadata(event.metadata());

        logService.store(entry);
        ack.acknowledge();
    }
}
