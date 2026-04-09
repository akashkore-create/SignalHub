package com.khetisetu.event.agnexus.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages rotation of multiple API keys per LLM provider.
 * Tracks usage using in-memory counters (with Redis fallback for distributed environments).
 * When a key hits a rate limit, it is marked as exhausted and the next key is used.
 */
@Service
@Slf4j
public class APIKeyRotationService {

    @Value("${llm.gemini.api.keys:}")
    private String geminiKeysConfig;

    @Value("${llm.groq.api.keys:}")
    private String groqKeysConfig;

    @Value("${llm.key.rate-limit-per-minute:15}")
    private int rateLimitPerMinute;

    private final RedisTemplate<String, String> redisTemplate;

    /** Provider → list of API keys */
    private Map<String, List<String>> providerKeys;

    /** Provider → current key index */
    private final Map<String, AtomicInteger> currentKeyIndex = new ConcurrentHashMap<>();

    /** Provider:KeyIndex → exhausted until timestamp */
    private final Map<String, Long> exhaustedKeys = new ConcurrentHashMap<>();

    public APIKeyRotationService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Initialize keys after Spring injects the properties.
     * Called lazily on first access or explicitly by config.
     */
    private void initializeIfNeeded() {
        if (providerKeys != null) return;

        providerKeys = new ConcurrentHashMap<>();

        List<String> geminiKeys = parseKeys(geminiKeysConfig);
        if (!geminiKeys.isEmpty()) {
            providerKeys.put("gemini", geminiKeys);
            currentKeyIndex.put("gemini", new AtomicInteger(0));
            log.info("Loaded {} Gemini API keys for rotation", geminiKeys.size());
        }

        List<String> groqKeys = parseKeys(groqKeysConfig);
        if (!groqKeys.isEmpty()) {
            providerKeys.put("groq", groqKeys);
            currentKeyIndex.put("groq", new AtomicInteger(0));
            log.info("Loaded {} Groq API keys for rotation", groqKeys.size());
        }
    }

    /**
     * Get the current API key for a provider. Returns null if none available.
     *
     * @param providerName provider identifier (e.g., "gemini", "grok")
     * @return current API key, or null if no keys configured / all exhausted
     */
    public String getCurrentKey(String providerName) {
        initializeIfNeeded();
        List<String> keys = providerKeys.get(providerName);
        if (keys == null || keys.isEmpty()) return null;

        AtomicInteger indexHolder = currentKeyIndex.get(providerName);
        int totalKeys = keys.size();

        // Try to find a non-exhausted key starting from current index
        for (int attempt = 0; attempt < totalKeys; attempt++) {
            int idx = indexHolder.get() % totalKeys;
            String exhaustionKey = providerName + ":" + idx;

            Long exhaustedUntil = exhaustedKeys.get(exhaustionKey);
            if (exhaustedUntil != null && System.currentTimeMillis() < exhaustedUntil) {
                // This key is still exhausted, try next
                indexHolder.compareAndSet(idx, (idx + 1) % totalKeys);
                continue;
            }

            // Key is available, remove exhaustion if expired
            if (exhaustedUntil != null) {
                exhaustedKeys.remove(exhaustionKey);
            }

            return keys.get(idx);
        }

        log.error("All {} API keys for provider '{}' are exhausted", totalKeys, providerName);
        return null;
    }

    /**
     * Mark the current key as rate-limited and rotate to the next one.
     *
     * @param providerName provider identifier
     */
    public void markCurrentKeyExhausted(String providerName) {
        initializeIfNeeded();
        AtomicInteger indexHolder = currentKeyIndex.get(providerName);
        if (indexHolder == null) return;

        List<String> keys = providerKeys.get(providerName);
        if (keys == null) return;

        int currentIdx = indexHolder.get() % keys.size();
        String exhaustionKey = providerName + ":" + currentIdx;

        // Exhaust this key for 60 seconds
        exhaustedKeys.put(exhaustionKey, System.currentTimeMillis() + 60_000);
        log.warn("API key {} for '{}' marked as exhausted for 60s. Rotating to next key.", currentIdx, providerName);

        // Move to next key
        indexHolder.set((currentIdx + 1) % keys.size());

        // Track in Redis for distributed environments
        try {
            String redisKey = "llm:key:exhausted:" + exhaustionKey;
            redisTemplate.opsForValue().set(redisKey, "1", 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Redis unavailable for key rotation tracking, using in-memory only");
        }
    }

    /**
     * Check if the provider has any available keys.
     *
     * @param providerName provider identifier
     * @return true if at least one key is available
     */
    public boolean hasAvailableKeys(String providerName) {
        return getCurrentKey(providerName) != null;
    }

    /**
     * Get the total number of configured keys for a provider.
     */
    public int getKeyCount(String providerName) {
        initializeIfNeeded();
        List<String> keys = providerKeys.get(providerName);
        return keys != null ? keys.size() : 0;
    }

    private List<String> parseKeys(String keysString) {
        if (keysString == null || keysString.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keysString.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toList());
    }
}
