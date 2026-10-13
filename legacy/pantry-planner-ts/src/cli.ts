#!/usr/bin/env node
import { runCli } from "./cli-core.js";
import { createRuntime } from "./config.js";

try {
  const output = await runCli(process.argv.slice(2), createRuntime());
  process.stdout.write(output + "\n");
} catch (err) {
  process.stderr.write(`Error: ${err instanceof Error ? err.message : String(err)}\n`);
  process.exitCode = 1;
}
