package com.khetisetu.event.notifications.controller;

import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for notification operations.
 * Called by the main khetisetu backend via WebClient for displaying notifications in the app.
 *
 * <p>All endpoints require a valid X-API-Key header (enforced by
 * {@link com.khetisetu.event.config.ApiKeyAuthFilter}).</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationApiController {

    private final NotificationQueryService queryService;

    /**
     * Gets paginated notifications for a user, sorted by creation date (newest first).
     *
     * @param userId the user ID
     * @param type   optional notification type filter (EMAIL, PUSH, SMS)
     * @param page   page number (0-indexed)
     * @param size   page size
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<Notification>> getUserNotifications(
            @PathVariable String userId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        var notifications = queryService.getUserNotifications(userId, type, pageable);

        log.debug("Fetched {} notifications for user {}", notifications.getNumberOfElements(), userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Gets unread notifications for a user.
     */
    @GetMapping("/user/{userId}/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@PathVariable String userId) {
        return ResponseEntity.ok(queryService.getUnreadNotifications(userId));
    }

    /**
     * Gets unread notification count for a user.
     */
    @GetMapping("/user/{userId}/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable String userId) {
        Long count = queryService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Marks a single notification as read.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable String notificationId) {
        return ResponseEntity.ok(queryService.markAsRead(notificationId));
    }

    /**
     * Marks all notifications as read for a user.
     */
    @PatchMapping("/user/{userId}/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(@PathVariable String userId) {
        queryService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("status", "OK", "message", "All notifications marked as read"));
    }
}
