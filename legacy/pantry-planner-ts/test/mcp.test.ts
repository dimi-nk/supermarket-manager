import assert from "node:assert/strict";
import { mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createRuntime } from "../src/config.js";
import { createServer } from "../src/mcp-server.js";

const FIXTURE = new URL("../fixtures/products.json", import.meta.url).pathname;

async function connect() {
  const dir = await mkdtemp(join(tmpdir(), "pantry-mcp-"));
  const rt = createRuntime({
    PANTRY_FILE: join(dir, "pantry.json"),
    PANTRY_PROVIDER: "fixture",
    PANTRY_FIXTURE: FIXTURE,
  });
  const server = createServer(rt);
  const [clientT, serverT] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: "test", version: "0" });
  await Promise.all([server.connect(serverT), client.connect(clientT)]);
  const call = async (name: string, args: Record<string, unknown> = {}) => {
    const res = await client.callTool({ name, arguments: args });
    const content = res.content as Array<{ type: string; text: string }>;
    return { text: content.map((c) => c.text).join("\n"), isError: !!res.isError };
  };
  return { client, call };
}

test("exposes the expected tools", async () => {
  const { client } = await connect();
  const { tools } = await client.listTools();
  assert.deepEqual(
    tools.map((t) => t.name).sort(),
    [
      "pantry_add_item",
      "pantry_add_stock",
      "pantry_apply_last_plan",
      "pantry_edit_rules",
      "pantry_list",
      "pantry_plan",
      "pantry_search_products",
      "pantry_set_expected",
      "pantry_set_item_state",
      "pantry_set_stock",
      "pantry_update_settings",
    ],
  );
  const planTool = tools.find((t) => t.name === "pantry_plan");
  assert.match(planTool?.description ?? "", /Does not place any orders/);
});

test("a full weekly flow through MCP tools", async () => {
  const { call } = await connect();

  const added = await call("pantry_add_item", {
    name: "Strained yoghurt",
    id: "yoghurt",
    unit: "kg",
    expected: 2,
    query: "γιαούρτι στραγγιστό",
    allow: ["ΔΕΛΤΑ", "ΟΛΥΜΠΟΣ", "ΦΑΓΕ"],
    deny: ["ΒΙΟ"],
    maxUnitPrice: 5,
    allowPrivateLabel: false,
  });
  assert.equal(added.isError, false);
  assert.match(added.text, /≤€5\.00\/kg/);

  await call("pantry_add_item", { name: "Eggs", id: "eggs", unit: "each", expected: 12, query: "αυγά" });
  const stock = await call("pantry_set_stock", { stock: { yoghurt: 0.5, eggs: 2 } });
  assert.match(stock.text, /yoghurt: have 0\.5 kg, need 1\.5/);
  assert.match(stock.text, /eggs: have 2 each, need 10/);

  await call("pantry_update_settings", { maxRetailers: 1, retailers: { lidl: { fee: 2.5 } } });
  const plan = await call("pantry_plan");
  assert.equal(plan.isError, false);
  // Lidl alone: yoghurt 4x1.90 = 7.60 + eggs 2.95 + 2.50 fee = 13.05; Sklavenitis: 7.90 + 3.10 = 11.00 wins.
  assert.match(plan.text, /Shopping plan: €11\.00 across 1 store/);
  assert.match(plan.text, /Sklavenitis/);

  const json = JSON.parse((await call("pantry_plan", { format: "json", itemIds: ["eggs"] })).text);
  assert.equal(json.items.length, 1);
  assert.equal(json.best.total, 3.1);

  // The last plan above covered only eggs; apply it, skipping nothing.
  const applied = await call("pantry_apply_last_plan");
  assert.match(applied.text, /eggs: \+10/);
  const again = await call("pantry_apply_last_plan");
  assert.equal(again.isError, true);
  assert.match(again.text, /already applied/);

  const listed = await call("pantry_list");
  assert.match(listed.text, /eggs: Eggs — have 12\/12 each, need 0/);
});

test("rule edits, state changes and search work through MCP", async () => {
  const { call } = await connect();
  await call("pantry_add_item", { name: "Butter", id: "butter", unit: "kg", expected: 0.5, query: "βούτυρο" });
  const edited = await call("pantry_edit_rules", { id: "butter", maxUnitPrice: 12, denyAdd: ["ΑΓΝΟΥ"] });
  assert.match(edited.text, /≤€12\.00\/kg/);
  assert.match(edited.text, /deny: ΑΓΝΟΥ/);
  const cleared = await call("pantry_edit_rules", { id: "butter", maxUnitPrice: null });
  assert.doesNotMatch(cleared.text, /≤€12/);

  const plan = await call("pantry_plan");
  assert.match(plan.text, /✗ Butter/);
  assert.match(plan.text, /1 denied/);

  assert.match((await call("pantry_set_item_state", { id: "butter", action: "pause" })).text, /pause done/);
  assert.match((await call("pantry_list")).text, /butter \[paused\]/);

  const search = await call("pantry_search_products", { query: "φέτα" });
  assert.match(search.text, /2 products found/);
  assert.match(search.text, /ΔΩΔΩΝΗ/);
});

test("tool errors come back as isError results, not crashes", async () => {
  const { call } = await connect();
  const res = await call("pantry_set_stock", { stock: { ghost: 1 } });
  assert.equal(res.isError, true);
  assert.match(res.text, /No pantry item "ghost"/);
});
