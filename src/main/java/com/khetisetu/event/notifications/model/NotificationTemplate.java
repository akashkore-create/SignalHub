// src/main/java/com/khetisetu/event/notifications/model/NotificationTemplate.java
package com.khetisetu.event.notifications.model;

import lombok.Data;

@Data
public class NotificationTemplate {
    private String name;
    private String type;      // EMAIL, PUSH, SMS
    private String subject;
    private String content;
}