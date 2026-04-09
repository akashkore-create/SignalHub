package com.khetisetu.event.agnexus.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents the response from an agent node execution.
 * Contains the generated response, routing information, and execution metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /** The generated text response */
    private String response;

    /** Next agent node to route to (null if terminal) */
    private String nextNode;

    /** Additional structured data from the agent */
    private Map<String, Object> data;

    /** Name of the agent that produced this response */
    private String agentName;

    /** Whether this is a terminal response (stops graph execution) */
    @Builder.Default
    private boolean terminal = false;

    /** Execution time for this agent in milliseconds */
    @Builder.Default
    private long executionTimeMs = 0;

    /** Tokens used by the LLM for this response */
    @Builder.Default
    private int tokensUsed = 0;

    /** LLM provider that served this response */
    private String provider;

    /** RAG document IDs referenced in this response */
    private List<String> ragDocumentsUsed;

    /** Confidence score (0.0-1.0) for the response quality */
    @Builder.Default
    private double confidenceScore = 1.0;

    /** Full execution trace for debugging */
    private List<AgentContext.ExecutionStep> executionTrace;
}
