import type { PriceProvider, Product, RetailerOffer } from "../types.js";

/**
 * PosoKanei (posokanei.gov.gr) provider.
 *
 * Endpoints and field names are taken from the open-source `posokanei-mcp` package (v0.1.1),
 * which wraps the same undocumented public API the PosoKanei web app uses. NOT verified live
 * from the build environment (network allowlist) - run `pantry search` once to confirm it works.
 * Undocumented endpoints can change without notice.
 */

const DEFAULT_BASE = "https://api.posokanei.gov.gr";
const PAGE_SIZE = 50;

interface RawRetailerPrice {
  retailer?: string;
  retailer_display_name?: string;
  retailer_name?: string;
  price?: number;
  price_normalized?: number;
  is_discount?: boolean;
  last_updated?: string | null;
}

interface RawProduct {
  id: string;
  name: string;
  brand?: string | null;
  barcode?: string | null;
  gtin?: string | null;
  unit?: string | null;
  unit_quantity?: number | null;
  private_label?: boolean;
  retailer_prices?: RawRetailerPrice[];
}

interface RawSearchResponse {
  products?: RawProduct[];
  has_next?: boolean;
}

export interface PosoKaneiOptions {
  baseUrl?: string;
  minIntervalMs?: number;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
}

export class PosoKaneiProvider implements PriceProvider {
  readonly name = "posokanei";
  private readonly base: string;
  private readonly minIntervalMs: number;
  private readonly timeoutMs: number;
  private readonly fetchImpl: typeof fetch;
  private lastRequestAt = 0;
  private queue: Promise<unknown> = Promise.resolve();

  constructor(opts: PosoKaneiOptions = {}) {
    this.base = (opts.baseUrl ?? process.env.POSOKANEI_API_BASE ?? DEFAULT_BASE).replace(/\/$/, "");
    this.minIntervalMs = opts.minIntervalMs ?? 300;
    this.timeoutMs = opts.timeoutMs ?? 15_000;
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  async search(query: string, opts: { pages: number }): Promise<Product[]> {
    const out: Product[] = [];
    for (let page = 1; page <= opts.pages; page++) {
      const res = await this.post<RawSearchResponse>("/products/search", {
        title: query,
        page,
        page_size: PAGE_SIZE,
        sort_by: "name",
        sort_order: "asc",
      });
      for (const raw of res.products ?? []) out.push(toProduct(raw));
      if (!res.has_next) break;
    }
    return out;
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    // Serialise requests and keep a minimum gap between them to stay polite to a public API.
    const run = this.queue.then(async () => {
      const wait = this.lastRequestAt + this.minIntervalMs - Date.now();
      if (wait > 0) await new Promise((r) => setTimeout(r, wait));
      this.lastRequestAt = Date.now();
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), this.timeoutMs);
      try {
        const response = await this.fetchImpl(`${this.base}${path}`, {
          method: "POST",
          headers: {
            accept: "application/json",
            "content-type": "application/json",
            "user-agent": "pantry-planner/0.1 (personal use)",
          },
          body: JSON.stringify(body),
          signal: controller.signal,
        });
        const text = await response.text();
        if (!response.ok) {
          const hint = response.status === 429 ? " (rate limited - try again later)" : "";
          throw new Error(`PosoKanei ${path} failed: HTTP ${response.status}${hint} ${text.slice(0, 200)}`);
        }
        return JSON.parse(text) as T;
      } catch (err) {
        if (err instanceof Error && err.name === "AbortError") {
          throw new Error(`PosoKanei ${path} timed out after ${this.timeoutMs}ms`);
        }
        throw err;
      } finally {
        clearTimeout(timer);
      }
    });
    this.queue = run.catch(() => undefined);
    return run as Promise<T>;
  }
}

export function toProduct(raw: RawProduct): Product {
  const offers: RetailerOffer[] = [];
  for (const p of raw.retailer_prices ?? []) {
    const retailer = p.retailer ?? p.retailer_display_name ?? p.retailer_name;
    if (!retailer || typeof p.price !== "number") continue;
    offers.push({
      retailer,
      retailerName: p.retailer_display_name || p.retailer_name || retailer,
      price: p.price,
      unitPrice: p.price_normalized ?? null,
      isDiscount: p.is_discount ?? false,
      lastUpdated: p.last_updated ?? null,
    });
  }
  return {
    id: String(raw.id),
    name: raw.name,
    brand: raw.brand ?? null,
    barcode: raw.barcode ?? raw.gtin ?? null,
    unit: raw.unit ?? null,
    unitQuantity: raw.unit_quantity ?? null,
    privateLabel: raw.private_label ?? false,
    offers,
  };
}
