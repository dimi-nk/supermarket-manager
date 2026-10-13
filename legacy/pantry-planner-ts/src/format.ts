import type { Plan } from "./plan.js";
import type { Pantry } from "./types.js";
import { needOf } from "./store.js";

const eur = (n: number) => `€${n.toFixed(2)}`;
const qty = (n: number) => String(Math.round(n * 1000) / 1000);

/** Compact, phone-friendly plain-text rendering of a plan. */
export function formatPlan(plan: Plan): string {
  const lines: string[] = [];
  const best = plan.best;

  if (!best) {
    lines.push("Nothing to buy from any store.");
  } else {
    const n = best.stores.length;
    lines.push(`Shopping plan: ${eur(best.total)} across ${n} store${n === 1 ? "" : "s"}`);
    for (const store of best.stores) {
      lines.push("");
      const fee = store.fee > 0 ? ` + ${eur(store.fee)} fee` : "";
      lines.push(`${store.retailerName} — ${eur(store.subtotal)}${fee}`);
      for (const l of store.lines) {
        const brand = l.brand && !l.productName.toLowerCase().includes(l.brand.toLowerCase()) ? `${l.brand} ` : "";
        const deal = l.isDiscount ? " (deal)" : "";
        lines.push(
          `  ${l.packs} × ${brand}${l.productName} — ${eur(l.price)}${deal} · ${eur(l.unitPrice)}/${unitLabel(plan, l.itemId)} · ${eur(l.lineCost)}`,
        );
      }
    }
  }

  const problems = plan.items.filter((i) => i.status === "unfulfilled" || i.status === "left_out");
  if (problems.length) {
    lines.push("", "Not planned:");
    for (const p of problems) lines.push(`  ✗ ${p.name} (need ${qty(p.need)} ${p.unit}) — ${p.reason}`);
  }

  const stocked = plan.items.filter((i) => i.status === "stocked");
  if (stocked.length) lines.push("", `Already stocked: ${stocked.map((i) => i.name).join(", ")}`);

  if (plan.bySize.length > 1) {
    lines.push("", "Store-count trade-off:");
    for (const s of plan.bySize) {
      lines.push(`  up to ${s.maxRetailers}: ${eur(s.combination.total)} (${s.combination.retailers.join(" + ")})`);
    }
  }

  if (plan.warnings.length) {
    lines.push("", "Warnings:");
    for (const w of plan.warnings) lines.push(`  ! ${w}`);
  }
  return lines.join("\n");
}

function unitLabel(plan: Plan, itemId: string): string {
  return plan.items.find((i) => i.id === itemId)?.unit ?? "unit";
}

export function formatItem(i: Pantry["items"][number]): string {
  const need = needOf(i);
  const caps: string[] = [];
  if (i.rules.maxUnitPrice !== undefined) caps.push(`≤${eur(i.rules.maxUnitPrice)}/${i.unit}`);
  if (i.rules.maxPackPrice !== undefined) caps.push(`pack ≤${eur(i.rules.maxPackPrice)}`);
  if (i.rules.minPackSize !== undefined) caps.push(`pack ≥${qty(i.rules.minPackSize)} ${i.unit}`);
  if (i.rules.maxPackSize !== undefined) caps.push(`pack ≤${qty(i.rules.maxPackSize)} ${i.unit}`);
  if (!i.rules.allowPrivateLabel) caps.push("no own-brand");
  if (i.rules.allow.length) caps.push(`allow: ${i.rules.allow.join(", ")}`);
  if (i.rules.deny.length) caps.push(`deny: ${i.rules.deny.join(", ")}`);
  const flag = i.paused ? " [paused]" : "";
  return `${i.id}${flag}: ${i.name} — have ${qty(i.actual)}/${qty(i.expected)} ${i.unit}, need ${qty(need)}${caps.length ? `\n    ${caps.join(" · ")}` : ""}`;
}

export function formatPantry(pantry: Pantry): string {
  if (pantry.items.length === 0) return "Pantry is empty. Add items with `pantry add`.";
  const s = pantry.settings;
  return [
    ...pantry.items.map(formatItem),
    "",
    `Settings: up to ${s.maxRetailers} store${s.maxRetailers === 1 ? "" : "s"}${s.excludeRetailers.length ? `, excluding ${s.excludeRetailers.join(", ")}` : ""}`,
  ].join("\n");
}
