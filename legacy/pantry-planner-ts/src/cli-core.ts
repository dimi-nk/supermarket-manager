import { parseArgs } from "node:util";
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
  type RuleEdit,
} from "./store.js";
import { MeasureSchema, RetailerSettingSchema } from "./types.js";

export const HELP = `pantry — weekly shopping list with price rules (Greek supermarkets via PosoKanei)

Pantry
  pantry list
  pantry add "<name>" --unit kg|l|each --expected N [--actual N] [--id ID] [--query TEXT]
             [--max-unit-price N] [--max-pack-price N] [--allow A,B] [--deny X,Y] [--no-private-label]
  pantry stock id=qty ...        set what you have now (actual)
  pantry bought id=qty ...       add to what you have (after shopping)
  pantry expect id=qty ...       set the weekly target (expected)
  pantry rule <id> [--allow-add A,B] [--allow-remove A] [--deny-add X] [--deny-remove X]
             [--clear-allow] [--clear-deny] [--max-unit-price N|none] [--max-pack-price N|none]
             [--min-pack N|none] [--max-pack N|none] [--private-label true|false]
  pantry pause <id> | resume <id> | remove <id>
  pantry settings [--max-retailers N] [--exclude a,b] [--fee RETAILER=EUR] [--free-above RETAILER=EUR]
             [--min-basket RETAILER=EUR] [--price-age-days N] [--rounding-tolerance N] [--search-pages N]

Shopping
  pantry search "<text>" [--limit N]   find products/brands to build allow & deny lists
  pantry plan [--items a,b] [--json] [--no-save]
  pantry received [--skip a,b] [--force]   add the last plan's purchases to your stock

Environment: PANTRY_FILE (default ./pantry.json), PANTRY_PROVIDER=posokanei|fixture, PANTRY_FIXTURE,
PANTRY_CACHE_TTL_MIN (default 360; 0 disables the price cache).`;

const csv = (v: string | undefined): string[] =>
  v ? v.split(",").map((s) => s.trim()).filter(Boolean) : [];

function num(label: string, v: string): number {
  const n = Number(v.replace(",", "."));
  if (!Number.isFinite(n)) throw new Error(`${label} must be a number, got "${v}"`);
  return n;
}

function numOrNone(label: string, v: string): number | null {
  return v.toLowerCase() === "none" ? null : num(label, v);
}

function pairs(label: string, args: string[]): Record<string, number> {
  if (args.length === 0) throw new Error(`Give at least one id=quantity pair, e.g. milk=2`);
  const out: Record<string, number> = {};
  for (const a of args) {
    const i = a.indexOf("=");
    if (i <= 0) throw new Error(`Expected id=quantity, got "${a}"`);
    out[a.slice(0, i)] = num(`${label} for ${a.slice(0, i)}`, a.slice(i + 1));
  }
  return out;
}

function retailerPairs(label: string, values: string[] | undefined): Array<[string, number]> {
  return (values ?? []).map((v) => {
    const i = v.indexOf("=");
    if (i <= 0) throw new Error(`${label} expects RETAILER=EUR, got "${v}"`);
    return [v.slice(0, i).toLowerCase(), num(label, v.slice(i + 1))];
  });
}

