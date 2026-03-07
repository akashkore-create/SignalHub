package com.khetisetu.event.agnexus.engine;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentGraphEngine {

    private final Map<String, AgentNode> nodes = new HashMap<>();

    public AgentGraphEngine(List<AgentNode> agentNodes) {
        for (AgentNode node : agentNodes) {
            nodes.put(node.getName(), node);
        }
    }

    public AgentResponse executeGraph(String startNodeName, AgentContext context) {
        String currentNodeName = startNodeName;
        AgentResponse lastResponse = null;

        while (currentNodeName != null && nodes.containsKey(currentNodeName)) {
            log.info("Executing agent node: {}", currentNodeName);
            AgentNode node = nodes.get(currentNodeName);
            lastResponse = node.execute(context);

            if (lastResponse.isTerminal()) {
                log.info("Reached terminal node: {}", currentNodeName);
                break;
            }

            currentNodeName = lastResponse.getNextNode();
        }

        return lastResponse;
    }
}
