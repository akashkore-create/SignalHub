package com.khetisetu.event.agnexus.engine;

import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.memory.ConversationMemoryService;
import com.khetisetu.event.agnexus.rag.KnowledgeDocument;
import com.khetisetu.event.agnexus.rag.RAGService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * LangGraph-inspired agent graph execution engine.
 * Manages state-machine-based agent execution with:
 * <ul>
 *   <li>Conditional edge routing between agent nodes</li>
 *   <li>Max iteration limits to prevent infinite loops</li>
 *   <li>Execution trace recording for debugging</li>
 *   <li>Integration with memory service (load/save conversation context)</li>
 *   <li>Integration with RAG (retrieve relevant docs before agent execution)</li>
 *   <li>Error recovery and fallback handling</li>
 * </ul>
 */
@Service
@Slf4j
public class AgentGraphEngine {

    private final Map<String, AgentNode> nodes = new HashMap<>();
    private final ConversationMemoryService memoryService;
    private final RAGService ragService;

    @Value("${agent.max-iterations:10}")
    private int maxIterations;

    @Value("${agent.execution-timeout-seconds:30}")
    private int executionTimeoutSeconds;

    public AgentGraphEngine(List<AgentNode> agentNodes,
                            ConversationMemoryService memoryService,
                            RAGService ragService) {
        for (AgentNode node : agentNodes) {
            nodes.put(node.getName(), node);
        }
        this.memoryService = memoryService;
        this.ragService = ragService;
        log.info("AgentGraphEngine initialized with {} nodes: {}", nodes.size(), nodes.keySet());
    }

    /**
     * Execute the agent graph starting from the specified node.
     * This is the main entry point for processing user queries.
     *
     * @param startNodeName name of the starting agent node
     * @param context       the execution context with user input and state
     * @return the final agent response
     */
    public AgentResponse executeGraph(String startNodeName, AgentContext context) {
        long graphStartTime = System.currentTimeMillis();

        // 1. Load conversation history if conversationId is present
        enrichWithMemory(context);

        // 2. Retrieve RAG context
        enrichWithRAG(context);

        // 3. Execute the graph
        String currentNodeName = startNodeName;
        AgentResponse lastResponse = null;
        context.setCurrentState(GraphState.ROUTING);

        while (currentNodeName != null && nodes.containsKey(currentNodeName)) {
            // Safety: prevent infinite loops
            context.setIterationCount(context.getIterationCount() + 1);
            if (context.getIterationCount() > maxIterations) {
                log.warn("Max iterations ({}) reached. Terminating graph.", maxIterations);
                context.addTraceStep("GraphEngine", "MAX_ITERATIONS",
                        "Terminated after " + maxIterations + " iterations", 0);
                break;
            }

            // Timeout check
            long elapsed = System.currentTimeMillis() - graphStartTime;
            if (elapsed > executionTimeoutSeconds * 1000L) {
                log.warn("Execution timeout ({}s) reached. Terminating.", executionTimeoutSeconds);
                context.addTraceStep("GraphEngine", "TIMEOUT",
                        "Terminated after " + elapsed + "ms", elapsed);
                break;
            }

            log.info("Executing agent node: {} (iteration {})", currentNodeName, context.getIterationCount());
            context.setCurrentState(GraphState.PROCESSING);

            AgentNode node = nodes.get(currentNodeName);
            long nodeStartTime = System.currentTimeMillis();

            try {
                lastResponse = node.execute(context);
                long nodeDuration = System.currentTimeMillis() - nodeStartTime;

                lastResponse.setAgentName(currentNodeName);
                lastResponse.setExecutionTimeMs(nodeDuration);

                context.addTraceStep(currentNodeName, "EXECUTED",
                        lastResponse.getNextNode() != null ? "→ " + lastResponse.getNextNode() : "TERMINAL",
                        nodeDuration);

                if (lastResponse.isTerminal()) {
                    log.info("Reached terminal node: {} ({}ms)", currentNodeName, nodeDuration);
                    context.setCurrentState(GraphState.COMPLETE);
                    break;
                }

                currentNodeName = lastResponse.getNextNode();

                // Validate next node exists
                if (currentNodeName != null && !nodes.containsKey(currentNodeName)) {
                    log.warn("Next node '{}' not found. Using GeneralChatAgent as fallback.", currentNodeName);
                    currentNodeName = nodes.containsKey("GeneralChatAgent") ? "GeneralChatAgent" : null;
                }

            } catch (Exception e) {
                long nodeDuration = System.currentTimeMillis() - nodeStartTime;
                log.error("Error executing agent node: {}", currentNodeName, e);
                context.setCurrentState(GraphState.ERROR);
                context.addTraceStep(currentNodeName, "ERROR", e.getMessage(), nodeDuration);

                // Try fallback to GeneralChatAgent
                if (!"GeneralChatAgent".equals(currentNodeName) && nodes.containsKey("GeneralChatAgent")) {
                    log.info("Falling back to GeneralChatAgent after error in {}", currentNodeName);
                    currentNodeName = "GeneralChatAgent";
                    context.setCurrentState(GraphState.PROCESSING);
                    continue;
                }

                lastResponse = AgentResponse.builder()
                        .response("I'm sorry, I encountered an issue processing your request. Please try again.")
                        .terminal(true)
                        .agentName(currentNodeName)
                        .executionTimeMs(nodeDuration)
                        .build();
                break;
            }
        }

        // 4. Finalize response with execution metadata
        long totalTime = System.currentTimeMillis() - graphStartTime;
        if (lastResponse != null) {
            lastResponse.setExecutionTrace(context.getExecutionTrace());
            if (lastResponse.getRagDocumentsUsed() == null && !context.getRagDocuments().isEmpty()) {
                lastResponse.setRagDocumentsUsed(
                        context.getRagDocuments().stream()
                                .map(KnowledgeDocument::getId)
                                .toList()
                );
            }
        }

        // 5. Save conversation messages to memory
        saveToMemory(context, lastResponse);

        log.info("Graph execution complete in {}ms ({} iterations)", totalTime,
                context.getIterationCount());
        return lastResponse;
    }

