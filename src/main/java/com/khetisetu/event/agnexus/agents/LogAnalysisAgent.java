package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.services.GeminiClientService;
import com.khetisetu.event.agnexus.tools.LogSearchTool;
import com.khetisetu.event.agnexus.tools.ToolResult;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogAnalysisAgent implements AgentNode {

    private final GeminiClientService gemini;
    private final LogSearchTool logSearchTool;

    @Override
    public String getName() {
        return "LogAnalysisAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        String keyword = (String) context.getMetadata().get("keyword");
        ToolResult logResult = logSearchTool
                .execute(Map.of("keyword", keyword != null ? keyword : "ERROR", "lines", 50));

        String logData = logResult.isSuccess() ? logResult.getResult().toString() : "No logs found";
        String prompt = "You are a senior Java engineer. Analyze these microservice logs and suggest the root cause and a specific fix:\n"
                + logData;
        String analysis = gemini.generateText(prompt);

        return AgentResponse.builder()
                .response(analysis)
                .terminal(true)
                .build();
    }
}
