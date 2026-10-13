import assert from "node:assert/strict";
import { test } from "node:test";
import { formatPlan } from "../src/format.js";
import { buildPlan, purchasesFromPlan } from "../src/plan.js";
import { FixtureProvider } from "../src/providers/fixture.js";
import { PantrySchema, type Pantry, type PriceProvider, type Product } from "../src/types.js";

const FIXTURE = new URL("../fixtures/products.json", import.meta.url).pathname;
const NOW = new Date("2026-10-13T10:00:00Z");

function pantry(settings: Record<string, unknown> = {}): Pantry {
  return PantrySchema.parse({
    version: 1,
    settings: { maxRetailers: 2, ...settings },
    items: [
      {
        id: "yoghurt",
        name: "Strained yoghurt",
        query: "γιαούρτι στραγγιστό",
        unit: "kg",
        expected: 2,
        actual: 0.5,
        rules: { allow: ["ΔΕΛΤΑ", "ΟΛΥΜΠΟΣ", "ΦΑΓΕ"], deny: ["ΒΙΟ"], maxUnitPrice: 5, allowPrivateLabel: false },
      },
      { id: "milk", name: "Milk", query: "γάλα φρέσκο", unit: "l", expected: 3, actual: 1 },
      { id: "eggs", name: "Eggs", query: "αυγά", unit: "each", expected: 12, actual: 2 },
      { id: "butter", name: "Butter", query: "βούτυρο", unit: "kg", expected: 0.5, actual: 0, rules: { maxUnitPrice: 12 } },
      { id: "pasta", name: "Spaghetti", query: "μακαρόνια", unit: "kg", expected: 1, actual: 0 },
      { id: "feta", name: "Feta", query: "φέτα", unit: "kg", expected: 0.4, actual: 0 },
      { id: "oil", name: "Olive oil", query: "ελαιόλαδο", unit: "l", expected: 2, actual: 2 },
      { id: "tea", name: "Tea", unit: "each", expected: 1, actual: 0, paused: true },
    ],
  });
}

const status = (plan: Awaited<ReturnType<typeof buildPlan>>, id: string) =>
  plan.items.find((i) => i.id === id)?.status;

test("end to end: rules, caps, rounding and a 2-store split", async () => {
  const plan = await buildPlan(pantry(), new FixtureProvider(FIXTURE), { now: NOW });

  assert.equal(status(plan, "yoghurt"), "planned");
  assert.equal(status(plan, "milk"), "planned");
  assert.equal(status(plan, "eggs"), "planned");
  assert.equal(status(plan, "pasta"), "planned");
  assert.equal(status(plan, "feta"), "planned");
  assert.equal(status(plan, "butter"), "unfulfilled");
  assert.equal(status(plan, "oil"), "stocked");
  assert.equal(status(plan, "tea"), "paused");

  assert.ok(plan.best);
  assert.deepEqual(plan.best.retailers, ["lidl", "mymarket"]);
  // lidl: yoghurt 4x1.90 + milk 2x1.69 + eggs 1x2.95 + pasta 2x1.10 = 16.13 ; mymarket: feta 1x5.50
  assert.equal(plan.best.total, 21.63);
  assert.deepEqual(
    plan.bySize.map((s) => [s.maxRetailers, s.combination.total]),
    [
      [1, 21.73],
      [2, 21.63],
    ],
  );

  const lidl = plan.best.stores.find((s) => s.retailer === "lidl");
  assert.equal(lidl?.subtotal, 16.13);
  const yoghurt = lidl?.lines.find((l) => l.itemId === "yoghurt");
  assert.equal(yoghurt?.brand, "ΦΑΓΕ");
  assert.equal(yoghurt?.packs, 4, "1.5 kg needed in 0.4 kg packs -> 4 packs");
  assert.ok(Math.abs((yoghurt?.unitPrice ?? 0) - 4.75) < 1e-9);

  assert.deepEqual(purchasesFromPlan(plan), { yoghurt: 1.6, milk: 2, eggs: 10, pasta: 1, feta: 0.4 });
});

test("price caps are hard limits and the reason shows how close the nearest offer was", async () => {
  const plan = await buildPlan(pantry(), new FixtureProvider(FIXTURE), { now: NOW });
  const butter = plan.items.find((i) => i.id === "butter");
  assert.equal(butter?.status, "unfulfilled");
  assert.match(butter?.reason ?? "", /3 offers over €12\/kg/);
  assert.match(butter?.reason ?? "", /€14\/kg/);
  assert.equal(butter?.diagnostics?.rejectedOffers.overUnitCap, 3);
  assert.equal(butter?.diagnostics?.cheapestOverCap?.retailerName, "My Market");
  for (const store of plan.best?.stores ?? []) {
    assert.ok(!store.lines.some((l) => l.itemId === "butter"));
  }
});

