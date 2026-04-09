package com.khetisetu.event.agnexus.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single message in a chat conversation with an LLM.
 * Supports SYSTEM, USER, and ASSISTANT roles for multi-turn dialogue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMMessage {

    public enum Role {
        SYSTEM, USER, ASSISTANT
    }

    private Role role;
    private String content;

    /** Optional: image data URI for multimodal models */
    private String imageUrl;

    public static LLMMessage system(String content) {
        return LLMMessage.builder().role(Role.SYSTEM).content(content).build();
    }

    public static LLMMessage user(String content) {
        return LLMMessage.builder().role(Role.USER).content(content).build();
    }

    public static LLMMessage user(String content, String imageUrl) {
        return LLMMessage.builder().role(Role.USER).content(content).imageUrl(imageUrl).build();
    }

    public static LLMMessage assistant(String content) {
        return LLMMessage.builder().role(Role.ASSISTANT).content(content).build();
    }
}
