import type { Option } from "./candidates.js";
import { roundMoney } from "./text.js";
import type { Settings } from "./types.js";

export interface ItemChoices {
  itemId: string;
  /** Cheapest acceptable option at each retailer that stocks something acceptable. */
  byRetailer: Map<string, Option>;
}

export interface PlanStore {
  retailer: string;
  retailerName: string;
  subtotal: number;
  fee: number;
  total: number;
  lines: Option[];
}

export interface Combination {
  /** Retailers actually used (those with at least one line). */
  retailers: string[];
  stores: PlanStore[];
  covered: string[];
  uncovered: string[];
  /** Sum of store totals, fees included. */
  total: number;
  /** Retailers whose subtotal is below their configured minimum basket. */
  minBasketViolations: string[];
}

export interface OptimizeResult {
  best: Combination | null;
  /** Best combination allowed to use at most k retailers, for k = 1..maxRetailers (deduplicated). */
  bySize: Array<{ maxRetailers: number; combination: Combination }>;
  /** True if retailers were pruned to keep the search small. */
  pruned: boolean;
}

const MAX_SEARCH_RETAILERS = 14;

export function optimize(choices: ItemChoices[], settings: Settings): OptimizeResult {
  const plannable = choices.filter((c) => c.byRetailer.size > 0);
  if (plannable.length === 0) return { best: null, bySize: [], pruned: false };

  let retailers = [...new Set(plannable.flatMap((c) => [...c.byRetailer.keys()]))].sort();
  let pruned = false;
  if (retailers.length > MAX_SEARCH_RETAILERS) {
    pruned = true;
    const score = (r: string) => {
      let coverage = 0;
      let cost = 0;
      for (const c of plannable) {
        const o = c.byRetailer.get(r);
        if (o) {
          coverage++;
          cost += o.lineCost;
        }
      }
      return { coverage, cost };
    };
    retailers = retailers
      .map((r) => ({ r, ...score(r) }))
      .sort((a, b) => b.coverage - a.coverage || a.cost - b.cost || a.r.localeCompare(b.r))
      .slice(0, MAX_SEARCH_RETAILERS)
      .map((x) => x.r);
  }

  const maxSize = Math.min(settings.maxRetailers, retailers.length);
  const bestAtMost: Array<Combination | null> = new Array(maxSize + 1).fill(null);

  for (let size = 1; size <= maxSize; size++) {
    for (const subset of combinations(retailers, size)) {
      const combo = evaluate(plannable, subset, settings, choices);
      for (let k = size; k <= maxSize; k++) {
        const cur = bestAtMost[k];
        if (!cur || compare(combo, cur) < 0) bestAtMost[k] = combo;
      }
    }
  }

  const best = bestAtMost[maxSize] ?? null;
  const bySize: OptimizeResult["bySize"] = [];
  for (let k = 1; k <= maxSize; k++) {
    const c = bestAtMost[k];
    if (!c) continue;
    const prev = bySize[bySize.length - 1];
    if (prev && sameCombo(prev.combination, c)) continue;
    bySize.push({ maxRetailers: k, combination: c });
  }
  return { best, bySize, pruned };
}

