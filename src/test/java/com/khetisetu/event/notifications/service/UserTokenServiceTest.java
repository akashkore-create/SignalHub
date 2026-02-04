package com.khetisetu.event.notifications.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UserTokenService userTokenService;

    @Test
    void getFcmToken_ShouldReturnToken_WhenTokenKeyExists() {
        // Arrange
        Map<String, Object> pushSubscription = new HashMap<>();
        pushSubscription.put("token", "token_123");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscription", pushSubscription);

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        // Act
        String token = userTokenService.getFcmToken("user_123");

        // Assert
        assertEquals("token_123", token);
    }

    @Test
    void getFcmToken_ShouldReturnToken_WhenKeysFcmKeyExists() {
        // Arrange
        Map<String, Object> keys = new HashMap<>();
        keys.put("fcm", "fcm_token_abc");

        Map<String, Object> pushSubscription = new HashMap<>();
        pushSubscription.put("keys", keys);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscription", pushSubscription);

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        // Act
        String token = userTokenService.getFcmToken("user_123");

        // Assert
        assertEquals("fcm_token_abc", token);
    }

    @Test
    void getFcmToken_ShouldReturnNull_WhenNoTokenPathFound() {
        // Arrange
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscription", new HashMap<>());

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        // Act
        String token = userTokenService.getFcmToken("user_123");

        // Assert
        assertNull(token);
    }

    @Test
    void getFcmToken_ShouldReturnNull_WhenUserNotFound() {
        // Arrange
        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(null);

        // Act
        String token = userTokenService.getFcmToken("user_123");

        // Assert
        assertNull(token);
    }
}
