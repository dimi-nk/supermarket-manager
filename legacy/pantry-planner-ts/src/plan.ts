import { bestPerRetailer, buildOptions, type Diagnostics, type Option } from "./candidates.js";
import { optimize, type Combination, type ItemChoices, type OptimizeResult } from "./optimize.js";
import { needOf } from "./store.js";
import type { Pantry, PriceProvider } from "./types.js";

export type ItemStatus = "planned" | "stocked" | "paused" | "unfulfilled" | "left_out";

export interface ItemReport {
  id: string;
  name: string;
  unit: string;
  expected: number;
  actual: number;
  need: number;
  status: ItemStatus;
  /** Human-readable explanation for anything that is not "planned". */
  reason?: string;
  diagnostics?: Diagnostics;
}

export interface Plan {
  generatedAt: string;
  provider: string;
  maxRetailers: number;
  best: Combination | null;
  /** Cheapest plan allowed to use at most k stores, for each k that gives a different answer. */
  bySize: OptimizeResult["bySize"];
  items: ItemReport[];
  warnings: string[];
}

export interface PlanOptions {
  /** Restrict the plan to these item ids. */
  itemIds?: string[];
  now?: Date;
}

export async function buildPlan(
  pantry: Pantry,
  provider: PriceProvider,
  opts: PlanOptions = {},
): Promise<Plan> {
  const now = opts.now ?? new Date();
  const warnings: string[] = [];
  const reports: ItemReport[] = [];
  const choices: ItemChoices[] = [];
  const optionsByItem = new Map<string, Option[]>();
  let searchFailures = 0;
  let searches = 0;

  const wanted = opts.itemIds ? new Set(opts.itemIds) : null;
  if (wanted) {
    for (const id of wanted) {
      if (!pantry.items.some((i) => i.id === id)) throw new Error(`No pantry item "${id}"`);
    }
  }

  for (const item of pantry.items) {
    if (wanted && !wanted.has(item.id)) continue;
    const base = {
      id: item.id,
      name: item.name,
      unit: item.unit,
      expected: item.expected,
      actual: item.actual,
      need: needOf(item),
    };
    if (item.paused) {
      reports.push({ ...base, status: "paused", reason: "paused" });
      continue;
    }
    if (base.need <= 1e-9) {
      reports.push({ ...base, status: "stocked", reason: "already at or above the expected amount" });
      continue;
    }

    searches++;
    let products;
    try {
      products = await provider.search(item.query ?? item.name, { pages: pantry.settings.searchPages });
    } catch (err) {
      searchFailures++;
      const message = err instanceof Error ? err.message : String(err);
      reports.push({ ...base, status: "unfulfilled", reason: `price lookup failed: ${message}` });
      continue;
    }

    const { options, diagnostics } = buildOptions(item, base.need, products, pantry.settings);
    optionsByItem.set(item.id, options);
    const byRetailer = bestPerRetailer(options);
    if (byRetailer.size === 0) {
      reports.push({
        ...base,
        status: "unfulfilled",
        reason: explainNoMatch(item.unit, item.rules.maxUnitPrice, item.rules.maxPackPrice, diagnostics),
        diagnostics,
      });
      continue;
    }
    choices.push({ itemId: item.id, byRetailer });
    reports.push({ ...base, status: "planned", diagnostics });
  }

  if (searches > 0 && searchFailures === searches) {
    warnings.push("Every price lookup failed — check network access to the price provider.");
  }

  const result = optimize(choices, pantry.settings);
  if (result.pruned) warnings.push("Many retailers matched; the search was limited to the 14 with the best coverage.");

  const best = result.best;
  const placed = new Set(best?.covered ?? []);
  for (const r of reports) {
    if (r.status === "planned" && !placed.has(r.id)) {
      r.status = "left_out";
      r.reason = `no store within the ${pantry.settings.maxRetailers}-store limit carries an acceptable product`;
    }
  }

  if (best) {
    for (const v of best.minBasketViolations) {
      warnings.push(`${v} is below its minimum basket; this plan cannot be ordered there as-is.`);
    }
    const staleMs = pantry.settings.maxPriceAgeDays * 86_400_000;
    for (const store of best.stores) {
      for (const line of store.lines) {
        if (line.lastUpdated) {
          const age = now.getTime() - Date.parse(line.lastUpdated);
          if (Number.isFinite(age) && age > staleMs) {
            warnings.push(
              `Price for "${line.productName}" at ${store.retailerName} is ${Math.floor(age / 86_400_000)} days old.`,
            );
          }
        }
        if (line.unitPriceMismatch) {
          warnings.push(
            `Unit price for "${line.productName}" at ${store.retailerName} disagrees with the provider's figure — check the pack size.`,
          );
        }
      }
    }
  }

  return {
    generatedAt: now.toISOString(),
    provider: provider.name,
    maxRetailers: pantry.settings.maxRetailers,
    best,
    bySize: result.bySize,
    items: reports,
    warnings,
  };
}

function explainNoMatch(
  unit: string,
  maxUnit: number | undefined,
  maxPack: number | undefined,
  d: Diagnostics,
): string {
  if (d.searched === 0) return "search returned no products — try a different query";
  const parts: string[] = [`${d.searched} products found`];
  const p = d.rejectedProducts;
  if (p.denied) parts.push(`${p.denied} denied`);
  if (p.notAllowed) parts.push(`${p.notAllowed} not on the allow list`);
  if (p.privateLabel) parts.push(`${p.privateLabel} private-label`);
  if (p.wrongMeasure) parts.push(`${p.wrongMeasure} sold in a different unit than ${unit}`);
  if (p.unknownPack) parts.push(`${p.unknownPack} with unreadable pack size`);
  if (p.packSize) parts.push(`${p.packSize} outside pack-size limits`);
  const o = d.rejectedOffers;
  if (o.overUnitCap || o.overPackCap) {
    const cap = o.overUnitCap && maxUnit !== undefined ? `over €${maxUnit}/${unit}` : `over the pack-price cap${maxPack !== undefined ? ` €${maxPack}` : ""}`;
    parts.push(`${o.overUnitCap + o.overPackCap} offers ${cap}`);
  }
  if (o.excludedRetailer) parts.push(`${o.excludedRetailer} at excluded retailers`);
  let text = parts.join("; ");
  if (d.cheapestOverCap) {
    text += `. Closest: ${d.cheapestOverCap.productName} at ${d.cheapestOverCap.retailerName}, €${d.cheapestOverCap.unitPrice}/${unit}`;
  }
  return text;
}

/** Quantities (in each item's unit) the plan would put into the house, keyed by item id. */
export function purchasesFromPlan(plan: Plan): Record<string, number> {
  const out: Record<string, number> = {};
  for (const store of plan.best?.stores ?? []) {
    for (const line of store.lines) {
      out[line.itemId] = Math.round(((out[line.itemId] ?? 0) + line.packs * line.packSize) * 1e6) / 1e6;
    }
  }
  return out;
}
