package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.rag.KnowledgeDocument;
import com.khetisetu.event.agnexus.tools.WeatherTool;
import com.khetisetu.event.agnexus.tools.ToolResult;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Agricultural advisory agent providing expert farming advice.
 * Integrates real-time weather data, RAG knowledge, and conversation context
 * for location-aware and season-aware recommendations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdvisoryAgent implements AgentNode {

    private final WeatherTool weatherTool;
    private final LLMProviderRouter llmRouter;

    @Override
    public String getName() {
        return "AdvisoryAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();

        // 1. Fetch weather data
        String location = context.getLocation() != null ? context.getLocation() : "Maharashtra";
        ToolResult weather = weatherTool.execute(Map.of("location", location));
        String weatherData = weather.isSuccess() ? weather.getResult().toString() : "Weather data unavailable.";
        context.getToolResults().put("weather", weatherData);

        // 2. Build prompt
        String systemPrompt = buildAdvisoryPrompt(context, weatherData);

        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(systemPrompt));

        if (context.getConversationHistory() != null) {
            messages.addAll(context.getConversationHistory());
        }

        messages.add(LLMMessage.user(context.getUserQuery()));

        LLMResponse response = llmRouter.chat(messages);
        long duration = System.currentTimeMillis() - startTime;

        String advice = response.isSuccess() ? response.getContent() :
                "I'm unable to provide advice right now. Please try again or ask a different question.";

        return AgentResponse.builder()
                .response(advice)
                .terminal(true)
                .agentName(getName())
                .provider(response.getProvider())
                .tokensUsed(response.getPromptTokens() + response.getCompletionTokens())
                .executionTimeMs(duration)
                .ragDocumentsUsed(context.getRagDocuments().stream()
                        .map(KnowledgeDocument::getId).toList())
                .build();
    }

    private String buildAdvisoryPrompt(AgentContext context, String weatherData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an Expert Agricultural Advisor named 'Kheti-Mitra' for KhetiSetu.\n");
        prompt.append("Provide practical, actionable farming advice tailored to the farmer's context.\n\n");

        prompt.append("**Crop:** ").append(context.getCrop() != null ? context.getCrop() : "General").append("\n");
        prompt.append("**Location:** ").append(context.getLocation() != null ? context.getLocation() : "India").append("\n");
        prompt.append("**Current Weather:** ").append(weatherData).append("\n\n");

        if (context.getAugmentedContext() != null && !context.getAugmentedContext().isBlank()) {
            prompt.append(context.getAugmentedContext()).append("\n");
        }

        prompt.append("**Response Requirements:**\n");
        prompt.append("1. Give specific, actionable advice based on the query.\n");
        prompt.append("2. Consider local weather conditions in your recommendations.\n");
        prompt.append("3. Mention organic/low-cost alternatives first.\n");
        prompt.append("4. Include seasonal timing advice when relevant.\n");
        prompt.append("5. Use markdown formatting for readability.\n");
        prompt.append("6. Be encouraging and supportive in tone.\n");

        if (context.getLanguage() != null && !"en".equals(context.getLanguage())) {
            prompt.append("\nRespond in the user's language: ").append(context.getLanguage()).append("\n");
        }

        return prompt.toString();
    }
}
