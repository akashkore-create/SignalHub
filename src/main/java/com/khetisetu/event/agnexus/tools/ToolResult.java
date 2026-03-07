package com.khetisetu.event.agnexus.tools;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private Object result;
    private boolean success;
    private String message;
}
