import assert from "node:assert/strict";
import { mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { runCli } from "../src/cli-core.js";
import { createRuntime } from "../src/config.js";

const FIXTURE = new URL("../fixtures/products.json", import.meta.url).pathname;

async function setup() {
  const dir = await mkdtemp(join(tmpdir(), "pantry-cli-"));
  const rt = createRuntime({
    PANTRY_FILE: join(dir, "pantry.json"),
    PANTRY_PROVIDER: "fixture",
    PANTRY_FIXTURE: FIXTURE,
  });
  const run = (...argv: string[]) => runCli(argv, rt);
  return { rt, run };
}

test("weekly loop: add, stock, rules, plan, received", async () => {
  const { run } = await setup();

  assert.match(await run("list"), /Pantry is empty/);

  await run("add", "Milk", "--unit", "l", "--expected", "3", "--query", "γάλα φρέσκο", "--id", "milk");
  await run(
    "add", "Greek yoghurt", "--unit", "kg", "--expected", "2", "--id", "yoghurt",
    "--query", "γιαούρτι στραγγιστό", "--allow", "ΔΕΛΤΑ,ΟΛΥΜΠΟΣ", "--deny", "ΒΙΟ", "--max-unit-price", "5",
  );

  assert.match(await run("stock", "milk=1", "yoghurt=0.5"), /milk: have 1 l, need 2/);
  const listed = await run("list");
  assert.match(listed, /yoghurt: Greek yoghurt — have 0\.5\/2 kg, need 1\.5/);
  assert.match(listed, /≤€5\.00\/kg/);
  assert.match(listed, /allow: ΔΕΛΤΑ, ΟΛΥΜΠΟΣ/);

  // Tighten the yoghurt rules from the CLI and make sure the plan obeys them.
  assert.match(await run("rule", "yoghurt", "--allow-remove", "ΔΕΛΤΑ", "--max-unit-price", "4"), /allow: ΟΛΥΜΠΟΣ/);

  const planText = await run("plan");
  assert.match(planText, /Shopping plan:/);
  assert.match(planText, /ΟΛΥΜΠΟΣ/);
  assert.doesNotMatch(planText, /ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ/);

  const json = JSON.parse(await run("plan", "--json", "--no-save"));
  assert.equal(json.provider, "fixture");
  // ΟΛΥΜΠΟΣ is €3.95-4.40/kg: with a 4.00 cap only Sklavenitis (3.95) qualifies; 2 packs
  const yoghurtLine = json.best.stores.flatMap((s: { lines: Array<{ itemId: string }> }) => s.lines).find((l: { itemId: string }) => l.itemId === "yoghurt");
  assert.equal(yoghurtLine.retailer, "sklavenitis");
  assert.equal(yoghurtLine.lineCost, 7.9);

  const received = await run("received");
  assert.match(received, /yoghurt: \+2/);
  assert.match(received, /milk: \+2/);
  await assert.rejects(() => run("received"), /already applied/);

  assert.match(await run("list"), /milk: Milk — have 3\/3 l, need 0/);
  assert.match(await run("plan"), /Nothing to buy/);
});

test("received --skip leaves items you did not buy", async () => {
  const { run } = await setup();
  await run("add", "Milk", "--unit", "l", "--expected", "2", "--query", "γάλα φρέσκο", "--id", "milk");
  await run("add", "Eggs", "--unit", "each", "--expected", "10", "--query", "αυγά", "--id", "eggs");
  await run("plan");
  await run("received", "--skip", "eggs");
  const listed = await run("list");
  assert.match(listed, /milk: Milk — have 2\/2 l, need 0/);
  assert.match(listed, /eggs: Eggs — have 0\/10 each, need 10/);
});

test("pause, resume, expect, settings and remove", async () => {
  const { run } = await setup();
  await run("add", "Eggs", "--unit", "each", "--expected", "10", "--id", "eggs", "--query", "αυγά");
  assert.equal(await run("pause", "eggs"), "eggs paused");
  assert.match(await run("list"), /eggs \[paused\]/);
  assert.match(await run("plan"), /Nothing to buy/);
  await run("resume", "eggs");
  assert.match(await run("expect", "eggs=6"), /expected 6 each, need 6/);

  const settings = JSON.parse(
    await run("settings", "--max-retailers", "3", "--exclude", "Lidl,AB", "--fee", "mymarket=3.5", "--free-above", "mymarket=40", "--min-basket", "sklavenitis=25"),
  );
  assert.equal(settings.maxRetailers, 3);
  assert.deepEqual(settings.excludeRetailers, ["lidl", "ab"]);
  assert.deepEqual(settings.retailers.mymarket, { fee: 3.5, freeAbove: 40 });
  assert.deepEqual(settings.retailers.sklavenitis, { fee: 0, minBasket: 25 });

  await run("remove", "eggs");
  assert.match(await run("list"), /Pantry is empty/);
});

test("search shows packs, unit prices and a brand summary", async () => {
  const { run } = await setup();
  const out = await run("search", "γιαούρτι στραγγιστό");
  assert.match(out, /5 products found/);
  assert.match(out, /ΦΑΓΕ ΓΙΑΟΥΡΤΙ ΣΤΡΑΓΓΙΣΤΟ 2% 2X200G.*0\.4 kg/);
  assert.match(out, /Brands:/);
  assert.match(out, /ΟΛΥΜΠΟΣ \(1\) from €3\.95/);
});

test("user errors are clear", async () => {
  const { run } = await setup();
  await assert.rejects(() => run("stock", "milk=1"), /No pantry item "milk"/);
  await assert.rejects(() => run("stock", "milk"), /Expected id=quantity/);
  await assert.rejects(() => run("add", "X"), /--unit and --expected are required/);
  await assert.rejects(() => run("add", "X", "--unit", "gallons", "--expected", "1"));
  await assert.rejects(() => run("frobnicate"), /Unknown command/);
  await assert.rejects(() => run("received"), /No saved plan/);
  assert.match(await run("help"), /pantry plan/);
});
