package com.khetisetu.event.notifications.repository;

import com.khetisetu.event.notifications.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for notification entities stored in the khetisetu database.
 * This is the single source of truth for notification storage across the system.
 */
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByRecipient(String recipient);

    // --- Paginated queries ---

    Page<Notification> findByRecipient(String recipient, Pageable pageable);

    Page<Notification> findByRecipientIn(Collection<String> recipients, Pageable pageable);

    Page<Notification> findByRecipientInAndType(Collection<String> recipients, String type, Pageable pageable);

    Page<Notification> findByType(String type, Pageable pageable);

    Page<Notification> findByStatus(String status, Pageable pageable);

    Page<Notification> findByRecipientAndType(String recipient, String type, Pageable pageable);

    // --- Unread queries ---

    List<Notification> findByRecipientInAndIsReadFalse(Collection<String> recipients);

    List<Notification> findByRecipientAndIsReadFalse(String recipient);

    Long countByRecipientInAndIsReadFalse(Collection<String> recipients);

    Long countByRecipientAndIsReadFalse(String recipient);

    // --- Read queries ---

    List<Notification> findByRecipientAndIsReadTrue(String recipient);

    // --- By type ---

    List<Notification> findByRecipientAndType(String recipient, String type);

    List<Notification> findByRecipientAndTypeAndIsReadFalse(String recipient, String type);

    // --- Excluding types ---

    List<Notification> findByRecipientAndTypeNotIn(String recipient, List<String> types);

    List<Notification> findByRecipientAndIsReadFalseAndTypeNotIn(String recipient, List<String> types);

    Long countByRecipientAndIsReadFalseAndTypeNotIn(String recipient, List<String> types);

    // --- User-specific queries using userId ---

    Page<Notification> findByUserId(String userId, Pageable pageable);

    Page<Notification> findByUserIdAndType(String userId, String type, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalse(String userId);

    Long countByUserIdAndIsReadFalse(String userId);
}