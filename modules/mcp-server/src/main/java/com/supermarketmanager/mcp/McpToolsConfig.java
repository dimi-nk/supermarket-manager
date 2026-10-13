package com.supermarketmanager.mcp;

import com.supermarketmanager.core.spi.AgentTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Collects the {@code @Tool} methods of every module's {@link AgentTools} bean and hands them to the
 * Spring AI MCP server (Streamable HTTP at /mcp, or stdio with --mcp-stdio). Modules that are left out of the
 * build simply contribute no tools.
 */
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider supermarketManagerTools(List<AgentTools> tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools.toArray()).build();
    }
}