/** Runs one CLI invocation and returns what should be printed. Throws on user errors. */
export async function runCli(argv: string[], rt: Runtime): Promise<string> {
  const [command, ...rest] = argv;
  switch (command) {
    case undefined:
    case "help":
    case "--help":
    case "-h":
      return HELP;

    case "list": {
      return formatPantry(await rt.store.load());
    }

    case "add": {
      const { values, positionals } = parseArgs({
        args: rest,
        allowPositionals: true,
        options: {
          unit: { type: "string" },
          expected: { type: "string" },
          actual: { type: "string" },
          id: { type: "string" },
          query: { type: "string" },
          "max-unit-price": { type: "string" },
          "max-pack-price": { type: "string" },
          allow: { type: "string" },
          deny: { type: "string" },
          "no-private-label": { type: "boolean" },
        },
      });
      const name = positionals.join(" ").trim();
      if (!name) throw new Error('Usage: pantry add "<name>" --unit kg|l|each --expected N');
      if (!values.unit || !values.expected) throw new Error("--unit and --expected are required");
      const unit = MeasureSchema.parse(values.unit);
      const item = await rt.store.update((p) =>
        addItem(p, {
          name,
          unit,
          expected: num("--expected", values.expected!),
          ...(values.actual ? { actual: num("--actual", values.actual) } : {}),
          ...(values.id ? { id: values.id } : {}),
          ...(values.query ? { query: values.query } : {}),
          rules: {
            allow: csv(values.allow),
            deny: csv(values.deny),
            allowPrivateLabel: !values["no-private-label"],
            ...(values["max-unit-price"] ? { maxUnitPrice: num("--max-unit-price", values["max-unit-price"]) } : {}),
            ...(values["max-pack-price"] ? { maxPackPrice: num("--max-pack-price", values["max-pack-price"]) } : {}),
          },
        }),
      );
      return `Added ${item.id}: ${item.name} (${item.expected} ${item.unit}/week)`;
    }

    case "stock": {
      const changed = await rt.store.update((p) => setActuals(p, pairs("quantity", rest)));
      return changed.map((i) => `${i.id}: have ${i.actual} ${i.unit}, need ${needOf(i)}`).join("\n");
    }

    case "bought": {
      const changed = await rt.store.update((p) => addToActuals(p, pairs("quantity", rest)));
      return changed.map((i) => `${i.id}: have ${i.actual} ${i.unit}, need ${needOf(i)}`).join("\n");
    }

    case "expect": {
      const targets = pairs("expected", rest);
      const changed = await rt.store.update((p) =>
        Object.entries(targets).map(([id, qty]) => {
          const item = findItem(p, id);
          if (qty < 0) throw new Error(`Invalid expected quantity for "${id}"`);
          item.expected = qty;
          return item;
        }),
      );
      return changed.map((i) => `${i.id}: expected ${i.expected} ${i.unit}, need ${needOf(i)}`).join("\n");
    }

    case "rule": {
      const { values, positionals } = parseArgs({
        args: rest,
        allowPositionals: true,
        options: {
          "allow-add": { type: "string" },
          "allow-remove": { type: "string" },
          "deny-add": { type: "string" },
          "deny-remove": { type: "string" },
          "clear-allow": { type: "boolean" },
          "clear-deny": { type: "boolean" },
          "max-unit-price": { type: "string" },
          "max-pack-price": { type: "string" },
          "min-pack": { type: "string" },
          "max-pack": { type: "string" },
          "private-label": { type: "string" },
        },
      });
      const id = positionals[0];
      if (!id) throw new Error("Usage: pantry rule <id> [options]");
      const edit: RuleEdit = {
        allowAdd: csv(values["allow-add"]),
        allowRemove: csv(values["allow-remove"]),
        denyAdd: csv(values["deny-add"]),
        denyRemove: csv(values["deny-remove"]),
        ...(values["clear-allow"] ? { clearAllow: true } : {}),
        ...(values["clear-deny"] ? { clearDeny: true } : {}),
        ...(values["max-unit-price"] !== undefined ? { maxUnitPrice: numOrNone("--max-unit-price", values["max-unit-price"]) } : {}),
        ...(values["max-pack-price"] !== undefined ? { maxPackPrice: numOrNone("--max-pack-price", values["max-pack-price"]) } : {}),
        ...(values["min-pack"] !== undefined ? { minPackSize: numOrNone("--min-pack", values["min-pack"]) } : {}),
        ...(values["max-pack"] !== undefined ? { maxPackSize: numOrNone("--max-pack", values["max-pack"]) } : {}),
        ...(values["private-label"] !== undefined ? { allowPrivateLabel: values["private-label"] === "true" } : {}),
      };
      const item = await rt.store.update((p) => editRules(p, id, edit));
      return formatItem(item);
    }

    case "pause":
    case "resume": {
      const id = rest[0];
      if (!id) throw new Error(`Usage: pantry ${command} <id>`);
      await rt.store.update((p) => {
        findItem(p, id).paused = command === "pause";
      });
      return `${id} ${command === "pause" ? "paused" : "resumed"}`;
    }

    case "remove": {
      const id = rest[0];
      if (!id) throw new Error("Usage: pantry remove <id>");
      await rt.store.update((p) => removeItem(p, id));
      return `Removed ${id}`;
    }

    case "settings": {
      const { values } = parseArgs({
        args: rest,
        options: {
          "max-retailers": { type: "string" },
          exclude: { type: "string" },
          fee: { type: "string", multiple: true },
          "free-above": { type: "string", multiple: true },
          "min-basket": { type: "string", multiple: true },
          "price-age-days": { type: "string" },
          "rounding-tolerance": { type: "string" },
          "search-pages": { type: "string" },
        },
      });
      const settings = await rt.store.update((p) => {
        const patch: Parameters<typeof updateSettings>[1] = {};
        if (values["max-retailers"]) patch.maxRetailers = num("--max-retailers", values["max-retailers"]);
        if (values.exclude !== undefined) patch.excludeRetailers = csv(values.exclude).map((r) => r.toLowerCase());
        if (values["price-age-days"]) patch.maxPriceAgeDays = num("--price-age-days", values["price-age-days"]);
        if (values["rounding-tolerance"]) patch.packRoundingTolerance = num("--rounding-tolerance", values["rounding-tolerance"]);
        if (values["search-pages"]) patch.searchPages = num("--search-pages", values["search-pages"]);
        const retailers = { ...p.settings.retailers };
        const touch = (r: string, field: "fee" | "freeAbove" | "minBasket", v: number) => {
          retailers[r] = RetailerSettingSchema.parse({ ...retailers[r], [field]: v });
        };
        for (const [r, v] of retailerPairs("--fee", values.fee)) touch(r, "fee", v);
        for (const [r, v] of retailerPairs("--free-above", values["free-above"])) touch(r, "freeAbove", v);
        for (const [r, v] of retailerPairs("--min-basket", values["min-basket"])) touch(r, "minBasket", v);
        patch.retailers = retailers;
        return updateSettings(p, patch);
      });
      return JSON.stringify(settings, null, 2);
    }

    case "search": {
      const { values, positionals } = parseArgs({
        args: rest,
        allowPositionals: true,
        options: { limit: { type: "string" } },
      });
      const query = positionals.join(" ").trim();
      if (!query) throw new Error('Usage: pantry search "<text>" [--limit N]');
      return formatSearch(await searchProducts(rt, query, values.limit ? { limit: num("--limit", values.limit) } : {}));
    }

    case "plan": {
      const { values } = parseArgs({
        args: rest,
        options: { items: { type: "string" }, json: { type: "boolean" }, "no-save": { type: "boolean" } },
      });
      const itemIds = csv(values.items);
      const plan = await runPlan(rt, { ...(itemIds.length ? { itemIds } : {}), save: !values["no-save"] });
      return values.json ? JSON.stringify(plan, null, 2) : formatPlan(plan);
    }

    case "received": {
      const { values } = parseArgs({
        args: rest,
        options: { skip: { type: "string" }, force: { type: "boolean" } },
      });
      const result = await applyLastPlan(rt, { skip: csv(values.skip), force: !!values.force });
      const lines = Object.entries(result.added).map(([id, q]) => `  ${id}: +${q}`);
      return `Added the plan from ${result.plannedAt} to your stock:\n${lines.join("\n") || "  (nothing)"}`;
    }

    default:
      throw new Error(`Unknown command "${command}". Run \`pantry help\`.`);
  }
}
