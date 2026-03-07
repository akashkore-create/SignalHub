package com.khetisetu.event.agnexus.services;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentGraphEngine;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentEventService {

    private final AgentGraphEngine engine;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "agnexus-queries", groupId = "agnexus-group")
    public void handleAgentQuery(String message) {
        log.info("Received agent query via Kafka: {}", message);
        try {
            JSONObject json = new JSONObject(message);
            String sessionId = json.optString("sessionId", "session-" + System.currentTimeMillis());

            AgentContext context = AgentContext.builder()
                    .userQuery(json.getString("query"))
                    .farmerId(json.optString("farmerId", "unknown"))
                    .crop(json.optString("crop", "General"))
                    .location(json.optString("location", "Maharashtra"))
                    .sessionId(sessionId)
                    .build();

            AgentResponse response = engine.executeGraph("RouterAgent", context);
            log.info("Agent execution complete. Terminal: {}", response.isTerminal());

            // Send response back to Kafka
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("farmerId", context.getFarmerId());
            result.put("response", response.getResponse());
            result.put("data", response.getData());

            kafkaTemplate.send("agnexus-responses", sessionId, result);
            log.info("Sent response back to context: {}", sessionId);

        } catch (Exception e) {
            log.error("Error processing agent query from Kafka", e);
        }
    }
}
