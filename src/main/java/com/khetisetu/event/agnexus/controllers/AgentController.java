package com.khetisetu.event.agnexus.controllers;

import com.khetisetu.event.agnexus.dto.AgentHealthResponse;
import com.khetisetu.event.agnexus.dto.ChatRequest;
import com.khetisetu.event.agnexus.dto.ChatResponse;
import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentGraphEngine;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.memory.Conversation;
import com.khetisetu.event.agnexus.memory.ConversationMemoryService;
import com.khetisetu.event.agnexus.memory.ConversationMessage;
import com.khetisetu.event.agnexus.rag.KnowledgeDocument;
import com.khetisetu.event.agnexus.rag.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for the AI Agent system.
 * Provides chat, conversation management, knowledge base management,
 * and health check endpoints.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentGraphEngine engine;
    private final ConversationMemoryService memoryService;
    private final RAGService ragService;
    private final LLMProviderRouter llmRouter;

    // ==================== CHAT ENDPOINTS ====================

    /**
     * Main chat endpoint with session-based conversation support.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Chat request from user={}, session={}", request.getUserId(), request.getSessionId());

        // Generate session ID if not provided
        String sessionId = request.getSessionId() != null ? request.getSessionId() :
                "session-" + System.currentTimeMillis();

        // Get or create conversation
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        Conversation conversation = memoryService.getOrCreateConversation(userId, sessionId);

        // Build agent context
        AgentContext context = AgentContext.builder()
                .userQuery(request.getMessage())
                .farmerId(userId)
                .crop(request.getCrop())
                .location(request.getLocation())
                .sessionId(sessionId)
                .conversationId(conversation.getId())
                .language(request.getLanguage())
                .metadata(request.getImageUrl() != null ?
                        Map.of("imageUrl", request.getImageUrl()) : new HashMap<>())
                .build();

        // Execute agent graph
        AgentResponse agentResponse = engine.executeGraph("RouterAgent", context);

        long totalTime = System.currentTimeMillis() - startTime;

        ChatResponse response = ChatResponse.builder()
                .conversationId(conversation.getId())
                .sessionId(sessionId)
                .response(agentResponse != null ? agentResponse.getResponse() : "Sorry, I couldn't process your request.")
                .agentUsed(agentResponse != null ? agentResponse.getAgentName() : "unknown")
                .executionTrace(agentResponse != null ? agentResponse.getExecutionTrace() : List.of())
                .ragDocsUsed(agentResponse != null ? agentResponse.getRagDocumentsUsed() : List.of())
                .tokensUsed(agentResponse != null ? agentResponse.getTokensUsed() : 0)
                .executionTimeMs(totalTime)
                .provider(agentResponse != null ? agentResponse.getProvider() : null)
                .data(agentResponse != null ? agentResponse.getData() : null)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Legacy query endpoint (backward compatible with existing /api/agent/query).
     */
    @PostMapping("/query")
    public AgentResponse query(@RequestBody AgentContext context) {
        return engine.executeGraph("RouterAgent", context);
    }

    // ==================== CONVERSATION ENDPOINTS ====================

    /**
     * List conversations for a user.
     */
    @GetMapping("/conversations/{userId}")
    public ResponseEntity<List<Conversation>> getUserConversations(@PathVariable String userId) {
        return ResponseEntity.ok(memoryService.getUserConversations(userId));
    }

    /**
     * Get messages for a conversation.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ConversationMessage>> getConversationMessages(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(memoryService.getConversationMessages(conversationId));
    }

    /**
     * Archive (soft-delete) a conversation.
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> archiveConversation(@PathVariable String conversationId) {
        memoryService.archiveConversation(conversationId);
        return ResponseEntity.noContent().build();
    }

    // ==================== KNOWLEDGE BASE ENDPOINTS ====================

    /**
     * Ingest a knowledge document.
     */
    @PostMapping("/knowledge")
    public ResponseEntity<KnowledgeDocument> ingestKnowledge(@RequestBody KnowledgeDocument doc) {
        KnowledgeDocument saved = ragService.ingestDocument(
                doc.getTitle(), doc.getContent(), doc.getCategory(), doc.getTags());
        return ResponseEntity.ok(saved);
    }

    /**
     * Bulk ingest knowledge documents.
     */
    @PostMapping("/knowledge/bulk")
    public ResponseEntity<List<KnowledgeDocument>> bulkIngestKnowledge(
            @RequestBody List<KnowledgeDocument> docs) {
        return ResponseEntity.ok(ragService.bulkIngest(docs));
    }

    /**
     * Search knowledge base.
     */
    @GetMapping("/knowledge/search")
    public ResponseEntity<List<KnowledgeDocument>> searchKnowledge(
            @RequestParam String q,
            @RequestParam(required = false) KnowledgeDocument.KnowledgeCategory category) {
        return ResponseEntity.ok(ragService.searchKnowledge(q, category));
    }

    /**
     * Delete a knowledge document.
     */
    @DeleteMapping("/knowledge/{id}")
    public ResponseEntity<Void> deleteKnowledge(@PathVariable String id) {
        ragService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== HEALTH & MONITORING ====================

    /**
     * Agent system health check.
     */
    @GetMapping("/health")
    public ResponseEntity<AgentHealthResponse> health() {
        AgentHealthResponse health = AgentHealthResponse.builder()
                .status("UP")
                .providerStatuses(llmRouter.getProviderStatuses())
                .registeredAgents(engine.getRegisteredAgents())
                .activeConversations(memoryService.getActiveConversationCount())
                .knowledgeBaseSize(ragService.getKnowledgeBaseSize())
                .build();
        return ResponseEntity.ok(health);
    }
}
