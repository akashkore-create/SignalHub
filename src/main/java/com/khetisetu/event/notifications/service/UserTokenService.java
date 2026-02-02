package com.khetisetu.event.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTokenService {

    private final MongoTemplate mongoTemplate;

    public String getFcmToken(String userId) {
        try {
            Query query = new Query(Criteria.where("_id").is(userId));
            query.fields().include("pushSubscription");
            Map<String, Object> user = mongoTemplate.findOne(query, Map.class, "users");

            if (user != null && user.containsKey("pushSubscription")) {
                Object sub = user.get("pushSubscription");
                if (sub instanceof Map) {
                    // Assuming the pushSubscription map contains String keys and Object values
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pushSubscriptionMap = (Map<String, Object>) sub;
                    if (pushSubscriptionMap.containsKey("token")) {
                        return (String) pushSubscriptionMap.get("token");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch token for user {}: {}", userId, e.getMessage());
        }
        return null;
    }
}
