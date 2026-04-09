package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.rag.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnoses crop diseases and pests using LLM analysis with RAG-augmented
 * knowledge about plant pathology. Supports text descriptions and image analysis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiagnosisAgent implements AgentNode {

    private final LLMProviderRouter llmRouter;

    @Override
    public String getName() {
        return "DiagnosisAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        long startTime = System.currentTimeMillis();

        String imageUrl = (String) context.getMetadata().get("imageUrl");
        String systemPrompt = buildDiagnosisPrompt(context);

        List<LLMMessage> messages = new ArrayList<>();
        messages.add(LLMMessage.system(systemPrompt));

        // Add conversation history
        if (context.getConversationHistory() != null) {
            messages.addAll(context.getConversationHistory());
        }

        // Add user message with optional image
        if (imageUrl != null && !imageUrl.isBlank()) {
            messages.add(LLMMessage.user(context.getUserQuery(), imageUrl));
        } else {
            messages.add(LLMMessage.user(context.getUserQuery()));
        }

        LLMResponse response = llmRouter.chat(messages);
        long duration = System.currentTimeMillis() - startTime;

        String analysis = response.isSuccess() ? response.getContent() :
                "I apologize, but I'm unable to analyze your crop issue right now. Please try again or describe the symptoms in more detail.";

        return AgentResponse.builder()
                .response(analysis)
                .terminal(true)
                .agentName(getName())
                .provider(response.getProvider())
                .tokensUsed(response.getPromptTokens() + response.getCompletionTokens())
                .executionTimeMs(duration)
                .ragDocumentsUsed(context.getRagDocuments().stream()
                        .map(KnowledgeDocument::getId).toList())
                .build();
    }

    private String buildDiagnosisPrompt(AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert Plant Pathologist for the KhetiSetu agricultural platform.\n");
        prompt.append("Your goal is to diagnose crop issues based on farmer descriptions and images.\n\n");

        prompt.append("**Crop:** ").append(context.getCrop() != null ? context.getCrop() : "Unknown").append("\n");
        prompt.append("**Location:** ").append(context.getLocation() != null ? context.getLocation() : "India").append("\n\n");

        // Inject RAG context
        if (context.getAugmentedContext() != null && !context.getAugmentedContext().isBlank()) {
            prompt.append(context.getAugmentedContext()).append("\n");
        }

        prompt.append("**Response Requirements:**\n");
        prompt.append("1. Identify the likely disease, pest, or nutrient deficiency.\n");
        prompt.append("2. Explain the visible symptoms.\n");
        prompt.append("3. Provide step-by-step treatment (organic methods first, then chemical).\n");
        prompt.append("4. Suggest preventive measures for the future.\n");
        prompt.append("5. Rate the severity as: Low / Medium / High / Critical.\n");
        prompt.append("6. If an image was provided, describe what you observe.\n");
        prompt.append("7. Use markdown formatting for readability.\n");

        if (context.getLanguage() != null && !"en".equals(context.getLanguage())) {
            prompt.append("\nRespond in the user's language: ").append(context.getLanguage()).append("\n");
        }

        return prompt.toString();
    }
}
