import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { PriceProvider, Product } from "../types.js";

/** Wraps a provider with an on-disk cache so repeated plans don't hammer a public API. */
export class CachedProvider implements PriceProvider {
  readonly name: string;

  constructor(
    private readonly inner: PriceProvider,
    private readonly dir: string,
    private readonly ttlMs: number,
  ) {
    this.name = inner.name;
  }

  async search(query: string, opts: { pages: number }): Promise<Product[]> {
    if (this.ttlMs <= 0) return this.inner.search(query, opts);
    const key = createHash("sha1").update(`${this.inner.name}|${opts.pages}|${query}`).digest("hex");
    const file = join(this.dir, `${key}.json`);
    try {
      const cached = JSON.parse(await readFile(file, "utf8")) as { at: number; products: Product[] };
      if (Date.now() - cached.at < this.ttlMs) return cached.products;
    } catch {
      /* miss or unreadable cache entry: fall through to a fresh fetch */
    }
    const products = await this.inner.search(query, opts);
    try {
      await mkdir(this.dir, { recursive: true });
      await writeFile(file, JSON.stringify({ at: Date.now(), query, products }), "utf8");
    } catch {
      /* caching is best-effort */
    }
    return products;
  }
}
