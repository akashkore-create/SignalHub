package com.khetisetu.event.agnexus.dto;

import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Health check response for the AI agent system.
 * Used by the admin dashboard to monitor system status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHealthResponse {

    /** Overall system status */
    private String status;

    /** Status of each LLM provider */
    private Map<String, LLMProviderRouter.ProviderStatus> providerStatuses;

    /** Set of registered agent names */
    private Set<String> registeredAgents;

    /** Number of active conversations */
    private long activeConversations;

    /** Total documents in the knowledge base */
    private long knowledgeBaseSize;

    /** System uptime info */
    private String uptime;
}
