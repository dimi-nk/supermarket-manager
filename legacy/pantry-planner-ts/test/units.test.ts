import assert from "node:assert/strict";
import { test } from "node:test";
import { containsAllTokens, norm } from "../src/text.js";
import { parsePack, parsePackFromName, resolvePack } from "../src/units.js";

test("norm folds case, accents and final sigma", () => {
  assert.equal(norm("Δέλτα"), norm("ΔΕΛΤΑ"));
  assert.equal(norm("ΟΛΥΜΠΟΣ"), norm("Όλυμπος"));
  assert.equal(norm("  Γιαούρτι   στραγγιστό "), "γιαουρτι στραγγιστο");
});

test("containsAllTokens needs every token, in any order", () => {
  assert.ok(containsAllTokens("ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ ΣΤΡΑΓΓΙΣΤΟ 1KG", "στραγγιστό γιαούρτι"));
  assert.ok(!containsAllTokens("ΔΕΛΤΑ ΓΑΛΑ 1L", "γιαούρτι"));
  assert.ok(!containsAllTokens("anything", "   "));
});

test("parsePack converts API unit/quantity pairs", () => {
  assert.deepEqual(parsePack("g", 400), { measure: "kg", amount: 0.4, source: "api" });
  assert.deepEqual(parsePack("ml", 750), { measure: "l", amount: 0.75, source: "api" });
  assert.deepEqual(parsePack("τεμ", 6), { measure: "each", amount: 6, source: "api" });
  assert.equal(parsePack("kg", 0), null);
  assert.equal(parsePack(null, 1), null);
  assert.equal(parsePack("furlong", 1), null);
});

test("parsePackFromName handles sizes, decimals and multipacks", () => {
  assert.equal(parsePackFromName("ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ 1KG")?.amount, 1);
  assert.equal(parsePackFromName("ΓΑΛΑ 1,5L")?.amount, 1.5);
  assert.equal(parsePackFromName("ΓΙΑΟΥΡΤΙ 2X200G")?.amount, 0.4);
  assert.equal(parsePackFromName("ΓΙΑΟΥΡΤΙ 3×150ΓΡ")?.amount, 0.45);
  assert.equal(parsePackFromName("ΜΑΚΑΡΟΝΙΑ ΣΠΑΓΓΕΤΙ Νο6 500Γ")?.amount, 0.5);
  assert.equal(parsePackFromName("ΑΥΓΑ ΜΕΣΑΙΑ 10 ΤΕΜ")?.measure, "each");
  assert.equal(parsePackFromName("ΧΑΡΤΙ ΚΟΥΖΙΝΑΣ"), null);
  assert.equal(parsePackFromName("ΣΑΠΟΥΝΙ 4ΤΕΜΑΧΙΑ ΓΑΛΑ"), null, "unit glued to a longer word is not a size");
});

test("resolvePack prefers API fields over the name", () => {
  assert.equal(resolvePack("g", 500, "ΖΑΧΑΡΗ 1KG")?.amount, 0.5);
  assert.equal(resolvePack(null, null, "ΖΑΧΑΡΗ 1KG")?.amount, 1);
  assert.equal(resolvePack(null, null, "ΖΑΧΑΡΗ"), null);
});
