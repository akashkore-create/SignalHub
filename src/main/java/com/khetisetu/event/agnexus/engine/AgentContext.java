package com.khetisetu.event.agnexus.engine;

import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.rag.KnowledgeDocument;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object passed through the agent graph during execution.
 * Contains user input, conversation history, RAG documents, execution state,
 * and accumulated results from agent/tool execution.
 */
@Data
@Builder
public class AgentContext {

    // === User Input ===
    private String userQuery;
    private String farmerId;
    private String location;
    private String crop;
    private String sessionId;
    private String language;

    // === Conversation Memory ===
    private String conversationId;
    @Builder.Default
    private List<LLMMessage> conversationHistory = new ArrayList<>();

    // === RAG Context ===
    @Builder.Default
    private List<KnowledgeDocument> ragDocuments = new ArrayList<>();
    private String augmentedContext;

    // === Execution State ===
    @Builder.Default
    private GraphState currentState = GraphState.ROUTING;
    @Builder.Default
    private List<ExecutionStep> executionTrace = new ArrayList<>();
    @Builder.Default
    private int iterationCount = 0;

    // === Agent Memory & Results ===
    @Builder.Default
    private Map<String, Object> agentMemory = new HashMap<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    @Builder.Default
    private Map<String, Object> toolResults = new HashMap<>();

    /**
     * Add a step to the execution trace for debugging/admin visibility.
     */
    public void addTraceStep(String agentName, String action, String detail, long durationMs) {
        executionTrace.add(new ExecutionStep(agentName, action, detail, durationMs));
    }

    /**
     * Represents one step in the execution trace.
     */
    public record ExecutionStep(String agentName, String action, String detail, long durationMs) {}
}
