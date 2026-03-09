package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.services.GeminiClientService;
import com.khetisetu.event.agnexus.tools.WeatherTool;
import com.khetisetu.event.agnexus.tools.ToolResult;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdvisoryAgent implements AgentNode {

    private final WeatherTool weatherTool;
    private final GeminiClientService gemini;

    @Override
    public String getName() {
        return "AdvisoryAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        ToolResult weather = weatherTool
                .execute(Map.of("location", context.getLocation() != null ? context.getLocation() : "Maharashtra"));
        String weatherData = weather.isSuccess() ? weather.getResult().toString() : "Unknown weather";

        String prompt = String.format(
                "You are an agricultural advisor. Crop: %s, Location: %s, Weather: %s. Query: %s. Provide expert advice on cultivation, pest prevention, or variety selection based on the query.",
                context.getCrop(), context.getLocation(), weatherData, context.getUserQuery());

        String advice = gemini.generateText(prompt);

        return AgentResponse.builder()
                .response(advice)
                .terminal(true)
                .build();
    }
}
