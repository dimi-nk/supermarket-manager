import { checkProductRules } from "./rules.js";
import { roundMoney } from "./text.js";
import type { Item, Product, Settings } from "./types.js";
import { resolvePack } from "./units.js";

export interface Option {
  itemId: string;
  retailer: string;
  retailerName: string;
  productId: string;
  productName: string;
  brand: string | null;
  barcode: string | null;
  /** Pack size in the item's unit (kg, l or pieces). */
  packSize: number;
  packs: number;
  /** Price of one pack. */
  price: number;
  /** EUR per item unit, computed from price and pack size. */
  unitPrice: number;
  lineCost: number;
  /** Quantity bought minus quantity needed (>= -tolerance). */
  overshoot: number;
  isDiscount: boolean;
  lastUpdated: string | null;
  /** Provider's own unit price disagrees with ours by >30%: pack size may be mis-parsed. */
  unitPriceMismatch: boolean;
}

export interface Diagnostics {
  /** Products returned by the search. */
  searched: number;
  /** Products dropped before looking at prices. */
  rejectedProducts: {
    denied: number;
    notAllowed: number;
    privateLabel: number;
    unknownPack: number;
    wrongMeasure: number;
    packSize: number;
  };
  /** Individual retailer offers dropped. */
  rejectedOffers: {
    excludedRetailer: number;
    invalidPrice: number;
    overUnitCap: number;
    overPackCap: number;
  };
  /** Cheapest offer that only failed on a price cap — shows how far off the cap is. */
  cheapestOverCap: { productName: string; retailerName: string; unitPrice: number } | null;
}

export function emptyDiagnostics(searched: number): Diagnostics {
  return {
    searched,
    rejectedProducts: {
      denied: 0,
      notAllowed: 0,
      privateLabel: 0,
      unknownPack: 0,
      wrongMeasure: 0,
      packSize: 0,
    },
    rejectedOffers: { excludedRetailer: 0, invalidPrice: 0, overUnitCap: 0, overPackCap: 0 },
    cheapestOverCap: null,
  };
}

const EPS = 1e-9;

export function buildOptions(
  item: Item,
  need: number,
  products: Product[],
  settings: Settings,
): { options: Option[]; diagnostics: Diagnostics } {
  const diagnostics = emptyDiagnostics(products.length);
  const options: Option[] = [];
  const excluded = new Set(settings.excludeRetailers.map((r) => r.toLowerCase()));
  const rules = item.rules;

  for (const product of products) {
    const reason = checkProductRules(product, rules);
    if (reason === "denied") {
      diagnostics.rejectedProducts.denied++;
      continue;
    }
    if (reason === "not_allowed") {
      diagnostics.rejectedProducts.notAllowed++;
      continue;
    }
    if (reason === "private_label") {
      diagnostics.rejectedProducts.privateLabel++;
      continue;
    }

    const pack = resolvePack(product.unit, product.unitQuantity, product.name);
    if (!pack) {
      diagnostics.rejectedProducts.unknownPack++;
      continue;
    }
    if (pack.measure !== item.unit) {
      diagnostics.rejectedProducts.wrongMeasure++;
      continue;
    }
    if (
      (rules.minPackSize !== undefined && pack.amount < rules.minPackSize - EPS) ||
      (rules.maxPackSize !== undefined && pack.amount > rules.maxPackSize + EPS)
    ) {
      diagnostics.rejectedProducts.packSize++;
      continue;
    }

    const ratio = need / pack.amount;
    const packs = Math.max(1, Math.ceil(ratio - settings.packRoundingTolerance - EPS));

    for (const offer of product.offers) {
      if (excluded.has(offer.retailer.toLowerCase())) {
        diagnostics.rejectedOffers.excludedRetailer++;
        continue;
      }
      if (!Number.isFinite(offer.price) || offer.price <= 0) {
        diagnostics.rejectedOffers.invalidPrice++;
        continue;
      }
      const unitPrice = offer.price / pack.amount;

      const overUnit = rules.maxUnitPrice !== undefined && unitPrice > rules.maxUnitPrice + EPS;
      const overPack = rules.maxPackPrice !== undefined && offer.price > rules.maxPackPrice + EPS;
      if (overUnit || overPack) {
        if (overUnit) diagnostics.rejectedOffers.overUnitCap++;
        else diagnostics.rejectedOffers.overPackCap++;
        if (!diagnostics.cheapestOverCap || unitPrice < diagnostics.cheapestOverCap.unitPrice) {
          diagnostics.cheapestOverCap = {
            productName: product.name,
            retailerName: offer.retailerName,
            unitPrice: roundMoney(unitPrice),
          };
        }
        continue;
      }

      const reported = offer.unitPrice;
      const mismatch =
        typeof reported === "number" &&
        reported > 0 &&
        Math.abs(reported - unitPrice) / unitPrice > 0.3;

      options.push({
        itemId: item.id,
        retailer: offer.retailer,
        retailerName: offer.retailerName,
        productId: product.id,
        productName: product.name,
        brand: product.brand,
        barcode: product.barcode,
        packSize: pack.amount,
        packs,
        price: offer.price,
        unitPrice,
        lineCost: roundMoney(offer.price * packs),
        overshoot: packs * pack.amount - need,
        isDiscount: offer.isDiscount,
        lastUpdated: offer.lastUpdated ?? null,
        unitPriceMismatch: mismatch,
      });
    }
  }
  return { options, diagnostics };
}

/** Keep only the cheapest option per retailer (ties: lower unit price, then fewer packs). */
export function bestPerRetailer(options: Option[]): Map<string, Option> {
  const best = new Map<string, Option>();
  for (const option of options) {
    const current = best.get(option.retailer);
    if (!current || isBetter(option, current)) best.set(option.retailer, option);
  }
  return best;
}

function isBetter(a: Option, b: Option): boolean {
  if (Math.abs(a.lineCost - b.lineCost) > 0.004) return a.lineCost < b.lineCost;
  if (Math.abs(a.unitPrice - b.unitPrice) > 1e-9) return a.unitPrice < b.unitPrice;
  return a.packs < b.packs;
}
