package com.khetisetu.event.agnexus.llm;

import java.util.List;

/**
 * Abstraction over different LLM providers (Gemini, Grok, etc.).
 * Each provider implements this interface for text generation and
 * multi-turn conversation support.
 */
public interface LLMProvider {

    /**
     * @return unique provider name (e.g., "gemini", "grok")
     */
    String getName();

    /**
     * Generate text from a single prompt string.
     *
     * @param prompt the input prompt
     * @param apiKey the API key to use for this call
     * @return the LLM response
     */
    LLMResponse generate(String prompt, String apiKey);

    /**
     * Generate text from a multi-turn conversation (chat completion).
     *
     * @param messages ordered list of conversation messages
     * @param apiKey   the API key to use for this call
     * @return the LLM response
     */
    LLMResponse chat(List<LLMMessage> messages, String apiKey);

    /**
     * Check if this provider is currently available/healthy.
     *
     * @return true if the provider can accept requests
     */
    boolean isAvailable();
}
