// src/main/java/com/khetisetu/event/notifications/consumer/NotificationConsumer.java
package com.khetisetu.event.notifications.consumer;

import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.service.NotificationProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationProcessingService processingService;

    @KafkaListener(topics = "notifications", groupId = "notification-event-group")
    public void listen(NotificationEvent event) {
        log.info("Consumed notification event: {} for {}", event.templateName(), event.recipient());
        processingService.process(event);
    }
}