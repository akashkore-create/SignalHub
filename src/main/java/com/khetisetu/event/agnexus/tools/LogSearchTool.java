package com.khetisetu.event.agnexus.tools;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class LogSearchTool implements Tool {

    private static final String LOG_FILE_PATH = "event.log";

    @Override
    public String getName() {
        return "LogSearchTool";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        log.info("Executing LogSearchTool with params: {}", params);
        String keyword = (String) params.get("keyword");
        int lines = (int) params.getOrDefault("lines", 50);

        try (BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE_PATH))) {
            List<String> foundLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (keyword == null || line.contains(keyword)) {
                    foundLines.add(line);
                }
                // Keep only the last 'lines' lines
                if (foundLines.size() > lines) {
                    foundLines.remove(0);
                }
            }
            return new ToolResult(foundLines, true, "Read " + foundLines.size() + " lines");
        } catch (Exception e) {
            log.error("Error reading log file", e);
            return new ToolResult(null, false, "Error reading logs: " + e.getMessage());
        }
    }
}
