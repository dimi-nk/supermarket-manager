import assert from "node:assert/strict";
import { test } from "node:test";
import { checkProductRules } from "../src/rules.js";
import type { Product, Rules } from "../src/types.js";

const product = (over: Partial<Product> = {}): Product => ({
  id: "p1",
  name: "ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ ΣΤΡΑΓΓΙΣΤΟ 1KG",
  brand: "ΔΕΛΤΑ",
  barcode: "5201234567890",
  unit: "kg",
  unitQuantity: 1,
  privateLabel: false,
  offers: [],
  ...over,
});
const rules = (over: Partial<Rules> = {}): Rules => ({ allow: [], deny: [], allowPrivateLabel: true, ...over });

test("no rules accepts everything", () => {
  assert.equal(checkProductRules(product(), rules()), null);
});

test("allow list restricts to listed brands, accent and case insensitive", () => {
  assert.equal(checkProductRules(product(), rules({ allow: ["δέλτα"] })), null);
  assert.equal(checkProductRules(product(), rules({ allow: ["ΟΛΥΜΠΟΣ", "ΦΑΓΕ"] })), "not_allowed");
});

test("deny beats allow", () => {
  assert.equal(checkProductRules(product(), rules({ allow: ["ΔΕΛΤΑ"], deny: ["Δέλτα"] })), "denied");
});

test("deny can match product name words, not just brand", () => {
  assert.equal(checkProductRules(product({ brand: null }), rules({ deny: ["στραγγιστό"] })), "denied");
});

test("gtin: and id: entries match exactly", () => {
  assert.equal(checkProductRules(product(), rules({ allow: ["gtin:5201234567890"] })), null);
  assert.equal(checkProductRules(product(), rules({ allow: ["gtin:5200000000000"] })), "not_allowed");
  assert.equal(checkProductRules(product(), rules({ deny: ["id:p1"] })), "denied");
  assert.equal(checkProductRules(product(), rules({ deny: ["id:p2"] })), null);
});

test("private label can be switched off", () => {
  const own = product({ privateLabel: true });
  assert.equal(checkProductRules(own, rules()), null);
  assert.equal(checkProductRules(own, rules({ allowPrivateLabel: false })), "private_label");
});

test("blank entries never match", () => {
  assert.equal(checkProductRules(product(), rules({ deny: ["", "  "] })), null);
});
