package com.planb.global.config.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    // Tool 추가 필요
    @Bean
    public ToolCallbackProvider toolCallbackProvider(){

        return MethodToolCallbackProvider
                .builder()
                .toolObjects()
                .build();
    }
}