function evaluate(
  plannable: ItemChoices[],
  subset: string[],
  settings: Settings,
  allChoices: ItemChoices[],
): Combination {
  // Start from the cheapest store per item within the subset.
  const assignment = new Map<string, Option>();
  for (const choice of plannable) {
    let pick: Option | null = null;
    for (const r of subset) {
      const o = choice.byRetailer.get(r);
      if (!o) continue;
      if (!pick || o.lineCost < pick.lineCost - 0.004 ||
          (Math.abs(o.lineCost - pick.lineCost) <= 0.004 && o.unitPrice < pick.unitPrice)) {
        pick = o;
      }
    }
    if (pick) assignment.set(choice.itemId, pick);
  }

  let combo = build(assignment, settings, allChoices);

  // Free-delivery thresholds make the per-item-cheapest assignment suboptimal: sometimes it is
  // worth paying a little more for some items to lift a store's basket over its threshold.
  // Greedy local search: for each store below its threshold, pull in the items that cost least extra.
  const thresholdStores = subset.filter((r) => settings.retailers[r]?.freeAbove !== undefined);
  for (let pass = 0; pass < 3 && thresholdStores.length > 0; pass++) {
    let improved = false;
    for (const r of thresholdStores) {
      const target = settings.retailers[r]?.freeAbove ?? 0;
      const current = combo.stores.find((st) => st.retailer === r)?.subtotal ?? 0;
      if (current >= target) continue;
      const moves: Array<{ itemId: string; to: Option; extra: number }> = [];
      for (const choice of plannable) {
        const from = assignment.get(choice.itemId);
        const to = choice.byRetailer.get(r);
        if (!from || !to || from.retailer === r) continue;
        moves.push({ itemId: choice.itemId, to, extra: to.lineCost - from.lineCost });
      }
      moves.sort((a, b) => a.extra / Math.max(a.to.lineCost, 0.01) - b.extra / Math.max(b.to.lineCost, 0.01));
      const trial = new Map(assignment);
      let subtotal = current;
      for (const m of moves) {
        if (subtotal >= target) break;
        trial.set(m.itemId, m.to);
        subtotal += m.to.lineCost;
      }
      if (subtotal < target) continue;
      const candidate = build(trial, settings, allChoices);
      if (compare(candidate, combo) < 0) {
        for (const [k, v] of trial) assignment.set(k, v);
        combo = candidate;
        improved = true;
      }
    }
    if (!improved) break;
  }
  return combo;
}

function build(assignment: Map<string, Option>, settings: Settings, allChoices: ItemChoices[]): Combination {
  const linesByRetailer = new Map<string, Option[]>();
  const covered: string[] = [];
  for (const choice of allChoices) {
    const pick = assignment.get(choice.itemId);
    if (!pick) continue;
    covered.push(choice.itemId);
    const lines = linesByRetailer.get(pick.retailer) ?? [];
    lines.push(pick);
    linesByRetailer.set(pick.retailer, lines);
  }

  const stores: PlanStore[] = [];
  const violations: string[] = [];
  for (const [retailer, lines] of [...linesByRetailer.entries()].sort(([a], [b]) => a.localeCompare(b))) {
    const subtotal = roundMoney(lines.reduce((s, l) => s + l.lineCost, 0));
    const cfg = settings.retailers[retailer];
    let fee = cfg?.fee ?? 0;
    if (cfg?.freeAbove !== undefined && subtotal >= cfg.freeAbove) fee = 0;
    if (cfg?.minBasket !== undefined && subtotal < cfg.minBasket) violations.push(retailer);
    stores.push({
      retailer,
      retailerName: lines[0]?.retailerName ?? retailer,
      subtotal,
      fee: roundMoney(fee),
      total: roundMoney(subtotal + fee),
      lines,
    });
  }

  const coveredSet = new Set(covered);
  return {
    retailers: stores.map((s) => s.retailer),
    stores,
    covered,
    uncovered: allChoices.filter((c) => !coveredSet.has(c.itemId)).map((c) => c.itemId),
    total: roundMoney(stores.reduce((s, st) => s + st.total, 0)),
    minBasketViolations: violations,
  };
}

/** Negative if `a` is the better plan. Order: basket-minimum violations, uncovered items, cost, store count. */
function compare(a: Combination, b: Combination): number {
  return (
    a.minBasketViolations.length - b.minBasketViolations.length ||
    a.uncovered.length - b.uncovered.length ||
    Math.round(a.total * 100) - Math.round(b.total * 100) ||
    a.retailers.length - b.retailers.length ||
    a.retailers.join(",").localeCompare(b.retailers.join(","))
  );
}

function sameCombo(a: Combination, b: Combination): boolean {
  return a.retailers.join(",") === b.retailers.join(",") && a.total === b.total;
}

function* combinations<T>(items: T[], size: number, start = 0, prefix: T[] = []): Generator<T[]> {
  if (prefix.length === size) {
    yield [...prefix];
    return;
  }
  for (let i = start; i <= items.length - (size - prefix.length); i++) {
    prefix.push(items[i] as T);
    yield* combinations(items, size, i + 1, prefix);
    prefix.pop();
  }
}
