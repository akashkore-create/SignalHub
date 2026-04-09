package com.khetisetu.event.agnexus.dto;

import com.khetisetu.event.agnexus.engine.AgentContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for chat endpoints.
 * Includes the AI response, execution metadata, and debugging info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** Conversation ID for session continuity */
    private String conversationId;

    /** Session ID */
    private String sessionId;

    /** The AI agent's response text */
    private String response;

    /** Name of the agent that produced the response */
    private String agentUsed;

    /** Execution trace for debugging (admin use) */
    private List<AgentContext.ExecutionStep> executionTrace;

    /** RAG document IDs referenced in the response */
    private List<String> ragDocsUsed;

    /** Total tokens consumed */
    @Builder.Default
    private int tokensUsed = 0;

    /** Total execution time in milliseconds */
    @Builder.Default
    private long executionTimeMs = 0;

    /** LLM provider that served the response */
    private String provider;

    /** Whether a fallback provider was used */
    @Builder.Default
    private boolean fallbackUsed = false;

    /** Any additional data from the agent */
    private Map<String, Object> data;
}
