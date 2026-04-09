package com.khetisetu.event.agnexus.memory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for conversation sessions.
 */
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByUserIdAndSessionId(String userId, String sessionId);

    List<Conversation> findByUserIdAndStatusOrderByUpdatedAtDesc(String userId, Conversation.ConversationStatus status);

    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId, Pageable pageable);

    long countByStatus(Conversation.ConversationStatus status);
}
