// src/main/java/com/khetisetu/event/notifications/dto/NotificationEvent.java
package com.khetisetu.event.notifications.dto;

import com.khetisetu.event.notifications.model.EmailSenderConfig;

import java.util.Map;

public record NotificationEvent(
        String type,           // EMAIL, PUSH, SMS
        String recipient,      // email (for EMAIL/SMS), userId (for PUSH)
        String templateName,
        Map<String, String> params,
        String language,        // MUST be sent from producer
        EmailSenderConfig senderConfig  // Now included!
) {}