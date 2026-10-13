import { norm } from "./text.js";

/** Measure kinds. Item quantities are kept in kg (mass), l (volume) or each (count). */
export type Measure = "kg" | "l" | "each";

export interface PackSize {
  measure: Measure;
  /** Amount in the base unit of `measure` (kg, l or pieces). */
  amount: number;
  source: "api" | "name";
}

const UNIT_TABLE: Record<string, { measure: Measure; factor: number }> = {
  kg: { measure: "kg", factor: 1 },
  kgr: { measure: "kg", factor: 1 },
  kilo: { measure: "kg", factor: 1 },
  κιλο: { measure: "kg", factor: 1 },
  κιλα: { measure: "kg", factor: 1 },
  g: { measure: "kg", factor: 0.001 },
  gr: { measure: "kg", factor: 0.001 },
  γρ: { measure: "kg", factor: 0.001 },
  γ: { measure: "kg", factor: 0.001 },
  γραμμαρια: { measure: "kg", factor: 0.001 },
  l: { measure: "l", factor: 1 },
  lt: { measure: "l", factor: 1 },
  ltr: { measure: "l", factor: 1 },
  λιτρο: { measure: "l", factor: 1 },
  λιτρα: { measure: "l", factor: 1 },
  λτ: { measure: "l", factor: 1 },
  ml: { measure: "l", factor: 0.001 },
  μλ: { measure: "l", factor: 0.001 },
  cl: { measure: "l", factor: 0.01 },
  τεμ: { measure: "each", factor: 1 },
  τεμαχιο: { measure: "each", factor: 1 },
  τεμαχια: { measure: "each", factor: 1 },
  pcs: { measure: "each", factor: 1 },
  pc: { measure: "each", factor: 1 },
  each: { measure: "each", factor: 1 },
  unit: { measure: "each", factor: 1 },
  units: { measure: "each", factor: 1 },
  item: { measure: "each", factor: 1 },
};

/** Convert an API-style (unit, unit_quantity) pair into a normalised pack size. */
export function parsePack(
  unit: string | null | undefined,
  quantity: number | null | undefined,
): PackSize | null {
  if (!unit || typeof quantity !== "number" || !Number.isFinite(quantity) || quantity <= 0) {
    return null;
  }
  const entry = UNIT_TABLE[norm(unit)];
  if (!entry) return null;
  return { measure: entry.measure, amount: quantity * entry.factor, source: "api" };
}

const NAME_SIZE =
  /(?:(\d+)\s*[x×χ]\s*)?(\d+(?:[.,]\d+)?)\s*(kgr|kg|κιλο|κιλα|γρ|gr|g|γ|ml|μλ|cl|ltr|lt|λτ|λιτρο|l|τεμ|pcs)(?![\p{L}])/giu;

/**
 * Fallback: pull a pack size out of a product name such as
 * "ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ ΣΤΡΑΓΓΙΣΤΟ 2x200g" or "ΓΑΛΑ 1,5 L". Returns null if nothing parseable.
 * If several sizes appear, the last one wins (names normally end with the size).
 */
export function parsePackFromName(name: string | null | undefined): PackSize | null {
  if (!name) return null;
  const text = norm(name.replace(/×/g, "x")).replace(/(\d),(\d)/g, "$1.$2");
  let last: PackSize | null = null;
  for (const m of text.matchAll(NAME_SIZE)) {
    const multiplier = m[1] ? Number(m[1]) : 1;
    const amount = Number(m[2]);
    const entry = UNIT_TABLE[norm(m[3] ?? "")];
    if (!entry || !Number.isFinite(amount) || amount <= 0 || !Number.isFinite(multiplier)) continue;
    last = { measure: entry.measure, amount: multiplier * amount * entry.factor, source: "name" };
  }
  return last;
}

export function resolvePack(
  unit: string | null | undefined,
  quantity: number | null | undefined,
  name: string | null | undefined,
): PackSize | null {
  return parsePack(unit, quantity) ?? parsePackFromName(name);
}
