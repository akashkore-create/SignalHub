package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.tools.LogSearchTool;
import com.khetisetu.event.agnexus.tools.ToolResult;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analyzes application logs using LLM to identify patterns,
 * root causes, and suggest fixes for microservice issues.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogAnalysisAgent implements AgentNode {

    private final LLMProviderRouter llmRouter;
    private final LogSearchTool logSearchTool;

    @Override
    public String getName() {
        return "LogAnalysisAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();

        String keyword = (String) context.getMetadata().get("keyword");
        ToolResult logResult = logSearchTool
                .execute(Map.of("keyword", keyword != null ? keyword : "ERROR", "lines", 50));

        String logData = logResult.isSuccess() ? logResult.getResult().toString() : "No logs found";

        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(
                "You are a senior Java engineer. Analyze these microservice logs and suggest the root cause and a specific fix."));
        messages.add(LLMMessage.user("Analyze these logs:\n" + logData));

        LLMResponse response = llmRouter.chat(messages);
        long duration = System.currentTimeMillis() - startTime;

        return AgentResponse.builder()
                .response(response.isSuccess() ? response.getContent() : "Unable to analyze logs.")
                .terminal(true)
                .agentName(getName())
                .provider(response.getProvider())
                .executionTimeMs(duration)
                .build();
    }
}
