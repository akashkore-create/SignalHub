// src/main/java/com/khetisetu/event/notifications/model/Notification.java
package com.khetisetu.event.notifications.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    private String type;
    private String recipient;
    private String subject;
    private String content;
    private String status;
    private String errorMessage;
    private int retryCount;
    private String templateName;
    private boolean isRead = false;
    private Instant createdAt;
    private Instant updatedAt;
}