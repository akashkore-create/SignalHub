package com.khetisetu.event.agnexus.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a conversation session between a user and the AI agent system.
 * Stores session metadata, compressed summary for long conversations,
 * and links to all messages in the conversation_messages collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndex(name = "idx_user_session", def = "{'userId': 1, 'sessionId': 1}", unique = true)
public class Conversation {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String sessionId;

    /** LLM-generated summary of the conversation for context compression */
    private String summary;

    /** Total number of messages in this conversation */
    @Builder.Default
    private int messageCount = 0;

    /** Last agent that processed a query in this conversation */
    private String lastAgentUsed;

    /** Primary crop discussed in this conversation */
    private String primaryCrop;

    /** Primary location for this conversation */
    private String primaryLocation;

    /** Conversation status */
    @Builder.Default
    private ConversationStatus status = ConversationStatus.ACTIVE;

    /** User's preferred language */
    @Builder.Default
    private String language = "en";

    /** Additional metadata (e.g., device, source) */
    private Map<String, Object> metadata;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    public enum ConversationStatus {
        ACTIVE, ARCHIVED, DELETED
    }
}
