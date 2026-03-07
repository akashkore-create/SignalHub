package com.khetisetu.event.agnexus.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private String response;
    private String nextNode;
    private Map<String, Object> data;
    @Builder.Default
    private boolean terminal = false;
}
