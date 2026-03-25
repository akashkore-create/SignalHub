package com.khetisetu.event.notifications.service;

import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service layer for notification read/write queries.
 * Used by {@link com.khetisetu.event.notifications.controller.NotificationApiController}
 * to serve notification data to the main khetisetu backend via REST APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    /**
     * Gets paginated notifications for a user, optionally filtered by type.
     */
    public Page<Notification> getUserNotifications(String userId, String type, Pageable pageable) {
        if (type != null && !type.isEmpty()) {
            return notificationRepository.findByUserIdAndType(userId, type, pageable);
        }
        return notificationRepository.findByUserId(userId, pageable);
    }

    /**
     * Gets unread notifications for a user.
     */
    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndIsReadFalse(userId);
    }

    /**
     * Gets unread notification count for a user.
     */
    public Long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Marks a single notification as read.
     */
    public Notification markAsRead(String notificationId) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setUpdatedAt(Instant.now());
            notificationRepository.save(notification);
            log.info("Marked notification {} as read for user {}", notificationId, notification.getUserId());
        }
        return notification;
    }

    /**
     * Marks all notifications as read for a user.
     */
    public void markAllAsRead(String userId) {
        var unreadNotifications = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unreadNotifications.forEach(n -> {
            n.setRead(true);
            n.setUpdatedAt(Instant.now());
        });
        notificationRepository.saveAll(unreadNotifications);
        log.info("Marked {} notifications as read for user {}", unreadNotifications.size(), userId);
    }
}
