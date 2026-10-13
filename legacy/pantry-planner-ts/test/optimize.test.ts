import assert from "node:assert/strict";
import { test } from "node:test";
import type { Option } from "../src/candidates.js";
import { optimize, type ItemChoices } from "../src/optimize.js";
import { SettingsSchema, type Settings } from "../src/types.js";

function opt(itemId: string, retailer: string, lineCost: number): Option {
  return {
    itemId,
    retailer,
    retailerName: retailer.toUpperCase(),
    productId: `${itemId}-${retailer}`,
    productName: `${itemId} @ ${retailer}`,
    brand: null,
    barcode: null,
    packSize: 1,
    packs: 1,
    price: lineCost,
    unitPrice: lineCost,
    lineCost,
    overshoot: 0,
    isDiscount: false,
    lastUpdated: null,
    unitPriceMismatch: false,
  };
}

function choice(itemId: string, prices: Record<string, number>): ItemChoices {
  return {
    itemId,
    byRetailer: new Map(Object.entries(prices).map(([r, p]) => [r, opt(itemId, r, p)])),
  };
}

const settings = (over: Partial<Settings> = {}): Settings =>
  SettingsSchema.parse({ maxRetailers: 2, ...over });

// X is cheap for A and B, Y is cheap for C, Z is cheap for nothing.
const choices = [
  choice("A", { x: 1, y: 3, z: 4 }),
  choice("B", { x: 2, y: 4, z: 5 }),
  choice("C", { x: 9, y: 3, z: 6 }),
];

// Same as above without Z, for fee / minimum-basket scenarios where Z would otherwise be an escape hatch.
const xyChoices = [
  choice("A", { x: 1, y: 3 }),
  choice("B", { x: 2, y: 4 }),
  choice("C", { x: 9, y: 3 }),
];

test("splits across two stores when that is cheapest", () => {
  const { best, bySize } = optimize(choices, settings({ maxRetailers: 2 }));
  assert.ok(best);
  assert.deepEqual(best.retailers, ["x", "y"]);
  assert.equal(best.total, 6); // A=1, B=2 from x; C=3 from y
  assert.deepEqual(bySize.map((s) => [s.maxRetailers, s.combination.total]), [
    [1, 10], // y alone: 3+4+3
    [2, 6],
  ]);
});

test("respects a one-store limit", () => {
  const { best } = optimize(choices, settings({ maxRetailers: 1 }));
  assert.ok(best);
  assert.deepEqual(best.retailers, ["y"]);
  assert.equal(best.total, 10);
});

test("a delivery fee can make the single store win", () => {
  const { best } = optimize(
    xyChoices,
    settings({ maxRetailers: 2, retailers: { y: { fee: 5 } } }),
  );
  assert.ok(best);
  // x+y = 6 + 5 fee = 11; x alone = 1+2+9 = 12; y alone = 10 + 5 = 15. Split still wins at 11.
  assert.equal(best.total, 11);
  const withBigFee = optimize(xyChoices, settings({ maxRetailers: 2, retailers: { y: { fee: 7 } } }));
  // x+y = 13, x alone = 12 -> single store x wins
  assert.deepEqual(withBigFee.best?.retailers, ["x"]);
  assert.equal(withBigFee.best?.total, 12);
});

test("freeAbove waives the fee", () => {
  const { best } = optimize(
    [choice("A", { x: 20 })],
    settings({ maxRetailers: 1, retailers: { x: { fee: 4, freeAbove: 20 } } }),
  );
  assert.equal(best?.total, 20);
  assert.equal(best?.stores[0]?.fee, 0);
  const below = optimize(
    [choice("A", { x: 19.99 })],
    settings({ maxRetailers: 1, retailers: { x: { fee: 4, freeAbove: 20 } } }),
  );
  assert.equal(below.best?.total, 23.99);
});

test("a store below its minimum basket is avoided when an alternative exists", () => {
  const { best } = optimize(
    xyChoices,
    settings({ maxRetailers: 2, retailers: { y: { fee: 0, minBasket: 11 } } }),
  );
  assert.ok(best);
  // y alone costs 10 (< min 11) and x+y gives y only C=3: both violate. Best feasible: x alone = 12.
  assert.deepEqual(best.retailers, ["x"]);
  assert.equal(best.total, 12);
  assert.deepEqual(best.minBasketViolations, []);
});

test("minimum basket violation is reported when unavoidable", () => {
  const { best } = optimize(
    [choice("A", { x: 3 })],
    settings({ maxRetailers: 1, retailers: { x: { minBasket: 10 } } }),
  );
  assert.deepEqual(best?.minBasketViolations, ["x"]);
});

test("covering more items beats being cheaper", () => {
  const { best } = optimize(
    [choice("A", { x: 1, y: 5 }), choice("B", { y: 5 })],
    settings({ maxRetailers: 1 }),
  );
  assert.deepEqual(best?.retailers, ["y"]);
  assert.deepEqual(best?.uncovered, []);
});

test("ties prefer fewer stores", () => {
  const { best } = optimize(
    [choice("A", { x: 1, y: 1 }), choice("B", { x: 1, y: 1 })],
    settings({ maxRetailers: 2 }),
  );
  assert.equal(best?.retailers.length, 1);
});

test("items nobody can supply are ignored by the search", () => {
  const empty: ItemChoices = { itemId: "Q", byRetailer: new Map() };
  const { best } = optimize([choice("A", { x: 2 }), empty], settings());
  assert.deepEqual(best?.covered, ["A"]);
});

test("a fee-free third store can beat paying a fee at the second", () => {
  const { best } = optimize(choices, settings({ maxRetailers: 2, retailers: { y: { fee: 5 } } }));
  // x+z = 1+2+6 = 9 beats x+y = 6+5 = 11.
  assert.deepEqual(best?.retailers, ["x", "z"]);
  assert.equal(best?.total, 9);
});

test("returns null when nothing is plannable", () => {
  assert.equal(optimize([], settings()).best, null);
});
