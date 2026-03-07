package com.khetisetu.event.agnexus.engine;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.HashMap;

@Data
@Builder
public class AgentContext {
    private String userQuery;
    private String farmerId;
    private String location;
    private String crop;
    private String sessionId;
    @Builder.Default
    private Map<String, Object> agentMemory = new HashMap<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
