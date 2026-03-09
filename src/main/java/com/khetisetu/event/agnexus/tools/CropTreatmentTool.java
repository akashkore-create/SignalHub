package com.khetisetu.event.agnexus.tools;

import com.khetisetu.event.agnexus.services.GeminiClientService;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CropTreatmentTool implements Tool {

    private final GeminiClientService gemini;

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
        String treatment = gemini.generateText(prompt);

        return new ToolResult(treatment, true, "Success");
    }
}
