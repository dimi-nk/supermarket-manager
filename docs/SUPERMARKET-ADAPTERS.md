# Supermarket adapters

An adapter implements `SupermarketAdapter` (modules/supermarkets) and becomes a candidate source
`supermarket:<id>` via a bean in `SupermarketCandidateSources`.

## AB Vassilopoulos (implemented, search only)

`POST https://www.ab.gr/api/v1/` with an ad-hoc GraphQL query (no persisted-query hash needed):

```graphql
query GetProductSearch($lang:String,$searchQuery:String,$pageNumber:Int,$pageSize:Int) {
  productSearch(lang:$lang, searchQuery:$searchQuery, pageNumber:$pageNumber, pageSize:$pageSize) {
    products { code name manufacturerName url
      price { value unitPrice supplementaryPriceLabel2 wasPrice showStrikethroughPrice }
      stock { inStock } potentialPromotions { code description } }
    pagination { totalResults totalPages }
  }
}
```

Notes from the live API (2026-10-13):
- `price.unitPrice` repeats the pack price, so it is ignored. Pack size comes from `supplementaryPriceLabel2`
  (`600 γρ`, `1 κιλ`, `1 λιτ`) or from the name.
- There is no EAN field. Brands are `manufacturerName` (Fage products appear as `TOTAL`, own brand as `ΑΒ`).
- Fields like `ean` and `isProductOnPromotion` do not exist, and the schema rejects them with `GRAPHQL_VALIDATION_FAILED`.
- The honest User-Agent `supermarket-manager/0.1` is accepted. Requests are throttled to ≥500 ms apart.

`AbGrContractTest` runs the mapping over a recorded response (`src/test/resources/recorded/`). Re-record it when the API changes.

## Adding a retailer

1. Open the retailer's e-shop with the browser dev tools and search for a product. Find the JSON/GraphQL request
   that returns products and prices. Prefer anonymous, read-only endpoints.
2. Implement `search()` and map each product to `Product` with exactly one `RetailerOffer`. Fill `unit` and
   `unitQuantity` when the API has them.
3. Record a trimmed real response under `src/test/resources/recorded/` and add a contract test.
4. Register `new AdapterSource(adapter, search)` as a `CandidateSource` bean.

Candidates with no adapter yet: Sklavenitis, My Market, Masoutis, Kritikos, Lidl. PosoKanei already covers their prices.

## Cart filling (not implemented)

`pushCart(StoreBasket, session, dryRun)` is the extension point. Filling a real cart needs:
- the user's own logged-in session (cookie/token supplied by the user, never collected by the app);
- the add-to-cart request captured from a logged-in HAR, for example with
  [reverse-api-engineer](https://github.com/kalil0321/reverse-api-engineer);
- awareness that ab.gr runs bot detection (Dynatrace), and a check of the retailer's terms of use;
- a human confirmation step. The app must never submit an order or payment.

Until then, `CartHandoff` gives per-store product links and pack counts for adding items by hand.
