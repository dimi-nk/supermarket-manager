import { z } from "zod";

/** Rules that decide which products may satisfy a pantry item. */
export const RulesSchema = z
  .object({
    /**
     * If non-empty, only products matching at least one entry are allowed.
     * Entries match brand or product name (accent/case-insensitive, all words must appear).
     * Prefix with "gtin:" or "id:" to match an exact barcode / PosoKanei product id.
     */
    allow: z.array(z.string()).default([]),
    /** Products matching any entry are never bought. Deny always beats allow. */
    deny: z.array(z.string()).default([]),
    /** Hard cap in EUR per item unit (per kg, per l or per piece). */
    maxUnitPrice: z.number().positive().optional(),
    /** Hard cap in EUR for a single pack. */
    maxPackPrice: z.number().positive().optional(),
    /** Smallest / largest acceptable pack, in the item's unit (kg, l or pieces). */
    minPackSize: z.number().positive().optional(),
    maxPackSize: z.number().positive().optional(),
    /** Set false to exclude retailer own-brand products. Default: allowed. */
    allowPrivateLabel: z.boolean().default(true),
  })
  .default({ allow: [], deny: [], allowPrivateLabel: true });
export type Rules = z.infer<typeof RulesSchema>;

export const MeasureSchema = z.enum(["kg", "l", "each"]);

export const ItemSchema = z.object({
  /** Stable key, e.g. "yoghurt". Used by CLI and MCP tools. */
  id: z.string().min(1),
  /** Human label. */
  name: z.string().min(1),
  /** Search text sent to the price provider. Defaults to `name`. */
  query: z.string().optional(),
  /** Unit that expected/actual/caps are expressed in. */
  unit: MeasureSchema,
  /** How much you want in the house at the start of a week. */
  expected: z.number().min(0),
  /** How much you actually have right now. */
  actual: z.number().min(0).default(0),
  rules: RulesSchema,
  /** Set true to skip this item in plans without deleting it. */
  paused: z.boolean().default(false),
  notes: z.string().optional(),
});
export type Item = z.infer<typeof ItemSchema>;

export const RetailerSettingSchema = z.object({
  /** Delivery / service fee in EUR. */
  fee: z.number().min(0).default(0),
  /** Fee is waived when the retailer subtotal reaches this amount. */
  freeAbove: z.number().min(0).optional(),
  /** Retailer is only used if its subtotal reaches this amount. */
  minBasket: z.number().min(0).optional(),
});
export type RetailerSetting = z.infer<typeof RetailerSettingSchema>;

export const SettingsSchema = z
  .object({
    /** Most retailers a plan may split across. */
    maxRetailers: z.number().int().min(1).max(10).default(2),
    /** Retailer ids to ignore completely. */
    excludeRetailers: z.array(z.string()).default([]),
    /** Per-retailer fee / minimum basket, keyed by retailer id as the provider reports it. */
    retailers: z.record(z.string(), RetailerSettingSchema).default({}),
    /** Prices older than this many days produce a warning. */
    maxPriceAgeDays: z.number().positive().default(7),
    /**
     * Allowed undershoot when rounding to whole packs: with 0.1, needing 1.08 packs buys 1 pack.
     * Set 0 to always round up.
     */
    packRoundingTolerance: z.number().min(0).max(0.5).default(0.1),
    /** Result pages (50 products each) to fetch per item search. */
    searchPages: z.number().int().min(1).max(5).default(1),
  })
  .default({
    maxRetailers: 2,
    excludeRetailers: [],
    retailers: {},
    maxPriceAgeDays: 7,
    packRoundingTolerance: 0.1,
    searchPages: 1,
  });
export type Settings = z.infer<typeof SettingsSchema>;

export const PantrySchema = z.object({
  version: z.literal(1).default(1),
  settings: SettingsSchema,
  items: z.array(ItemSchema).default([]),
});
export type Pantry = z.infer<typeof PantrySchema>;

/* ---------- Provider-facing normalised product model ---------- */

export interface RetailerOffer {
  retailer: string;
  retailerName: string;
  price: number;
  /** Provider-reported price per kg/l, if any (used only as a fallback). */
  unitPrice?: number | null;
  isDiscount: boolean;
  lastUpdated?: string | null;
}

export interface Product {
  id: string;
  name: string;
  brand: string | null;
  barcode: string | null;
  unit: string | null;
  unitQuantity: number | null;
  privateLabel: boolean;
  offers: RetailerOffer[];
}

export interface PriceProvider {
  readonly name: string;
  search(query: string, opts: { pages: number }): Promise<Product[]>;
}
