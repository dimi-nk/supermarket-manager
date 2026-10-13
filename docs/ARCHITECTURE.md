# Architecture

## Contracts (basket-core)

Every stage consumes and produces one of three documents. That is what makes the modules chainable and lets
each one be used alone.

| Document | Produced by | Consumed by |
|---|---|---|
| `Basket`: lines to buy (generic or concrete; optionally with a known price) | pantry delta, or you | candidate sources |
| `CandidateSet`: per line, purchasable `Candidate`s (product × retailer × pack size × price) + `Diagnostics` + `MixPolicy` | candidate sources, item rules | item rules, resolvers |
| `ResolvedBasket`: per store, lines filled with whole packs; unresolved lines; alternatives by store count; warnings | resolvers | export, "record as bought" |

SPIs: `CandidateSource` (`id()` like `price:posokanei`), `BasketResolver` (`optimizer`, `cheapest-per-line`) and
`AgentTools` (marker for beans with `@Tool` methods). The pipeline discovers implementations as Spring beans, so a
new provider or supermarket adapter is one class plus a bean.

`BasketLine` fields that matter downstream:
- `query` is the search text. For generic lines, every query word must start a word of the product's brand or name
  (`CandidateFactory`), because retailer search is fuzzy ("φέτα" also returns "φέτες" bread).
- `product` (`ProductRef`) makes a line concrete. Matching order: barcode, then (source, id), then brand + non-numeric
  name words. The pack size must match within 5%.
- `categoryId` / `categoryPath` scope the item rules.
- `offer` is a known price; the `basket` source turns it into the only candidate.

## Filling a line (LineFiller)

- `SINGLE_PRODUCT` (default): `packs = max(1, ceil(need/pack − tolerance))` of the cheapest single product (tolerance 0.1:
  needing 1.08 packs buys 1).
- `SINGLE_BRAND` / `ANY`: an unbounded knapsack (minimum cost to reach the need) over that brand's or all products, on a
  5 g / 5 ml / 1-piece grid. Example: 1.5 kg as 1 kg + 0.5 kg when that is cheaper than 2 × 1 kg.
- Unit prices are always computed as price / pack size. A provider's own unit price only raises a "check the pack
  size" warning when it differs by more than 30%.

## Optimizer (price-comparison)

Ported from the TypeScript planner. Behaviour is unchanged and covered by the same hand-computed scenarios and brute-force bounds:
1. Per line and retailer, the cheapest fill (above).
2. Every retailer subset of size ≤ `maxRetailers` (pruned to the 14 best-covering retailers).
3. Within a subset each line goes to its cheapest store; fees apply per used store, waived at `freeAbove`.
4. A greedy pass lifts stores over their free-delivery threshold when that lowers the total.
5. Ranking: fewer minimum-basket violations → fewer uncovered lines → lower total (to the cent) → fewer stores → ids.

The result is exact without fees or with flat fees. With thresholds it is a heuristic (`OptimizerBruteForceTest`: ≤2% of
random cases suboptimal, gap ≤ €2).

## Item rules

All rules that apply to a line (scope: everything, a category and everything below it, or one line id) are combined:
deny beats allow, every applicable ALLOW rule must match, and price/pack ranges are hard limits. The cheapest offer
rejected only for its price is reported, so you can tune limits. For `MIX_POLICY`, the most specific scope wins.

## Persistence

SQLite in the data folder, WAL mode, so the UI process and a `--mcp-stdio` process can share it. Each module owns its
tables (`pantry_*`, `rules_*`, `prices_*`, `pipeline_*`) and migrations (`db/migration/<module>/V1xx…`), and Flyway
runs with `out-of-order` enabled. Stock changes are append-only events (`pantry_stock_event`), which keeps price and
consumption history available for future features.

## Safety

- Binds to 127.0.0.1. `LocalOnlyFilter` rejects non-loopback `Host` headers (DNS rebinding).
- Nothing logs in to a retailer, fills a cart or pays. "Record as bought" is idempotent per run (`ConflictException` on repeat).
