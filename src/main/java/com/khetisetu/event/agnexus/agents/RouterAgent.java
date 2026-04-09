package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.utils.PromptLoader;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes incoming queries to the appropriate specialized agent.
 * Uses the LLM to analyze the user's query, conversation context,
 * and decide which agent should handle it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RouterAgent implements AgentNode {

    private final LLMProviderRouter llmRouter;
    private final PromptLoader promptLoader;

    @Override
    public String getName() {
        return "RouterAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();

        String prompt = promptLoader.loadPrompt("router_prompt")
                .replace("{{userQuery}}", context.getUserQuery())
                .replace("{{crop}}", context.getCrop() != null ? context.getCrop() : "Not specified")
                .replace("{{location}}", context.getLocation() != null ? context.getLocation() : "Not specified");

        // Build messages with conversation history for context-aware routing
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(prompt));

        // Add recent conversation context for better routing
        if (context.getConversationHistory() != null && !context.getConversationHistory().isEmpty()) {
            int historySize = Math.min(context.getConversationHistory().size(), 4);
            messages.addAll(context.getConversationHistory().subList(
                    context.getConversationHistory().size() - historySize,
                    context.getConversationHistory().size()));
        }

        messages.add(LLMMessage.user(context.getUserQuery()));

        LLMResponse response = llmRouter.chat(messages);
        String decision = response.isSuccess() ? response.getContent().trim() : "GeneralChatAgent";

        // Clean up the decision — LLM might return extra text
        decision = cleanAgentDecision(decision);

        long duration = System.currentTimeMillis() - startTime;
        log.info("RouterAgent decision: '{}' for query: '{}' ({}ms, provider={})",
                decision, context.getUserQuery().substring(0, Math.min(50, context.getUserQuery().length())),
                duration, response.getProvider());

        return AgentResponse.builder()
                .response("Routing query to " + decision)
                .nextNode(decision)
                .terminal(false)
                .provider(response.getProvider())
                .tokensUsed(response.getPromptTokens() + response.getCompletionTokens())
                .executionTimeMs(duration)
                .build();
    }

    /**
     * Clean up the LLM's agent decision to extract just the agent name.
     */
    private String cleanAgentDecision(String raw) {
        if (raw == null || raw.isBlank()) return "GeneralChatAgent";

        // Try to find known agent names in the response
        String[] knownAgents = {"DiagnosisAgent", "MarketAgent", "AdvisoryAgent", "GeneralChatAgent"};
        for (String agent : knownAgents) {
            if (raw.contains(agent)) {
                return agent;
            }
        }

        // If the response is just one word and a valid agent name (case-insensitive)
        String trimmed = raw.replaceAll("[^a-zA-Z]", "");
        for (String agent : knownAgents) {
            if (trimmed.equalsIgnoreCase(agent)) {
                return agent;
            }
        }

        log.warn("Unexpected routing decision '{}', defaulting to GeneralChatAgent", raw);
        return "GeneralChatAgent";
    }
}
