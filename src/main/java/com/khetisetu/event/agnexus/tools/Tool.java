package com.khetisetu.event.agnexus.tools;

import java.util.Map;

public interface Tool {
    ToolResult execute(Map<String, Object> params);

    String getName();
}
