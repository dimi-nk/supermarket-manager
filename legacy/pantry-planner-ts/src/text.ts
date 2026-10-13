/**
 * Text normalisation for matching Greek (and Latin) product names.
 * Lower-cases, strips diacritics (tonos, dialytika), folds final sigma,
 * and collapses whitespace so "Δέλτα", "ΔΕΛΤΑ" and "δελτα" all match.
 */
export function norm(input: string | null | undefined): string {
  if (!input) return "";
  return input
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/ς/g, "σ")
    .replace(/[^\p{L}\p{N}.,%]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** True when every whitespace-separated token of `needle` appears in `haystack` (both normalised). */
export function containsAllTokens(haystack: string, needle: string): boolean {
  const h = norm(haystack);
  const tokens = norm(needle).split(" ").filter(Boolean);
  if (tokens.length === 0) return false;
  return tokens.every((t) => h.includes(t));
}

export function roundMoney(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

export function slugify(input: string): string {
  const s = norm(input).replace(/\s+/g, "-").replace(/[^\p{L}\p{N}-]/gu, "");
  return s || "item";
}
