package com.khetisetu.event.agnexus.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptLoader {

    public String loadPrompt(String promptName) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + promptName + ".txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + promptName, e);
        }
    }
}
