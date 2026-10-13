package com.supermarketmanager.core.spi;

/**
 * Marker for beans whose {@code @Tool} methods should be exposed to agents over MCP.
 * Each module ships its own tools; the mcp-server module collects every AgentTools bean.
 */
public interface AgentTools {
}
