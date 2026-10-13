import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { Runtime } from "./config.js";
import { formatItem, formatPantry, formatPlan } from "./format.js";
import { applyLastPlan, formatSearch, runPlan, searchProducts } from "./service.js";
import {
  addItem,
  addToActuals,
  editRules,
  findItem,
  needOf,
  removeItem,
  setActuals,
  updateSettings,
} from "./store.js";
import { MeasureSchema, RetailerSettingSchema } from "./types.js";

type ToolResult = { content: Array<{ type: "text"; text: string }>; isError?: boolean };

const text = (t: string): ToolResult => ({ content: [{ type: "text", text: t }] });

async function guard(fn: () => Promise<string>): Promise<ToolResult> {
  try {
    return text(await fn());
  } catch (err) {
    return { content: [{ type: "text", text: err instanceof Error ? err.message : String(err) }], isError: true };
  }
}

const quantities = z.record(z.string(), z.number().min(0));

export function createServer(rt: Runtime): McpServer {
  const server = new McpServer({ name: "pantry-planner", version: "0.1.0" });

  server.registerTool(
    "pantry_list",
    {
      title: "Show pantry",
      description:
        "Show every pantry item with expected vs actual quantity, how much is needed, and its allow/deny/price rules.",
      annotations: { readOnlyHint: true },
    },
    () => guard(async () => formatPantry(await rt.store.load())),
  );

  server.registerTool(
    "pantry_add_item",
    {
      title: "Add pantry item",
      description:
        "Add a recurring item. `unit` is kg, l or each; expected/actual and price caps use that unit. `query` is the search text (Greek works best, e.g. 'γιαούρτι στραγγιστό'). " +
        "allow/deny entries match brand or product name (accent/case-insensitive); deny always wins.",
      inputSchema: {
        name: z.string().min(1),
        unit: MeasureSchema,
        expected: z.number().min(0).describe("Target stock at the start of a week"),
        actual: z.number().min(0).optional().describe("Current stock (default 0)"),
        id: z.string().optional(),
        query: z.string().optional(),
        allow: z.array(z.string()).optional(),
        deny: z.array(z.string()).optional(),
        maxUnitPrice: z.number().positive().optional().describe("EUR per kg / l / piece"),
        maxPackPrice: z.number().positive().optional(),
        allowPrivateLabel: z.boolean().optional(),
      },
    },
    (a) =>
      guard(async () => {
        const item = await rt.store.update((p) =>
          addItem(p, {
            name: a.name,
            unit: a.unit,
            expected: a.expected,
            ...(a.actual !== undefined ? { actual: a.actual } : {}),
            ...(a.id ? { id: a.id } : {}),
            ...(a.query ? { query: a.query } : {}),
            rules: {
              allow: a.allow ?? [],
              deny: a.deny ?? [],
              allowPrivateLabel: a.allowPrivateLabel ?? true,
              ...(a.maxUnitPrice !== undefined ? { maxUnitPrice: a.maxUnitPrice } : {}),
              ...(a.maxPackPrice !== undefined ? { maxPackPrice: a.maxPackPrice } : {}),
            },
          }),
        );
        return `Added.\n${formatItem(item)}`;
      }),
  );

  server.registerTool(
    "pantry_set_stock",
    {
      title: "Set current stock",
      description:
        "Record what is actually in the house now (replaces `actual`) for one or more items, e.g. {\"milk\": 1, \"eggs\": 4}. Use this for the weekly top-up check.",
      inputSchema: { stock: quantities },
    },
    ({ stock }) =>
      guard(async () => {
        const changed = await rt.store.update((p) => setActuals(p, stock));
        return changed.map((i) => `${i.id}: have ${i.actual} ${i.unit}, need ${needOf(i)}`).join("\n");
      }),
  );

  server.registerTool(
    "pantry_add_stock",
    {
      title: "Add purchased quantities",
      description: "Add quantities to current stock after shopping (adds to `actual`).",
      inputSchema: { added: quantities },
    },
    ({ added }) =>
      guard(async () => {
        const changed = await rt.store.update((p) => addToActuals(p, added));
        return changed.map((i) => `${i.id}: have ${i.actual} ${i.unit}, need ${needOf(i)}`).join("\n");
      }),
  );

  server.registerTool(
    "pantry_set_expected",
    {
      title: "Set weekly targets",
      description: "Change how much of each item you want in the house at the start of a week.",
      inputSchema: { expected: quantities },
    },
    ({ expected }) =>
      guard(async () => {
        const changed = await rt.store.update((p) =>
          Object.entries(expected).map(([id, qty]) => {
            const item = findItem(p, id);
            item.expected = qty;
            return item;
          }),
        );
        return changed.map((i) => `${i.id}: expected ${i.expected} ${i.unit}, need ${needOf(i)}`).join("\n");
      }),
  );

  server.registerTool(
    "pantry_edit_rules",
    {
      title: "Edit item rules",
      description:
        "Change an item's allow list, deny list and price caps. Add/remove entries incrementally. Pass null for a cap to clear it. " +
        "Entries match brand or name; prefix with 'gtin:' or 'id:' for exact barcode/product id. Use pantry_search_products first to see real brands.",
      inputSchema: {
        id: z.string(),
        allowAdd: z.array(z.string()).optional(),
        allowRemove: z.array(z.string()).optional(),
        denyAdd: z.array(z.string()).optional(),
        denyRemove: z.array(z.string()).optional(),
        clearAllow: z.boolean().optional(),
        clearDeny: z.boolean().optional(),
        maxUnitPrice: z.number().positive().nullable().optional(),
        maxPackPrice: z.number().positive().nullable().optional(),
        minPackSize: z.number().positive().nullable().optional(),
        maxPackSize: z.number().positive().nullable().optional(),
        allowPrivateLabel: z.boolean().optional(),
      },
    },
    ({ id, ...edit }) =>
      guard(async () => {
        const clean = Object.fromEntries(Object.entries(edit).filter(([, v]) => v !== undefined));
        const item = await rt.store.update((p) => editRules(p, id, clean));
        return formatItem(item);
      }),
  );

  server.registerTool(
    "pantry_set_item_state",
    {
      title: "Pause, resume or remove an item",
      description: "Pause an item to skip it in plans without deleting it, resume it, or remove it permanently.",
      inputSchema: { id: z.string(), action: z.enum(["pause", "resume", "remove"]) },
      annotations: { destructiveHint: true },
    },
    ({ id, action }) =>
      guard(async () => {
        await rt.store.update((p) => {
          if (action === "remove") removeItem(p, id);
          else findItem(p, id).paused = action === "pause";
        });
        return `${id}: ${action} done`;
      }),
  );

  server.registerTool(
    "pantry_update_settings",
    {
      title: "Update planner settings",
      description:
        "Set the maximum number of stores to split a shop across, excluded retailers, per-retailer delivery fee / free-delivery threshold / minimum basket (keyed by retailer id as shown in plans and searches), and other planner tuning.",
      inputSchema: {
        maxRetailers: z.number().int().min(1).max(10).optional(),
        excludeRetailers: z.array(z.string()).optional(),
        retailers: z.record(z.string(), RetailerSettingSchema.partial()).optional(),
        maxPriceAgeDays: z.number().positive().optional(),
        packRoundingTolerance: z.number().min(0).max(0.5).optional(),
        searchPages: z.number().int().min(1).max(5).optional(),
      },
    },
    (a) =>
      guard(async () => {
        const settings = await rt.store.update((p) => {
          const retailers = { ...p.settings.retailers };
          for (const [r, cfg] of Object.entries(a.retailers ?? {})) {
            retailers[r.toLowerCase()] = RetailerSettingSchema.parse({ ...retailers[r.toLowerCase()], ...cfg });
          }
          const patch: Parameters<typeof updateSettings>[1] = { retailers };
          if (a.maxRetailers !== undefined) patch.maxRetailers = a.maxRetailers;
          if (a.excludeRetailers) patch.excludeRetailers = a.excludeRetailers.map((r) => r.toLowerCase());
          if (a.maxPriceAgeDays !== undefined) patch.maxPriceAgeDays = a.maxPriceAgeDays;
          if (a.packRoundingTolerance !== undefined) patch.packRoundingTolerance = a.packRoundingTolerance;
          if (a.searchPages !== undefined) patch.searchPages = a.searchPages;
          return updateSettings(p, patch);
        });
        return JSON.stringify(settings, null, 2);
      }),
  );

  server.registerTool(
    "pantry_search_products",
    {
      title: "Search supermarket products",
      description:
        "Search the price database (all retailers). Shows products with pack size and cheapest unit price, plus a brand summary — use it to choose allow/deny entries and realistic price caps.",
      inputSchema: { query: z.string().min(1), limit: z.number().int().min(1).max(50).optional() },
      annotations: { readOnlyHint: true, openWorldHint: true },
    },
    ({ query, limit }) =>
      guard(async () => formatSearch(await searchProducts(rt, query, limit ? { limit } : {}))),
  );

  server.registerTool(
    "pantry_plan",
    {
      title: "Plan this week's shop",
      description:
        "Compute what to buy: for every item that is below its expected quantity, find acceptable products (allow/deny/price caps/pack size), then choose the cheapest combination of stores (up to the configured maximum, including fees). " +
        "Read-only with respect to the pantry; the plan is saved so pantry_apply_last_plan can add it to stock after shopping. Does not place any orders.",
      inputSchema: {
        itemIds: z.array(z.string()).optional().describe("Limit to these items"),
        format: z.enum(["text", "json"]).optional().describe("Default text"),
      },
      annotations: { readOnlyHint: false, openWorldHint: true },
    },
    ({ itemIds, format }) =>
      guard(async () => {
        const plan = await runPlan(rt, itemIds?.length ? { itemIds } : {});
        return format === "json" ? JSON.stringify(plan, null, 2) : formatPlan(plan);
      }),
  );

  server.registerTool(
    "pantry_apply_last_plan",
    {
      title: "Add last plan to stock",
      description:
        "After shopping, add the quantities from the most recent plan to current stock. Pass `skip` for items you did not buy. Refuses to apply the same plan twice unless `force` is set.",
      inputSchema: { skip: z.array(z.string()).optional(), force: z.boolean().optional() },
    },
    ({ skip, force }) =>
      guard(async () => {
        const r = await applyLastPlan(rt, { skip: skip ?? [], force: !!force });
        const lines = Object.entries(r.added).map(([id, q]) => `${id}: +${q}`);
        return `Applied plan from ${r.plannedAt}:\n${lines.join("\n") || "(nothing)"}`;
      }),
  );

  return server;
}
