package com.khetisetu.event.agnexus.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Routes LLM requests to the best available provider with automatic
 * failover and key rotation. Primary → Gemini, Fallback → Groq.
 *
 * <p>When the primary provider hits a rate limit, it rotates keys.
 * If all keys are exhausted, it falls back to the secondary provider.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LLMProviderRouter {

    private final List<LLMProvider> providers;
    private final APIKeyRotationService keyRotation;

    @Value("${llm.primary.provider:gemini}")
    private String primaryProviderName;

    /**
     * Generate a simple text response using the best available provider.
     *
     * @param prompt the input prompt
     * @return LLM response with provider metadata
     */
    public LLMResponse generate(String prompt) {
        return executeWithFailover(provider -> {
            String key = keyRotation.getCurrentKey(provider.getName());
            return provider.generate(prompt, key);
        });
    }

    /**
     * Generate a chat response using multi-turn conversation history.
     *
     * @param messages ordered conversation messages
     * @return LLM response with provider metadata
     */
    public LLMResponse chat(List<LLMMessage> messages) {
        return executeWithFailover(provider -> {
            String key = keyRotation.getCurrentKey(provider.getName());
            return provider.chat(messages, key);
        });
    }

    /**
     * Generate using a specific provider by name (for testing/admin control).
     *
     * @param providerName explicit provider to use
     * @param messages     conversation messages
     * @return LLM response
     */
    public LLMResponse chatWithProvider(String providerName, List<LLMMessage> messages) {
        LLMProvider provider = findProvider(providerName);
        if (provider == null) {
            return LLMResponse.builder()
                    .error("Provider not found: " + providerName)
                    .build();
        }

        String key = keyRotation.getCurrentKey(providerName);
        if (key == null) {
            return LLMResponse.builder()
                    .provider(providerName)
                    .error("No API keys available for: " + providerName)
                    .build();
        }

        try {
            return provider.chat(messages, key);
        } catch (GeminiProvider.RateLimitException e) {
            keyRotation.markCurrentKeyExhausted(providerName);
            // Retry with rotated key
            String newKey = keyRotation.getCurrentKey(providerName);
            if (newKey != null) {
                return provider.chat(messages, newKey);
            }
            return LLMResponse.builder()
                    .provider(providerName)
                    .error("All keys exhausted for: " + providerName)
                    .build();
        }
    }

    /**
     * Get the status of all configured providers.
     */
    public Map<String, ProviderStatus> getProviderStatuses() {
        return providers.stream().collect(Collectors.toMap(
                LLMProvider::getName,
                p -> new ProviderStatus(
                        p.getName(),
                        p.isAvailable(),
                        keyRotation.hasAvailableKeys(p.getName()),
                        keyRotation.getKeyCount(p.getName())
                )
        ));
    }

    private LLMResponse executeWithFailover(ProviderCall call) {
        // 1. Try primary provider
        LLMProvider primary = findProvider(primaryProviderName);
        if (primary != null && primary.isAvailable() && keyRotation.hasAvailableKeys(primary.getName())) {
            try {
                LLMResponse response = call.execute(primary);
                if (response.isSuccess()) {
                    return response;
                }
                log.warn("Primary provider '{}' returned error: {}", primaryProviderName, response.getError());
            } catch (GeminiProvider.RateLimitException e) {
                log.warn("Primary provider '{}' rate limited, rotating key", primaryProviderName);
                keyRotation.markCurrentKeyExhausted(primaryProviderName);

                // Retry with new key
                if (keyRotation.hasAvailableKeys(primaryProviderName)) {
                    try {
                        LLMResponse retryResponse = call.execute(primary);
                        if (retryResponse.isSuccess()) {
                            return retryResponse;
                        }
                    } catch (GeminiProvider.RateLimitException e2) {
                        keyRotation.markCurrentKeyExhausted(primaryProviderName);
                        log.warn("Primary provider '{}' rate limited again after rotation", primaryProviderName);
                    }
                }
            }
        }

        // 2. Fallback to other providers
        for (LLMProvider fallbackProvider : providers) {
            if (fallbackProvider.getName().equals(primaryProviderName)) continue;
            if (!fallbackProvider.isAvailable() || !keyRotation.hasAvailableKeys(fallbackProvider.getName())) continue;

            log.info("Falling back to provider: {}", fallbackProvider.getName());
            try {
                LLMResponse response = call.execute(fallbackProvider);
                if (response.isSuccess()) {
                    response.setFallback(true);
                    return response;
                }
            } catch (GeminiProvider.RateLimitException e) {
                keyRotation.markCurrentKeyExhausted(fallbackProvider.getName());
                log.warn("Fallback provider '{}' also rate limited", fallbackProvider.getName());
            }
        }

        // 3. All providers failed
        return LLMResponse.builder()
                .error("All LLM providers are unavailable or rate limited")
                .build();
    }

    private LLMProvider findProvider(String name) {
        return providers.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @FunctionalInterface
    private interface ProviderCall {
        LLMResponse execute(LLMProvider provider);
    }

    /**
     * Status record for monitoring.
     */
    public record ProviderStatus(String name, boolean available, boolean hasKeys, int totalKeys) {}
}
