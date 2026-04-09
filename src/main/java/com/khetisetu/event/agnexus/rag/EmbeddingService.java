package com.khetisetu.event.agnexus.rag;

import com.khetisetu.event.agnexus.llm.APIKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generates text embeddings using Google's text-embedding-004 model (768 dimensions).
 * Used for creating vector representations of knowledge documents and queries
 * for similarity search in the RAG pipeline.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private final APIKeyRotationService keyRotation;

    @Value("${rag.embedding.model:text-embedding-004}")
    private String embeddingModel;

    @Value("${rag.embedding.dimensions:768}")
    private int dimensions;

    private static final String EMBEDDING_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Generate embeddings for a text string.
     *
     * @param text the input text to embed
     * @return list of doubles representing the embedding vector, or empty if failed
     */
    public List<Double> generateEmbedding(String text) {
        String apiKey = keyRotation.getCurrentKey("gemini");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No Gemini API key available for embedding generation");
            return List.of();
        }

        try {
            JSONObject requestBody = new JSONObject();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", text));
            content.put("parts", parts);
            requestBody.put("content", content);

            String url = String.format(EMBEDDING_API_URL, embeddingModel, apiKey);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown";
                    log.error("Embedding API error ({}): {}", response.code(), errorBody);
                    return List.of();
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                JSONArray values = jsonResponse
                        .getJSONObject("embedding")
                        .getJSONArray("values");

                List<Double> embedding = new ArrayList<>(values.length());
                for (int i = 0; i < values.length(); i++) {
                    embedding.add(values.getDouble(i));
                }

                log.debug("Generated embedding with {} dimensions", embedding.size());
                return embedding;
            }

        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return List.of();
        }
    }

    /**
     * @return configured embedding dimension count
     */
    public int getDimensions() {
        return dimensions;
    }
}
