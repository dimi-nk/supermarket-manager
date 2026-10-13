import assert from "node:assert/strict";
import { mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { CachedProvider } from "../src/providers/cached.js";
import { PosoKaneiProvider, toProduct } from "../src/providers/posokanei.js";
import type { PriceProvider, Product } from "../src/types.js";

const rawProduct = {
  id: 42,
  name: "ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ 1KG",
  brand: "ΔΕΛΤΑ",
  barcode: "5201",
  unit: "kg",
  unit_quantity: 1,
  private_label: false,
  retailer_prices: [
    { retailer: "sklavenitis", retailer_display_name: "Sklavenitis", price: 4.2, price_normalized: 4.2, is_discount: true, last_updated: "2026-10-12T06:00:00Z" },
    { retailer_display_name: "My Market", price: 4.1 },
    { retailer: "ab", price: undefined },
    { price: 3 },
  ],
};

test("toProduct maps PosoKanei fields and drops unusable offers", () => {
  const p = toProduct(rawProduct);
  assert.equal(p.id, "42");
  assert.equal(p.unitQuantity, 1);
  assert.equal(p.offers.length, 2);
  assert.deepEqual(p.offers[0], {
    retailer: "sklavenitis",
    retailerName: "Sklavenitis",
    price: 4.2,
    unitPrice: 4.2,
    isDiscount: true,
    lastUpdated: "2026-10-12T06:00:00Z",
  });
  assert.equal(p.offers[1]?.retailer, "My Market"); // falls back to display name when id is missing
});

test("search posts the documented request shape and pages until has_next is false", async () => {
  const calls: Array<{ url: string; init: RequestInit }> = [];
  const fetchImpl = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    const page = JSON.parse(String(init.body)).page as number;
    return new Response(JSON.stringify({ products: [{ ...rawProduct, id: page }], has_next: page < 2 }), { status: 200 });
  }) as unknown as typeof fetch;

  const provider = new PosoKaneiProvider({ fetchImpl, minIntervalMs: 0, baseUrl: "https://example.test/" });
  const products = await provider.search("γιαούρτι", { pages: 3 });

  assert.equal(calls.length, 2, "stops when has_next is false");
  assert.equal(calls[0]?.url, "https://example.test/products/search");
  assert.equal(calls[0]?.init.method, "POST");
  const body = JSON.parse(String(calls[0]?.init.body));
  assert.deepEqual(body, { title: "γιαούρτι", page: 1, page_size: 50, sort_by: "name", sort_order: "asc" });
  assert.deepEqual(products.map((p) => p.id), ["1", "2"]);
});

test("search surfaces HTTP errors with a rate-limit hint", async () => {
  const fetchImpl = (async () => new Response("slow down", { status: 429 })) as unknown as typeof fetch;
  const provider = new PosoKaneiProvider({ fetchImpl, minIntervalMs: 0 });
  await assert.rejects(() => provider.search("x", { pages: 1 }), /HTTP 429 \(rate limited/);
});

test("CachedProvider serves repeats from disk until the TTL expires", async () => {
  let hits = 0;
  const inner: PriceProvider = {
    name: "inner",
    async search(): Promise<Product[]> {
      hits++;
      return [toProduct(rawProduct)];
    },
  };
  const dir = await mkdtemp(join(tmpdir(), "pantry-cache-"));
  const cached = new CachedProvider(inner, dir, 60_000);
  await cached.search("q", { pages: 1 });
  const again = await cached.search("q", { pages: 1 });
  assert.equal(hits, 1);
  assert.equal(again[0]?.offers.length, 2);
  await cached.search("other", { pages: 1 });
  assert.equal(hits, 2);

  const noCache = new CachedProvider(inner, dir, 0);
  await noCache.search("q", { pages: 1 });
  await noCache.search("q", { pages: 1 });
  assert.equal(hits, 4);
});
