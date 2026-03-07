package com.khetisetu.event.agnexus.agents;

import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentNode;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.services.GeminiClientService;
import com.khetisetu.event.agnexus.utils.PromptLoader;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RouterAgent implements AgentNode {

    private final GeminiClientService geminiClient;
    private final PromptLoader promptLoader;

    @Override
    public String getName() {
        return "RouterAgent";
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        String prompt = promptLoader.loadPrompt("router_prompt")
                .replace("{{userQuery}}", context.getUserQuery());

        String decision = geminiClient.generateText(prompt).trim();

        return AgentResponse.builder()
                .response("Routing query to " + decision)
                .nextNode(decision)
                .terminal(false)
                .build();
    }
}
