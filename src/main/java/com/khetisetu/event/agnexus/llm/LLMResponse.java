package com.khetisetu.event.agnexus.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wraps the response from any LLM provider, including metadata
 * about token usage and which provider/model served the request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {

    /** The generated text content */
    private String content;

    /** Name of the provider that served this request (e.g., "gemini", "grok") */
    private String provider;

    /** Model identifier used (e.g., "gemini-2.0-flash", "grok-3-mini") */
    private String model;

    /** Approximate token count for the prompt */
    @Builder.Default
    private int promptTokens = 0;

    /** Approximate token count for the completion */
    @Builder.Default
    private int completionTokens = 0;

    /** Total latency in milliseconds */
    @Builder.Default
    private long latencyMs = 0;

    /** Whether this response came from a fallback provider */
    @Builder.Default
    private boolean fallback = false;

    /** Error message if the call partially failed */
    private String error;

    public boolean isSuccess() {
        return content != null && error == null;
    }
}
