package com.khetisetu.event.agnexus.controllers;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentGraphEngine;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentGraphEngine engine;

    @PostMapping("/query")
    public AgentResponse query(@RequestBody AgentContext context) {
        return engine.executeGraph("RouterAgent", context);
    }
}
