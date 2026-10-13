import { readFile } from "node:fs/promises";
import { containsAllTokens } from "../text.js";
import type { PriceProvider, Product } from "../types.js";

/** Offline provider backed by a JSON file of `Product[]` — for tests, demos and dry runs. */
export class FixtureProvider implements PriceProvider {
  readonly name = "fixture";
  private products: Product[] | null = null;

  constructor(private readonly source: string | Product[]) {}

  private async load(): Promise<Product[]> {
    if (this.products) return this.products;
    this.products =
      typeof this.source === "string"
        ? (JSON.parse(await readFile(this.source, "utf8")) as Product[])
        : this.source;
    return this.products;
  }

  async search(query: string): Promise<Product[]> {
    const all = await this.load();
    return all.filter((p) => containsAllTokens(`${p.brand ?? ""} ${p.name}`, query));
  }
}
