package com.khetisetu.event.agnexus.llm;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Groq LLM provider implementation.
 * Uses the OpenAI-compatible API at api.groq.com/openai/v1 for text generation.
 * Supports multi-turn conversations with system/user/assistant roles.
 */
@Component
@Slf4j
public class GroqProvider implements LLMProvider {

    @Value("${llm.groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${llm.groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final AtomicBoolean available = new AtomicBoolean(true);

    @Override
    public String getName() {
        return "groq";
    }

    @Override
    public LLMResponse generate(String prompt, String apiKey) {
        return chat(List.of(LLMMessage.user(prompt)), apiKey);
    }

    @Override
    public LLMResponse chat(List<LLMMessage> messages, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return LLMResponse.builder()
                    .provider(getName())
                    .model(model)
                    .error("Groq API key not configured")
                    .build();
        }

        long startTime = System.currentTimeMillis();
        try {
            JSONObject requestBody = buildRequest(messages);
            String url = baseUrl + "/chat/completions";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - startTime;

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    int code = response.code();

                    if (code == 429 || code == 503 || code == 529) {
                        log.warn("Groq rate limit or service unavailable ({}): {}", code, errorBody);
                        throw new GeminiProvider.RateLimitException("Groq rate limited: " + code);
                    }

                    log.error("Groq API error ({}): {}", code, errorBody);
                    return LLMResponse.builder()
                            .provider(getName())
                            .model(model)
                            .error("Groq API error: " + code)
                            .latencyMs(latency)
                            .build();
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                String text = extractText(jsonResponse);
                int promptTokens = extractUsage(jsonResponse, "prompt_tokens");
                int completionTokens = extractUsage(jsonResponse, "completion_tokens");

                available.set(true);

                return LLMResponse.builder()
                        .content(text)
                        .provider(getName())
                        .model(model)
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .latencyMs(latency)
                        .build();
            }

        } catch (GeminiProvider.RateLimitException e) {
            throw e;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Groq call failed", e);
            return LLMResponse.builder()
                    .provider(getName())
                    .model(model)
                    .error("Groq call failed: " + e.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }

    @Override
    public boolean isAvailable() {
        return available.get();
    }

    private JSONObject buildRequest(List<LLMMessage> messages) {
        JSONObject request = new JSONObject();
        request.put("model", model);

        JSONArray messagesArray = new JSONArray();
        for (LLMMessage msg : messages) {
            JSONObject messageObj = new JSONObject();
            String role = switch (msg.getRole()) {
                case SYSTEM -> "system";
                case USER -> "user";
                case ASSISTANT -> "assistant";
            };
            messageObj.put("role", role);
            messageObj.put("content", msg.getContent());
            messagesArray.put(messageObj);
        }

        request.put("messages", messagesArray);
        request.put("temperature", 0.7);
        request.put("max_tokens", 4096);

        return request;
    }

    private String extractText(JSONObject response) {
        try {
            return response.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            log.warn("Failed to extract text from Groq response", e);
            return "";
        }
    }

    private int extractUsage(JSONObject response, String field) {
        try {
            return response.getJSONObject("usage").getInt(field);
        } catch (Exception e) {
            return 0;
        }
    }
}
