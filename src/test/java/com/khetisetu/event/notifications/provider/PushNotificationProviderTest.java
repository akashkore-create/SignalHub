package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.FirebaseMessaging;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.service.UserTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationProviderTest {

    @Mock
    private UserTokenService userTokenService;

    private PushNotificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PushNotificationProvider(userTokenService);
    }

    @Test
    void send_ShouldMarkAsSkipped_WhenTokenIsMissing() {
        // Arrange
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(new HashMap<>())
                .build();
        Notification notificationRecord = new Notification();

        when(userTokenService.getFcmToken("user_123")).thenReturn(null);

        // Act
        provider.send(event, notificationRecord);

        // Assert
        assertEquals("SKIPPED", notificationRecord.getStatus());
        assertEquals("No FCM token found", notificationRecord.getErrorMessage());

        // Verify userTokenService WAS called
        verify(userTokenService).getFcmToken("user_123");

        // Ensure Firebase was not called (would be caught by mockStatic if we had it
        // active,
        // but here we just want to ensure we returned early)
    }

    @Test
    void send_ShouldCallFirebase_WhenTokenIsPresent() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("title", "Hello");
        params.put("body", "World");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notificationRecord = new Notification();

        when(userTokenService.getFcmToken("user_123")).thenReturn("fake_token");

        // We use MockedStatic for FirebaseMessaging
        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any())).thenReturn("msg_id");

            // Act
            provider.send(event, notificationRecord);

            // Assert
            verify(firebaseMessaging, times(1)).send(any());
        }
    }
}
