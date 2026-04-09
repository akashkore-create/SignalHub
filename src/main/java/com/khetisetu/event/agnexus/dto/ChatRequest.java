package com.khetisetu.event.agnexus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for the main chat endpoint.
 * Supports session-based conversations with optional context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** User identifier */
    private String userId;

    /** Session ID for conversation continuity (auto-generated if null) */
    private String sessionId;

    /** The user's message */
    private String message;

    /** Optional image URL or base64 data URI for image analysis */
    private String imageUrl;

    /** Crop being discussed */
    private String crop;

    /** User's location (e.g., "Pune, Maharashtra") */
    private String location;

    /** Preferred language (e.g., "en", "mr", "hi") */
    @Builder.Default
    private String language = "en";

    /** Force a specific LLM provider for testing (e.g., "gemini", "grok") */
    private String forceProvider;
}
