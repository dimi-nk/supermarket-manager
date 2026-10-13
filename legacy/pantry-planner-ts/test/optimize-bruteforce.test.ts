import assert from "node:assert/strict";
import { test } from "node:test";
import type { Option } from "../src/candidates.js";
import { optimize, type ItemChoices } from "../src/optimize.js";
import { SettingsSchema } from "../src/types.js";

/**
 * Compares optimize() with an exhaustive search over every item -> store assignment.
 * Guarantee: exact when there are no fees or only flat fees. With free-delivery thresholds the
 * optimizer uses a greedy improvement step, so we only require it to stay close (and never
 * claim a total below the true optimum).
 */

const RETAILERS = ["a", "b", "c", "d"];

function lcg(seed: number) {
  let s = seed;
  return () => (s = (s * 1664525 + 1013904223) % 4294967296) / 4294967296;
}

const opt = (itemId: string, retailer: string, lineCost: number): Option => ({
  itemId, retailer, retailerName: retailer, productId: itemId + retailer, productName: itemId,
  brand: null, barcode: null, packSize: 1, packs: 1, price: lineCost, unitPrice: lineCost,
  lineCost, overshoot: 0, isDiscount: false, lastUpdated: null, unitPriceMismatch: false,
});

type Cfg = Record<string, { fee?: number; freeAbove?: number }>;

function bruteForce(costs: Array<Record<string, number>>, maxStores: number, cfg: Cfg): number {
  const n = costs.length;
  const pick = new Array<number>(n).fill(0);
  let best = Infinity;
  const rec = (i: number): void => {
    if (i === n) {
      const used = new Map<string, number>();
      for (let j = 0; j < n; j++) {
        const r = RETAILERS[pick[j]!]!;
        used.set(r, (used.get(r) ?? 0) + costs[j]![r]!);
      }
      if (used.size > maxStores) return;
      let total = 0;
      for (const [r, sub] of used) {
        const c = cfg[r];
        let fee = c?.fee ?? 0;
        if (c?.freeAbove !== undefined && sub >= c.freeAbove) fee = 0;
        total += sub + fee;
      }
      best = Math.min(best, Math.round(total * 100) / 100);
      return;
    }
    for (let r = 0; r < RETAILERS.length; r++) {
      pick[i] = r;
      rec(i + 1);
    }
  };
  rec(0);
  return best;
}

function run(mode: "none" | "flat" | "threshold", trials: number, seed: number) {
  const rnd = lcg(seed);
  let suboptimal = 0;
  let worstGap = 0;
  for (let t = 0; t < trials; t++) {
    const n = 2 + Math.floor(rnd() * 4);
    const k = 1 + Math.floor(rnd() * 3);
    const costs = Array.from({ length: n }, () =>
      Object.fromEntries(RETAILERS.map((r) => [r, Math.round((1 + rnd() * 9) * 100) / 100])),
    );
    const cfg: Cfg = {};
    if (mode !== "none") {
      for (const r of RETAILERS) {
        cfg[r] = { fee: Math.round(rnd() * 4 * 100) / 100 };
        if (mode === "threshold" && rnd() < 0.6) cfg[r]!.freeAbove = Math.round(5 + rnd() * 15);
      }
    }
    const choices: ItemChoices[] = costs.map((c, i) => ({
      itemId: `i${i}`,
      byRetailer: new Map(RETAILERS.map((r) => [r, opt(`i${i}`, r, c[r]!)])),
    }));
    const got = optimize(choices, SettingsSchema.parse({ maxRetailers: k, retailers: cfg })).best!.total;
    const want = bruteForce(costs, k, cfg);
    assert.ok(got >= want - 0.005, `optimizer claimed ${got}, below the true optimum ${want}`);
    if (got > want + 0.005) {
      suboptimal++;
      worstGap = Math.max(worstGap, got - want);
    }
  }
  return { suboptimal, worstGap };
}

test("exact without fees", () => {
  assert.deepEqual(run("none", 1500, 1), { suboptimal: 0, worstGap: 0 });
});

test("exact with flat delivery fees", () => {
  assert.deepEqual(run("flat", 1500, 2), { suboptimal: 0, worstGap: 0 });
});

test("close to optimal with free-delivery thresholds", () => {
  const { suboptimal, worstGap } = run("threshold", 1500, 3);
  assert.ok(suboptimal <= 1500 * 0.02, `${suboptimal} of 1500 suboptimal`);
  assert.ok(worstGap <= 2, `worst gap €${worstGap.toFixed(2)}`);
});
