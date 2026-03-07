package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.NotificationProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationAgent implements AgentNode {

    private final NotificationProcessingService notificationService;

    @Override
    public String getName() {
        return "NotificationAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        log.info("Generating push notification for farmer: {}", context.getFarmerId());

        // This is a placeholder for how we might bridge the agent result to the
        // existing service
        // We'll need to extract the message from the context/response of previous
        // agents
        String message = (String) context.getMetadata().getOrDefault("agentOutput", "System update from Agri-Nexus");

        try {
            NotificationRequestEvent request = NotificationRequestEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(context.getFarmerId())
                    .recipient(context.getFarmerId()) // Assuming farmerId is the recipient for PUSH
                    .type("PUSH")
                    .templateName("AGENT_UPDATE")
                    .params(Map.of("message", message))
                    .sendPush(true)
                    .build();

            notificationService.process(request);

            return AgentResponse.builder()
                    .response("Notification sent successfully")
                    .terminal(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to send notification via NotificationAgent", e);
            return AgentResponse.builder()
                    .response("Failed to send notification: " + e.getMessage())
                    .terminal(true)
                    .build();
        }
    }
}
