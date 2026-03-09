package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.services.GeminiClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiagnosisAgent implements AgentNode {

    private final GeminiClientService gemini;

    @Override
    public String getName() {
        return "DiagnosisAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        String imageUrl = (String) context.getMetadata().get("imageUrl");
        String prompt = "You are a plant pathologist. Diagnose the issue for this crop: " + context.getCrop()
                + ". User description: " + context.getUserQuery();

        String analysis;
        if (imageUrl != null) {
            analysis = gemini.analyzeImage(imageUrl, prompt);
        } else {
            analysis = gemini.generateText(prompt);
        }

        return AgentResponse.builder()
                .response(analysis)
                .terminal(true) // Could transition to AdvisoryAgent or TreatmentTool
                .build();
    }
}
