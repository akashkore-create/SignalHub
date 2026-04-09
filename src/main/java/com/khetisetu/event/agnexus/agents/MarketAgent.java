package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.rag.KnowledgeDocument;
import com.khetisetu.event.agnexus.tools.MandiPriceTool;
import com.khetisetu.event.agnexus.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Market analysis agent that provides crop price insights and trading recommendations.
 * Fetches live mandi price data and augments with RAG knowledge for comprehensive advice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketAgent implements AgentNode {

    private final MandiPriceTool mandiTool;
    private final LLMProviderRouter llmRouter;

    @Override
    public String getName() {
        return "MarketAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();

        // 1. Fetch live market data
        Map<String, Object> params = new HashMap<>();
        params.put("state", context.getLocation() != null ? context.getLocation() : "Maharashtra");
        params.put("crop", context.getCrop() != null ? context.getCrop() : "");

        ToolResult result = mandiTool.execute(params);
        String marketData = result.isSuccess() ? result.getResult().toString() : "No live market data available.";
        context.getToolResults().put("mandiPrices", marketData);

        // 2. Build prompt with RAG context + live data
        String systemPrompt = buildMarketPrompt(context, marketData);

        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(systemPrompt));

        if (context.getConversationHistory() != null) {
            messages.addAll(context.getConversationHistory());
        }

        messages.add(LLMMessage.user(context.getUserQuery()));

        LLMResponse response = llmRouter.chat(messages);
        long duration = System.currentTimeMillis() - startTime;

        String analysis = response.isSuccess() ? response.getContent() :
                "I'm unable to analyze market data right now. Please try again later.";

        return AgentResponse.builder()
                .response(analysis)
                .terminal(true)
                .agentName(getName())
                .provider(response.getProvider())
                .tokensUsed(response.getPromptTokens() + response.getCompletionTokens())
                .executionTimeMs(duration)
                .data(Map.of("marketDataAvailable", result.isSuccess()))
                .ragDocumentsUsed(context.getRagDocuments().stream()
                        .map(KnowledgeDocument::getId).toList())
                .build();
    }

    private String buildMarketPrompt(AgentContext context, String marketData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Professional Agricultural Market Analyst for KhetiSetu.\n");
        prompt.append("Interpret raw market data and provide actionable insights for farmers.\n\n");

        prompt.append("**Crop:** ").append(context.getCrop() != null ? context.getCrop() : "General").append("\n");
        prompt.append("**Location:** ").append(context.getLocation() != null ? context.getLocation() : "India").append("\n\n");

        prompt.append("**Live Market Data:**\n").append(marketData).append("\n\n");

        if (context.getAugmentedContext() != null && !context.getAugmentedContext().isBlank()) {
            prompt.append(context.getAugmentedContext()).append("\n");
        }

        prompt.append("**Response Requirements:**\n");
        prompt.append("1. Summarize the current modal price and 30-day trends (use the provided statistics if available).\n");
        prompt.append("2. Compare with nearby mandis within the state if data is present.\n");
        prompt.append("3. Give a clear 'Sell Now' or 'Wait' recommendation based on whether the current modal price is above or below the 30-day average.\n");
        prompt.append("4. Mention the best mandi identified in the latest records.\n");
        prompt.append("5. Use markdown formatting with clear tables for price comparisons.\n");

        return prompt.toString();
    }
}
