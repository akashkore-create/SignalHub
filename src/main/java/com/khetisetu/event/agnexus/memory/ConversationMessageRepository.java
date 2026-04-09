package com.khetisetu.event.agnexus.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * MongoDB repository for conversation messages.
 */
public interface ConversationMessageRepository extends MongoRepository<ConversationMessage, String> {

    List<ConversationMessage> findByConversationIdOrderByTimestampAsc(String conversationId);

    List<ConversationMessage> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);

    long countByConversationId(String conversationId);

    void deleteByConversationId(String conversationId);
}
