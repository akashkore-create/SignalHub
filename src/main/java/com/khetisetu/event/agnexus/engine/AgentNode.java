package com.khetisetu.event.agnexus.engine;

public interface AgentNode {
    AgentResponse execute(AgentContext context);

    String getName();
}
