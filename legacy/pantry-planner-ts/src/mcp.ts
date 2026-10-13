#!/usr/bin/env node
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { createRuntime } from "./config.js";
import { createServer } from "./mcp-server.js";

const server = createServer(createRuntime());
await server.connect(new StdioServerTransport());
