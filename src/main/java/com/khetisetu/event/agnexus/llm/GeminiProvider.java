package com.khetisetu.event.agnexus.llm;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Google Gemini LLM provider implementation.
 * Uses the Gemini REST API with support for multi-turn conversations,
 * system instructions, and image analysis.
 */
@Component
@Slf4j
public class GeminiProvider implements LLMProvider {

    @Value("${llm.gemini.model:gemini-2.0-flash}")
    private String model;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final AtomicBoolean available = new AtomicBoolean(true);

    @Override
    public String getName() {
        return "gemini";
    }

    @Override
    public LLMResponse generate(String prompt, String apiKey) {
        return chat(List.of(LLMMessage.user(prompt)), apiKey);
    }

    @Override
    public LLMResponse chat(List<LLMMessage> messages, String apiKey) {
        long startTime = System.currentTimeMillis();
        try {
            JSONObject requestBody = buildGeminiRequest(messages);
            String url = BASE_URL + model + ":generateContent?key=" + apiKey;

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - startTime;

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    int code = response.code();

                    if (code == 429 || code == 503) {
                        log.warn("Gemini rate limit or service unavailable ({}): {}", code, errorBody);
                        throw new RateLimitException("Gemini rate limited: " + code);
                    }

                    log.error("Gemini API error ({}): {}", code, errorBody);
                    return LLMResponse.builder()
                            .provider(getName())
                            .model(model)
                            .error("Gemini API error: " + code)
                            .latencyMs(latency)
                            .build();
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                String text = extractText(jsonResponse);
                int promptTokens = extractTokenCount(jsonResponse, "promptTokenCount");
                int completionTokens = extractTokenCount(jsonResponse, "candidatesTokenCount");

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

        } catch (RateLimitException e) {
            throw e; // Re-throw for key rotation handling
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Gemini call failed", e);
            return LLMResponse.builder()
                    .provider(getName())
                    .model(model)
                    .error("Gemini call failed: " + e.getMessage())
                    .latencyMs(latency)
                    .build();
        }
    }

    @Override
    public boolean isAvailable() {
        return available.get();
    }

    private JSONObject buildGeminiRequest(List<LLMMessage> messages) {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject systemInstruction = null;

        for (LLMMessage msg : messages) {
            if (msg.getRole() == LLMMessage.Role.SYSTEM) {
                // Gemini uses systemInstruction for system prompts
                systemInstruction = new JSONObject();
                JSONArray systemParts = new JSONArray();
                systemParts.put(new JSONObject().put("text", msg.getContent()));
                systemInstruction.put("parts", systemParts);
                continue;
            }

            JSONObject content = new JSONObject();
            content.put("role", msg.getRole() == LLMMessage.Role.USER ? "user" : "model");

            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", msg.getContent()));

            // Handle image if present
            if (msg.getImageUrl() != null && !msg.getImageUrl().isEmpty()) {
                if (msg.getImageUrl().startsWith("data:")) {
                    // Handle base64 inline data
                    String[] dataParts = msg.getImageUrl().split(",", 2);
                    if (dataParts.length == 2) {
                        String mimeType = dataParts[0].replace("data:", "").replace(";base64", "");
                        JSONObject inlineData = new JSONObject();
                        inlineData.put("mimeType", mimeType);
                        inlineData.put("data", dataParts[1]);
                        parts.put(new JSONObject().put("inlineData", inlineData));
                    }
                }
            }

            content.put("parts", parts);
            contents.put(content);
        }

        requestBody.put("contents", contents);

        if (systemInstruction != null) {
            requestBody.put("systemInstruction", systemInstruction);
        }

        // Generation config
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 4096);
        generationConfig.put("topP", 0.95);
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private String extractText(JSONObject response) {
        try {
            return response.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
        } catch (Exception e) {
            log.warn("Failed to extract text from Gemini response", e);
            return "";
        }
    }

    private int extractTokenCount(JSONObject response, String field) {
        try {
            return response.getJSONObject("usageMetadata").getInt(field);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Custom exception for rate limit detection, used by key rotation logic.
     */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
