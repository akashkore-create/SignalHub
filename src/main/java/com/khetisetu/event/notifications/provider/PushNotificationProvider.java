package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.UserTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("PUSH")
@RequiredArgsConstructor
@Slf4j
public class PushNotificationProvider implements NotificationProvider {

    private final UserTokenService userTokenService;

    @Override
    public String getType() {
        return "PUSH";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(NotificationRequestEvent event,
            com.khetisetu.event.notifications.model.Notification notificationRecord) {
        String token = userTokenService.getFcmToken(event.recipient());

        if (token == null) {
            log.warn("No FCM token found for user: {}", event.recipient());
            // We might not want to throw exception here to avoid infinite retries if the
            // user just doesn't have a token.
            // But for now, let's keep it consistent or throw a specific exception.
            throw new IllegalArgumentException("No FCM token found for user: " + event.recipient());
        }

        String title = event.params().getOrDefault("title", "Notification");
        String body = event.params().getOrDefault("body", "");

        Message.Builder messageBuilder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (event.params() != null) {
            event.params().forEach((k, v) -> {
                if (v != null)
                    messageBuilder.putData(k, String.valueOf(v));
            });
        }

        try {
            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage());
            throw new RuntimeException("FCM Send Failed", e);
        }
    }
}