    /**
     * Get the list of registered agent node names.
     */
    public Set<String> getRegisteredAgents() {
        return nodes.keySet();
    }

    // ========== Private Helpers ==========

    private void enrichWithMemory(AgentContext context) {
        if (context.getConversationId() == null || context.getConversationId().isBlank()) {
            return;
        }
        try {
            List<LLMMessage> history = memoryService.buildConversationContext(context.getConversationId());
            context.setConversationHistory(history);
            context.addTraceStep("GraphEngine", "MEMORY_LOADED",
                    "Loaded " + history.size() + " history messages", 0);
        } catch (Exception e) {
            log.warn("Failed to load conversation memory: {}", e.getMessage());
        }
    }

    private void enrichWithRAG(AgentContext context) {
        try {
            List<KnowledgeDocument> docs = ragService.retrieveRelevantDocuments(context.getUserQuery());
            if (!docs.isEmpty()) {
                context.setRagDocuments(docs);
                String augmented = ragService.buildAugmentedContext(context.getUserQuery(), docs);
                context.setAugmentedContext(augmented);
                context.addTraceStep("GraphEngine", "RAG_RETRIEVED",
                        "Retrieved " + docs.size() + " documents", 0);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve RAG documents: {}", e.getMessage());
        }
    }

    private void saveToMemory(AgentContext context, AgentResponse response) {
        if (context.getConversationId() == null || context.getConversationId().isBlank()) {
            return;
        }
        try {
            // Save user message
            memoryService.addMessage(context.getConversationId(), "USER",
                    context.getUserQuery(), null, null, 0, 0);

            // Save assistant response
            if (response != null && response.getResponse() != null) {
                memoryService.addMessage(context.getConversationId(), "ASSISTANT",
                        response.getResponse(), response.getAgentName(),
                        response.getProvider(), response.getTokensUsed(),
                        response.getExecutionTimeMs());
            }
        } catch (Exception e) {
            log.warn("Failed to save conversation to memory: {}", e.getMessage());
        }
    }
}