test("deny, allow and private-label rules keep products out of the plan", async () => {
  const plan = await buildPlan(pantry(), new FixtureProvider(FIXTURE), { now: NOW });
  const d = plan.items.find((i) => i.id === "yoghurt")?.diagnostics;
  assert.equal(d?.searched, 5);
  assert.equal(d?.rejectedProducts.denied, 1); // ΒΙΟ yoghurt
  assert.equal(d?.rejectedProducts.privateLabel, 1); // Sklavenitis own brand
  // ΦΑΓΕ at Sklavenitis (€5.25/kg), AB (€5.75/kg) are over the €5/kg cap
  assert.equal(d?.rejectedOffers.overUnitCap, 2);
  const bought = (plan.best?.stores ?? []).flatMap((s) => s.lines).filter((l) => l.itemId === "yoghurt");
  for (const l of bought) assert.ok(l.unitPrice <= 5 + 1e-9);
});

test("one-store limit surfaces stale-price and suspicious-unit-price warnings", async () => {
  const plan = await buildPlan(pantry({ maxRetailers: 1 }), new FixtureProvider(FIXTURE), { now: NOW });
  assert.deepEqual(plan.best?.retailers, ["lidl"]);
  assert.equal(plan.best?.total, 21.73);
  assert.ok(plan.warnings.some((w) => /Dodoni|ΦΕΤΑ ΠΟΠ 200G/.test(w) && /23 days old/.test(w)), plan.warnings.join("\n"));
  assert.ok(plan.warnings.some((w) => /ΜΑΚΑΡΟΝΙΑ/.test(w) && /pack size/.test(w)), plan.warnings.join("\n"));
});

test("pack size can be recovered from the product name when the API omits it", async () => {
  const plan = await buildPlan(pantry({ maxRetailers: 1 }), new FixtureProvider(FIXTURE), { now: NOW });
  const pasta = plan.best?.stores.flatMap((s) => s.lines).find((l) => l.itemId === "pasta");
  assert.equal(pasta?.packSize, 0.5);
  assert.equal(pasta?.packs, 2);
});

test("minimum basket pushes the plan to other stores", async () => {
  const plan = await buildPlan(
    pantry({ retailers: { lidl: { minBasket: 25 } } }),
    new FixtureProvider(FIXTURE),
    { now: NOW },
  );
  assert.deepEqual(plan.best?.retailers, ["mymarket", "sklavenitis"]);
  assert.equal(plan.best?.total, 22.6);
  assert.deepEqual(plan.bySize.map((s) => [s.maxRetailers, s.combination.total]), [
    [1, 22.74],
    [2, 22.6],
  ]);
});

test("excluded retailers are never used", async () => {
  const plan = await buildPlan(pantry({ excludeRetailers: ["LIDL"] }), new FixtureProvider(FIXTURE), { now: NOW });
  assert.ok(plan.best);
  assert.ok(!plan.best.retailers.includes("lidl"));
});

test("items that no store within the limit can supply are reported, not silently dropped", async () => {
  const mk = (id: string, name: string, retailer: string): Product => ({
    id, name, brand: null, barcode: null, unit: "kg", unitQuantity: 1, privateLabel: false,
    offers: [{ retailer, retailerName: retailer, price: 2, isDiscount: false }],
  });
  const provider = new FixtureProvider([mk("a", "alpha 1kg", "x"), mk("b", "beta 1kg", "y")]);
  const p = PantrySchema.parse({
    settings: { maxRetailers: 1 },
    items: [
      { id: "alpha", name: "alpha", unit: "kg", expected: 1 },
      { id: "beta", name: "beta", unit: "kg", expected: 1 },
    ],
  });
  const plan = await buildPlan(p, provider, { now: NOW });
  const statuses = plan.items.map((i) => i.status).sort();
  assert.deepEqual(statuses, ["left_out", "planned"]);
  assert.match(plan.items.find((i) => i.status === "left_out")?.reason ?? "", /1-store limit/);
});

test("provider failures are reported per item and as a warning", async () => {
  const failing: PriceProvider = {
    name: "down",
    async search() {
      throw new Error("HTTP 503");
    },
  };
  const plan = await buildPlan(pantry(), failing, { now: NOW });
  assert.equal(plan.best, null);
  assert.ok(plan.warnings.some((w) => /Every price lookup failed/.test(w)));
  const yoghurt = plan.items.find((i) => i.id === "yoghurt");
  assert.equal(yoghurt?.status, "unfulfilled");
  assert.match(yoghurt?.reason ?? "", /HTTP 503/);
});

test("itemIds limits the plan and rejects unknown ids", async () => {
  const plan = await buildPlan(pantry(), new FixtureProvider(FIXTURE), { now: NOW, itemIds: ["milk"] });
  assert.deepEqual(plan.items.map((i) => i.id), ["milk"]);
  await assert.rejects(() => buildPlan(pantry(), new FixtureProvider(FIXTURE), { itemIds: ["nope"] }), /No pantry item "nope"/);
});

test("formatPlan renders stores, shortfalls and trade-offs", async () => {
  const plan = await buildPlan(pantry(), new FixtureProvider(FIXTURE), { now: NOW });
  const text = formatPlan(plan);
  assert.match(text, /Shopping plan: €21\.63 across 2 stores/);
  assert.match(text, /Lidl — €16\.13/);
  assert.match(text, /✗ Butter/);
  assert.match(text, /Already stocked: Olive oil/);
  assert.match(text, /up to 1: €21\.73/);
});
