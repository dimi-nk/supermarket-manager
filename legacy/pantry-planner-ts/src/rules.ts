import { containsAllTokens } from "./text.js";
import type { Product, Rules } from "./types.js";

export type RejectReason = "denied" | "not_allowed" | "private_label";

function matchesEntry(product: Product, entry: string): boolean {
  const trimmed = entry.trim();
  if (!trimmed) return false;
  const lower = trimmed.toLowerCase();
  if (lower.startsWith("gtin:")) {
    const want = trimmed.slice(5).trim();
    return !!want && product.barcode === want;
  }
  if (lower.startsWith("id:")) {
    const want = trimmed.slice(3).trim();
    return !!want && product.id === want;
  }
  return (
    (product.brand ? containsAllTokens(product.brand, trimmed) : false) ||
    containsAllTokens(product.name, trimmed)
  );
}

/** Product-level checks (before any retailer price is considered). Returns null if acceptable. */
export function checkProductRules(product: Product, rules: Rules): RejectReason | null {
  if (rules.deny.some((entry) => matchesEntry(product, entry))) return "denied";
  if (!rules.allowPrivateLabel && product.privateLabel) return "private_label";
  if (rules.allow.length > 0 && !rules.allow.some((entry) => matchesEntry(product, entry))) {
    return "not_allowed";
  }
  return null;
}
