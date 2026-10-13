import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { Runtime } from "./config.js";
import { buildPlan, purchasesFromPlan, type Plan } from "./plan.js";
import { addToActuals } from "./store.js";
import { roundMoney } from "./text.js";
import { resolvePack } from "./units.js";

/* ---------- planning + last-plan bookkeeping ---------- */

interface SavedPlan {
  plan: Plan;
  /** Set once the purchases were added to stock, so they can't be applied twice by accident. */
  appliedAt?: string;
}

export async function runPlan(
  rt: Runtime,
  opts: { itemIds?: string[]; save?: boolean } = {},
): Promise<Plan> {
  const pantry = await rt.store.load();
  const plan = await buildPlan(pantry, rt.provider, opts.itemIds ? { itemIds: opts.itemIds } : {});
  if (opts.save !== false) {
    await mkdir(dirname(rt.lastPlanPath), { recursive: true });
    const saved: SavedPlan = { plan };
    await writeFile(rt.lastPlanPath, JSON.stringify(saved, null, 2) + "\n", "utf8");
  }
  return plan;
}

export interface ApplyResult {
  added: Record<string, number>;
  plannedAt: string;
}

/**
 * Add the quantities from the most recent saved plan to `actual` stock.
 * Pass `skip` to leave out items you didn't end up buying.
 */
export async function applyLastPlan(
  rt: Runtime,
  opts: { skip?: string[]; force?: boolean } = {},
): Promise<ApplyResult> {
  let saved: SavedPlan;
  try {
    saved = JSON.parse(await readFile(rt.lastPlanPath, "utf8")) as SavedPlan;
  } catch {
    throw new Error("No saved plan found. Run a plan first.");
  }
  if (saved.appliedAt && !opts.force) {
    throw new Error(
      `The last plan (${saved.plan.generatedAt}) was already applied on ${saved.appliedAt}. Run a new plan, or pass force to apply it again.`,
    );
  }
  const added = purchasesFromPlan(saved.plan);
  for (const id of opts.skip ?? []) delete added[id];
  await rt.store.update((p) => addToActuals(p, added));
  saved.appliedAt = new Date().toISOString();
  await writeFile(rt.lastPlanPath, JSON.stringify(saved, null, 2) + "\n", "utf8");
  return { added, plannedAt: saved.plan.generatedAt };
}

/* ---------- product discovery (for choosing allow / deny entries) ---------- */

export interface ProductView {
  id: string;
  name: string;
  brand: string | null;
  barcode: string | null;
  privateLabel: boolean;
  /** e.g. "1 kg", "0.4 kg", "6 each"; null if the pack size could not be determined. */
  pack: string | null;
  measure: "kg" | "l" | "each" | null;
  cheapest: { retailerName: string; price: number; unitPrice: number | null } | null;
  retailers: string[];
}

export interface BrandSummary {
  brand: string;
  products: number;
  cheapestUnitPrice: number | null;
}

export async function searchProducts(
  rt: Runtime,
  query: string,
  opts: { limit?: number; pages?: number } = {},
): Promise<{ products: ProductView[]; brands: BrandSummary[]; total: number }> {
  const pantry = await rt.store.load();
  const found = await rt.provider.search(query, { pages: opts.pages ?? pantry.settings.searchPages });

  const views: ProductView[] = found.map((p) => {
    const pack = resolvePack(p.unit, p.unitQuantity, p.name);
    let cheapest: ProductView["cheapest"] = null;
    for (const o of p.offers) {
      const unitPrice = pack ? o.price / pack.amount : null;
      const better =
        !cheapest ||
        (unitPrice !== null && (cheapest.unitPrice === null || unitPrice < cheapest.unitPrice)) ||
        (unitPrice === null && cheapest.unitPrice === null && o.price < cheapest.price);
      if (better) {
        cheapest = {
          retailerName: o.retailerName,
          price: o.price,
          unitPrice: unitPrice === null ? null : roundMoney(unitPrice),
        };
      }
    }
    return {
      id: p.id,
      name: p.name,
      brand: p.brand,
      barcode: p.barcode,
      privateLabel: p.privateLabel,
      pack: pack ? `${Math.round(pack.amount * 1000) / 1000} ${pack.measure}` : null,
      measure: pack?.measure ?? null,
      cheapest,
      retailers: [...new Set(p.offers.map((o) => o.retailer))].sort(),
    };
  });

  views.sort((a, b) => (a.cheapest?.unitPrice ?? Infinity) - (b.cheapest?.unitPrice ?? Infinity));

  const byBrand = new Map<string, BrandSummary>();
  for (const v of views) {
    const key = v.brand ?? "(no brand)";
    const entry = byBrand.get(key) ?? { brand: key, products: 0, cheapestUnitPrice: null };
    entry.products++;
    const u = v.cheapest?.unitPrice ?? null;
    if (u !== null && (entry.cheapestUnitPrice === null || u < entry.cheapestUnitPrice)) {
      entry.cheapestUnitPrice = u;
    }
    byBrand.set(key, entry);
  }
  const brands = [...byBrand.values()].sort(
    (a, b) => (a.cheapestUnitPrice ?? Infinity) - (b.cheapestUnitPrice ?? Infinity),
  );

  return { products: views.slice(0, opts.limit ?? 15), brands, total: views.length };
}

export function formatSearch(r: Awaited<ReturnType<typeof searchProducts>>, measureHint?: string): string {
  if (r.total === 0) return "No products found. Try a simpler or Greek search term.";
  const lines = [`${r.total} products found. Cheapest per unit first:`];
  for (const p of r.products) {
    const price = p.cheapest
      ? `€${p.cheapest.price.toFixed(2)} at ${p.cheapest.retailerName}${p.cheapest.unitPrice !== null ? ` (€${p.cheapest.unitPrice.toFixed(2)}/${p.measure ?? measureHint ?? "unit"})` : ""}`
      : "no price";
    const brand = p.brand && !p.name.toLowerCase().includes(p.brand.toLowerCase()) ? `${p.brand} ` : "";
    lines.push(`  ${brand}${p.name}${p.privateLabel ? " [own brand]" : ""} — ${p.pack ?? "pack ?"} — ${price}`);
  }
  if (r.total > r.products.length) lines.push(`  … and ${r.total - r.products.length} more`);
  lines.push("", "Brands:");
  for (const b of r.brands.slice(0, 12)) {
    lines.push(`  ${b.brand} (${b.products})${b.cheapestUnitPrice !== null ? ` from €${b.cheapestUnitPrice.toFixed(2)}` : ""}`);
  }
  return lines.join("\n");
}
