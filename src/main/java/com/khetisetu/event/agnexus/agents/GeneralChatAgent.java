package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.utils.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles general/casual queries that don't fit specialized agents.
 * Acts as the default fallback agent for the chatbot, providing
 * friendly conversational responses about farming and KhetiSetu platform.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeneralChatAgent implements AgentNode {

    private final LLMProviderRouter llmRouter;
    private final PromptLoader promptLoader;

    @Override
    public String getName() {
        return "GeneralChatAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();

        String systemPrompt = promptLoader.loadPrompt("general_chat_prompt");

        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(systemPrompt));

        // Add RAG context if available
        if (context.getAugmentedContext() != null && !context.getAugmentedContext().isBlank()) {
            messages.add(LLMMessage.system(context.getAugmentedContext()));
        }

        // Add conversation history for context continuity
        if (context.getConversationHistory() != null) {
            messages.addAll(context.getConversationHistory());
        }

        messages.add(LLMMessage.user(context.getUserQuery()));

        LLMResponse response = llmRouter.chat(messages);
        long duration = System.currentTimeMillis() - startTime;

        String reply = response.isSuccess() ? response.getContent() :
                "I'm sorry, I'm having trouble responding right now. Can you please try again?";

        return AgentResponse.builder()
                .response(reply)
                .terminal(true)
                .agentName(getName())
                .provider(response.getProvider())
                .tokensUsed(response.getPromptTokens() + response.getCompletionTokens())
                .executionTimeMs(duration)
                .build();
    }
}
