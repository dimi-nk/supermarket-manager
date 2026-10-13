import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import {
  PantryStore,
  addItem,
  addToActuals,
  editRules,
  needOf,
  removeItem,
  setActuals,
  updateSettings,
} from "../src/store.js";
import { PantrySchema } from "../src/types.js";

const empty = () => PantrySchema.parse({ version: 1 });

test("missing file loads as an empty pantry with default settings", async () => {
  const dir = await mkdtemp(join(tmpdir(), "pantry-"));
  const pantry = await new PantryStore(join(dir, "nope.json")).load();
  assert.deepEqual(pantry.items, []);
  assert.equal(pantry.settings.maxRetailers, 2);
  assert.equal(pantry.settings.packRoundingTolerance, 0.1);
});

test("save then load round-trips and writes valid JSON", async () => {
  const dir = await mkdtemp(join(tmpdir(), "pantry-"));
  const store = new PantryStore(join(dir, "sub", "pantry.json"));
  const p = empty();
  addItem(p, { name: "Greek Yoghurt", unit: "kg", expected: 2, rules: { deny: ["ΒΙΟ"], maxUnitPrice: 5 } });
  await store.save(p);
  JSON.parse(await readFile(store.path, "utf8"));
  const loaded = await store.load();
  assert.equal(loaded.items[0]?.id, "greek-yoghurt");
  assert.deepEqual(loaded.items[0]?.rules.deny, ["ΒΙΟ"]);
  assert.equal(loaded.items[0]?.rules.maxUnitPrice, 5);
  assert.equal(loaded.items[0]?.actual, 0);
});

test("invalid pantry data is rejected on save", async () => {
  const dir = await mkdtemp(join(tmpdir(), "pantry-"));
  const store = new PantryStore(join(dir, "p.json"));
  const p = empty();
  addItem(p, { name: "x", unit: "kg", expected: 1 });
  p.items[0]!.expected = -1;
  await assert.rejects(() => store.save(p));
});

test("addItem rejects duplicate ids; removeItem rejects unknown ids", () => {
  const p = empty();
  addItem(p, { id: "milk", name: "Milk", unit: "l", expected: 3 });
  assert.throws(() => addItem(p, { id: "milk", name: "Milk 2", unit: "l", expected: 1 }), /already exists/);
  assert.throws(() => removeItem(p, "bread"), /No pantry item "bread"/);
  removeItem(p, "milk");
  assert.equal(p.items.length, 0);
});

test("setActuals replaces, addToActuals adds, need is expected minus actual floored at zero", () => {
  const p = empty();
  addItem(p, { id: "milk", name: "Milk", unit: "l", expected: 3, actual: 1 });
  assert.equal(needOf(p.items[0]!), 2);
  setActuals(p, { milk: 2.5 });
  assert.equal(needOf(p.items[0]!), 0.5);
  addToActuals(p, { milk: 1 });
  assert.equal(p.items[0]?.actual, 3.5);
  assert.equal(needOf(p.items[0]!), 0);
  assert.throws(() => setActuals(p, { milk: -1 }), /Invalid quantity/);
  assert.throws(() => setActuals(p, { ghost: 1 }), /No pantry item/);
});

test("editRules adds, removes and clears allow/deny entries case-insensitively without duplicates", () => {
  const p = empty();
  addItem(p, { id: "y", name: "Yoghurt", unit: "kg", expected: 1 });
  editRules(p, "y", { allowAdd: ["ΔΕΛΤΑ", "δελτα", "ΟΛΥΜΠΟΣ"], denyAdd: ["ΒΙΟ"] });
  assert.deepEqual(p.items[0]?.rules.allow, ["ΔΕΛΤΑ", "ΟΛΥΜΠΟΣ"]);
  editRules(p, "y", { allowRemove: ["δελτα"] });
  assert.deepEqual(p.items[0]?.rules.allow, ["ΟΛΥΜΠΟΣ"]);
  editRules(p, "y", { clearDeny: true, maxUnitPrice: 5, minPackSize: 0.5 });
  assert.deepEqual(p.items[0]?.rules.deny, []);
  assert.equal(p.items[0]?.rules.maxUnitPrice, 5);
  editRules(p, "y", { maxUnitPrice: null });
  assert.equal(p.items[0]?.rules.maxUnitPrice, undefined);
  assert.equal(p.items[0]?.rules.minPackSize, 0.5);
});

test("updateSettings merges and the result still validates", () => {
  const p = empty();
  updateSettings(p, { maxRetailers: 3, excludeRetailers: ["lidl"] });
  assert.doesNotThrow(() => PantrySchema.parse(p));
  assert.equal(p.settings.maxRetailers, 3);
  assert.equal(p.settings.packRoundingTolerance, 0.1);
});

test("store.update persists a mutation", async () => {
  const dir = await mkdtemp(join(tmpdir(), "pantry-"));
  const store = new PantryStore(join(dir, "p.json"));
  await store.update((p) => addItem(p, { id: "a", name: "A", unit: "each", expected: 4 }));
  await store.update((p) => setActuals(p, { a: 1 }));
  assert.equal((await store.load()).items[0]?.actual, 1);
});
