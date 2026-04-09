package com.khetisetu.event.agnexus.tools;

import com.khetisetu.event.agnexus.llm.LLMProviderRouter;
import com.khetisetu.event.agnexus.llm.LLMResponse;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Tool for generating specific crop treatment recommendations.
 * Uses LLM to provide organic and chemical treatment options
 * for identified crop diseases.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CropTreatmentTool implements Tool {

    private final LLMProviderRouter llmRouter;

    @Override
    public String getName() {
        return "CropTreatmentTool";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        log.info("Executing CropTreatmentTool with params: {}", params);
        String crop = (String) params.get("crop");
        String disease = (String) params.get("disease");

        if (crop == null || disease == null) {
            return new ToolResult(null, false, "Crop and disease are required for CropTreatmentTool.");
        }

        String prompt = String.format(
                "You are an expert agronomist. Provide a specific treatment plan for %s disease in %s crops. Include organic and chemical options if applicable.",
                disease, crop);

        LLMResponse response = llmRouter.generate(prompt);
        String treatment = response.isSuccess() ? response.getContent() : "Treatment info unavailable.";

        return new ToolResult(treatment, true, "Success");
    }
}
