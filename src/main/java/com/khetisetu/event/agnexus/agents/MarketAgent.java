package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.tools.MandiPriceTool;
import com.khetisetu.event.agnexus.tools.ToolResult;
import com.khetisetu.event.agnexus.services.GeminiClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketAgent implements AgentNode {

    private final MandiPriceTool mandiTool;
    private final GeminiClientService gemini;

    @Override
    public String getName() {
        return "MarketAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        Map<String, Object> params = new HashMap<>();
        params.put("state", context.getLocation());
        params.put("crop", context.getCrop());

        ToolResult result = mandiTool.execute(params);
        String marketData = result.isSuccess() ? result.getResult().toString() : "No market data available.";

        String prompt = "You are a market analyst. Based on this market data, provide a concise summary and recommendation for the farmer:\n"
                + marketData;
        String analysis = gemini.generateText(prompt);

        return AgentResponse.builder()
                .response(analysis)
                .terminal(true)
                .build();
    }
}
