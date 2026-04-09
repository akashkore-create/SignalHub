package com.khetisetu.event.agnexus.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MandiPriceTool implements Tool {

    @Value("${data.gov.in.api.key:}")
    private String apiKey;

    private static final String BASE_URL = "https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "MandiPriceTool";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        log.info("Executing MandiPriceTool with params: {}", params);

        if (apiKey == null || apiKey.isEmpty()) {
            return new ToolResult(null, false, "API Key (data.gov.in.api.key) is not configured.");
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("api-key", apiKey)
                    .queryParam("format", "json");

            // Map input params to API filters
            if (params.containsKey("state")) {
                builder.queryParam("filters[State]", params.get("state"));
            }
            if (params.containsKey("district")) {
                builder.queryParam("filters[District]", params.get("district"));
            }
            
            // Handle crop/commodity alias
            Object commodity = params.getOrDefault("commodity", params.get("crop"));
            if (commodity != null && !commodity.toString().isEmpty()) {
                builder.queryParam("filters[Commodity]", commodity.toString());
            }

            // Increase limit for better statistical overview (trends)
            int limit = params.containsKey("limit") ? (int) params.get("limit") : 50;
            builder.queryParam("limit", limit);
            
            // Sort by Arrival_Date desc to get latest data
            builder.queryParam("sort[Arrival_Date]", "desc");

            String url = builder.toUriString();
            log.debug("Mandi API URL: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            // Perform basic statistical analysis if data is present
            if (response != null && response.containsKey("records")) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) response.get("records");
                if (!records.isEmpty()) {
                    analyzePrices(response, records);
                }
            }

            return new ToolResult(response, true, "Success");

        } catch (Exception e) {
            log.error("Error fetching mandi prices", e);
            return new ToolResult(null, false, "Error: " + e.getMessage());
        }
    }

    /**
     * Calculates price statistics and adds them to the response map.
     */
    private void analyzePrices(Map<String, Object> response, List<Map<String, Object>> records) {
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        int count = 0;

        for (Map<String, Object> record : records) {
            try {
                double modal = Double.parseDouble(record.get("Modal_Price").toString());
                sum += modal;
                min = Math.min(min, modal);
                max = Math.max(max, modal);
                count++;
            } catch (Exception e) {
                // Skip invalid records
            }
        }

        if (count > 0) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("avg_modal_price", Math.round(sum / count));
            stats.put("min_modal_price", min);
            stats.put("max_modal_price", max);
            stats.put("record_count", count);
            stats.put("period_overview", "Latest " + count + " records analyzed");
            response.put("statistics", stats);
        }
    }
}
