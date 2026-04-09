package com.khetisetu.event.agnexus.memory;

import com.khetisetu.event.agnexus.llm.LLMMessage;
import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import com.khetisetu.event.agnexus.utils.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core memory service for managing conversation state.
 * Uses a 3-layer architecture:
 * <ul>
 *   <li>L1 (Redis): Active session cache — last N messages, 30min TTL</li>
 *   <li>L2 (MongoDB): Full persistent conversation history</li>
 *   <li>L3 (Summary): LLM-generated compressed summary for long conversations</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final ConversationRepository conversationRepo;
    private final ConversationMessageRepository messageRepo;
    private final RedisTemplate<String, String> redisTemplate;
    private final LLMProviderRouter llmRouter;
    private final PromptLoader promptLoader;

    @Value("${memory.conversation.max-messages:50}")
    private int maxMessages;

    @Value("${memory.conversation.summary-threshold:20}")
    private int summaryThreshold;

    @Value("${memory.redis.session-ttl-minutes:30}")
    private int sessionTtlMinutes;

    private static final String REDIS_CONV_KEY = "conv:%s:messages";

    /**
     * Get or create a conversation for the given user and session.
     * Creates a new conversation if one doesn't exist.
     */
    public Conversation getOrCreateConversation(String userId, String sessionId) {
        return conversationRepo.findByUserIdAndSessionId(userId, sessionId)
                .orElseGet(() -> {
                    log.info("Creating new conversation for user={}, session={}", userId, sessionId);
                    Conversation conv = Conversation.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .build();
                    return conversationRepo.save(conv);
                });
    }

    /**
     * Add a message to the conversation and update caches.
     *
     * @param conversationId the conversation ID
     * @param role           message role (USER, ASSISTANT, SYSTEM)
     * @param content        message text
     * @param agentName      name of the agent (null for user messages)
     * @param provider       LLM provider used
     * @param tokensUsed     tokens consumed
     * @param executionTimeMs execution time
     * @return the saved message
     */
    public ConversationMessage addMessage(String conversationId, String role, String content,
                                           String agentName, String provider,
                                           int tokensUsed, long executionTimeMs) {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .agentName(agentName)
                .provider(provider)
                .tokensUsed(tokensUsed)
                .executionTimeMs(executionTimeMs)
                .timestamp(Instant.now())
                .build();

        message = messageRepo.save(message);

        // Update conversation metadata
        conversationRepo.findById(conversationId).ifPresent(conv -> {
            conv.setMessageCount(conv.getMessageCount() + 1);
            conv.setUpdatedAt(Instant.now());
            if (agentName != null) {
                conv.setLastAgentUsed(agentName);
            }
            conversationRepo.save(conv);
        });

        // Cache in Redis (L1)
        cacheMessageInRedis(conversationId, role, content);

        // Check if summary is needed
        long msgCount = messageRepo.countByConversationId(conversationId);
        if (msgCount > summaryThreshold && msgCount % summaryThreshold == 0) {
            summarizeConversationAsync(conversationId);
        }

        return message;
    }

    /**
     * Get recent messages for context building. Checks Redis L1 first, then MongoDB L2.
     *
     * @param conversationId conversation ID
     * @param limit          max number of recent messages
     * @return ordered list of recent messages (oldest first)
     */
    public List<ConversationMessage> getRecentMessages(String conversationId, int limit) {
        // Try Redis first for active sessions
        List<ConversationMessage> cached = getMessagesFromRedis(conversationId, limit);
        if (!cached.isEmpty()) {
            return cached;
        }

        // Fallback to MongoDB
        List<ConversationMessage> messages = messageRepo.findByConversationIdOrderByTimestampDesc(
                conversationId, PageRequest.of(0, limit));

        // Reverse to get chronological order (oldest first)
        Collections.reverse(messages);
        return messages;
    }

    /**
     * Build the full conversation context for an LLM call.
     * Includes summary (if available) + recent messages.
     *
     * @param conversationId conversation ID
     * @return list of LLMMessage objects ready for the LLM
     */
    public List<LLMMessage> buildConversationContext(String conversationId) {
        List<LLMMessage> context = new ArrayList<>();

        // Add conversation summary if available
        conversationRepo.findById(conversationId).ifPresent(conv -> {
            if (conv.getSummary() != null && !conv.getSummary().isBlank()) {
                context.add(LLMMessage.system("Previous conversation summary: " + conv.getSummary()));
            }
        });

        // Add recent messages
        List<ConversationMessage> recentMessages = getRecentMessages(conversationId, 10);
        for (ConversationMessage msg : recentMessages) {
            LLMMessage.Role role = switch (msg.getRole()) {
                case "USER" -> LLMMessage.Role.USER;
                case "ASSISTANT" -> LLMMessage.Role.ASSISTANT;
                case "SYSTEM" -> LLMMessage.Role.SYSTEM;
                default -> LLMMessage.Role.USER;
            };
            context.add(LLMMessage.builder().role(role).content(msg.getContent()).build());
        }

        return context;
    }

    /**
     * Get all conversations for a user.
     */
    public List<Conversation> getUserConversations(String userId) {
        return conversationRepo.findByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, Conversation.ConversationStatus.ACTIVE);
    }

    /**
     * Get full message history for a conversation.
     */
    public List<ConversationMessage> getConversationMessages(String conversationId) {
        return messageRepo.findByConversationIdOrderByTimestampAsc(conversationId);
    }

    /**
     * Archive a conversation (soft delete).
     */
    public void archiveConversation(String conversationId) {
        conversationRepo.findById(conversationId).ifPresent(conv -> {
            conv.setStatus(Conversation.ConversationStatus.ARCHIVED);
            conv.setUpdatedAt(Instant.now());
            conversationRepo.save(conv);
        });

        // Clear Redis cache
        try {
            redisTemplate.delete(String.format(REDIS_CONV_KEY, conversationId));
        } catch (Exception e) {
            log.debug("Redis unavailable for cache cleanup");
        }
    }

    /**
     * Get count of active conversations.
     */
    public long getActiveConversationCount() {
        return conversationRepo.countByStatus(Conversation.ConversationStatus.ACTIVE);
    }

    // ========== Private Helpers ==========

    private void cacheMessageInRedis(String conversationId, String role, String content) {
        try {
            String key = String.format(REDIS_CONV_KEY, conversationId);
            String value = role + ":::" + content;
            redisTemplate.opsForList().rightPush(key, value);

            // Trim to keep only last 10 messages
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > 10) {
                redisTemplate.opsForList().trim(key, size - 10, -1);
            }

            redisTemplate.expire(key, Duration.ofMinutes(sessionTtlMinutes));
        } catch (Exception e) {
            log.debug("Redis unavailable for message caching, using MongoDB fallback");
        }
    }

    private List<ConversationMessage> getMessagesFromRedis(String conversationId, int limit) {
        try {
            String key = String.format(REDIS_CONV_KEY, conversationId);
            List<String> cached = redisTemplate.opsForList().range(key, -limit, -1);
            if (cached == null || cached.isEmpty()) {
                return List.of();
            }

            return cached.stream()
                    .map(entry -> {
                        String[] parts = entry.split(":::", 2);
                        return ConversationMessage.builder()
                                .conversationId(conversationId)
                                .role(parts[0])
                                .content(parts.length > 1 ? parts[1] : "")
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Redis unavailable for message retrieval");
            return List.of();
        }
    }

    private void summarizeConversationAsync(String conversationId) {
        try {
            List<ConversationMessage> allMessages = messageRepo.findByConversationIdOrderByTimestampAsc(conversationId);
            if (allMessages.size() < summaryThreshold) return;

            // Build text from messages for summarization
            StringBuilder conversationText = new StringBuilder();
            for (ConversationMessage msg : allMessages) {
                conversationText.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }

            String summaryPrompt = promptLoader.loadPrompt("summary_prompt")
                    .replace("{{conversation}}", conversationText.toString());

            LLMResponse summaryResponse = llmRouter.generate(summaryPrompt);
            if (summaryResponse.isSuccess()) {
                conversationRepo.findById(conversationId).ifPresent(conv -> {
                    conv.setSummary(summaryResponse.getContent());
                    conv.setUpdatedAt(Instant.now());
                    conversationRepo.save(conv);
                    log.info("Generated conversation summary for {}", conversationId);
                });
            }
        } catch (Exception e) {
            log.error("Failed to summarize conversation {}", conversationId, e);
        }
    }
}
