import { api } from "../api";
import { Page, useLoad } from "../components/ui";

export default function AgentsPage() {
  const info = useLoad(() => api.system.info());
  const url = info.data?.mcpUrl ?? "http://localhost:8765/mcp";
  const remote = JSON.stringify({ mcpServers: { "supermarket-manager": { command: "npx", args: ["-y", "mcp-remote", url] } } }, null, 2);
  const stdio = JSON.stringify({ mcpServers: { "supermarket-manager": { command: "java", args: ["-jar", "/path/to/supermarket-manager.jar", "--mcp-stdio"] } } }, null, 2);
  return (
    <Page title="Agents (MCP)" subtitle="Every module exposes tools over the Model Context Protocol, so an agent such as Claude can run your weekly shop.">
      <div className="card">
        <h3>While this app is running</h3>
        <p>MCP endpoint (Streamable HTTP): <code>{url}</code></p>
        <p className="small muted">Claude Code:</p>
        <pre>claude mcp add --transport http supermarket-manager {url}</pre>
        <p className="small muted">Clients that only launch local processes (e.g. Claude Desktop config file):</p>
        <pre>{remote}</pre>
      </div>
      <div className="card">
        <h3>Without the app running (stdio)</h3>
        <p className="small muted">The same tools over stdin/stdout, sharing this app's database. Point it at the downloaded jar (or the launcher inside the installed app with <code>--mcp-stdio</code>).</p>
        <pre>{stdio}</pre>
      </div>
      <div className="card">
        <h3>Tools</h3>
        <ul className="small">
          <li><strong>pantry_*</strong>: overview, delta, stock (set/adjust/record purchases), categories, products, profiles, targets over time</li>
          <li><strong>rules_*</strong>: list, add/remove rules, check a hand-made basket</li>
          <li><strong>prices_*</strong>: providers, search, optimizer settings, retailer fees</li>
          <li><strong>supermarkets_*</strong>: adapters and live store search</li>
          <li><strong>pipeline_*</strong>: run any chain of stages, list runs, apply a run after shopping</li>
        </ul>
        <p className="small muted">Tools never place orders or pay. Data folder: <code>{info.data?.dataDir}</code></p>
      </div>
    </Page>
  );
}
