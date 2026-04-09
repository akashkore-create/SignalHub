package com.khetisetu.event.agnexus.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an individual message within a conversation.
 * Stores the role, content, which agent handled it, and any tool calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_messages")
public class ConversationMessage {

    @Id
    private String id;

    @Indexed
    private String conversationId;

    /** Message role: USER, ASSISTANT, SYSTEM */
    private String role;

    /** The text content of the message */
    private String content;

    /** Name of the agent that produced this response (null for user messages) */
    private String agentName;

    /** Tool calls/results associated with this message */
    private Map<String, Object> toolCalls;

    /** RAG documents referenced in this response */
    private java.util.List<String> ragDocumentIds;

    /** LLM provider that generated this message */
    private String provider;

    /** Token usage for this message */
    @Builder.Default
    private int tokensUsed = 0;

    /** Execution latency for this message */
    @Builder.Default
    private long executionTimeMs = 0;

    /** Message timestamp — used for ordering */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
