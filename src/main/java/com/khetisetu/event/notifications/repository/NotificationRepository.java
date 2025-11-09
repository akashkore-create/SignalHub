// src/main/java/com/khetisetu/event/notifications/repository/NotificationRepository.java
package com.khetisetu.event.notifications.repository;

import com.khetisetu.event.notifications.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
}