# HANDOVER — pantry-planner

Read this first. It is written for a coding agent (Claude Code) picking up the project cold.
Everything here reflects the state at the end of the first session (2026-10-13).

## 1. What the user wants

A weekly-shopping assistant for Greece that Claude can operate through structured tools
(**not browser automation**):

- A recurring **pantry**: items with an **expected** quantity (target at start of week) and an
  **actual** quantity (what is in the house). Weekly top-up = expected − actual.
- **Per-item rules**: an allow list and/or deny list of brands/products, and **price limits**
  (their example: "never above €5 per kg of yoghurt").
- A script that compares **supermarket prices** and returns the **best combination of stores**
  for the week.
- Claude runs it (MCP tools), the user reviews, shops, then records what was bought.

Out of scope so far (and should stay so unless the user asks): logging in to retailers, filling
carts, placing orders, handling payment.

The user is a staff-level backend engineer (event-driven / data-intensive systems) based in
Greece. Be direct and technical; they also like diagrams where they help (see §3).

## 2. Research findings that shaped the design

- **No official public API/MCP** exists for efood, Wolt (consumer side) or the big chains
  (Sklavenitis, AB Vassilopoulos, My Market, Masoutis).
- **Wolt Drive API** exists but is merchant-only last-mile delivery (credentials via Wolt's
  Drive team). Not useful here.
- **Unofficial scrapers** exist on Apify for efood and ab.gr (the AB one hits ab.gr's own product
  API, same Ahold Delhaize platform as delhaize.be). None found for Sklavenitis / My Market / Masoutis.
- **PosoKanei** (posokanei.gov.gr) is the Greek government price-comparison platform launched in
  2026: ~10,000 products, daily updates, 10 chains (Galaxias, Sklavenitis, Chalkiadakis, SYNKA,
  Masoutis, My Market, AB Vassilopoulos, Kritikos, Market In, Lidl). Some chains feed data
  directly; others are crawled. This is the price source.
- **posokanei-mcp** (npm, MIT, v0.1.1, github.com/charistas/posokanei-mcp) is an unofficial
  read-only MCP over PosoKanei's undocumented API. Our provider was written from its source.
- **reverse-api-engineer** (github.com/kalil0321/reverse-api-engineer) turns captured HAR traffic
  into typed API clients — the suggested route if cart/ordering support is ever wanted.

## 3. Architecture

```
                     ┌──────────────────────────────────────────┐
  user / Claude ───► │  CLI (cli-core.ts)   MCP (mcp-server.ts) │   thin adapters
                     └───────────────┬──────────────────────────┘
                                     ▼
                              service.ts  (runPlan, applyLastPlan, searchProducts)
                                     │
      ┌──────────────────────────────┼───────────────────────────────┐
      ▼                              ▼                               ▼
 store.ts                        plan.ts                       providers/
 pantry.json                     buildPlan()                   posokanei.ts  (live, UNVERIFIED)
 (zod-validated,                   │                           fixture.ts    (offline JSON)
  atomic write)                    ├─ per item: provider.search(query)
                                   ├─ candidates.ts  buildOptions(): rules → packs → price caps
                                   │      rules.ts   allow/deny/private-label
                                   │      units.ts   pack size from API fields or product name
                                   └─ optimize.ts    choose cheapest store subset (≤ maxRetailers)
                                          │
                                          ▼
                                  Plan (best + bySize + per-item status + warnings)
                                          │
                       format.ts (text)   └─► last-plan.json ─► `received` / pantry_apply_last_plan
```

### Files

| File | Responsibility |
|---|---|
| `src/types.ts` | zod schemas (`Pantry`, `Item`, `Rules`, `Settings`) and the provider-facing `Product` / `RetailerOffer` / `PriceProvider` types |
| `src/text.ts` | `norm()` (lower-case, strip diacritics, fold final sigma ς→σ), `containsAllTokens()`, `roundMoney()` |
| `src/units.ts` | `parsePack()` (API unit+quantity), `parsePackFromName()` (`2x200g`, `1,5L`, `500Γ`), `resolvePack()` |
| `src/rules.ts` | `checkProductRules()` → `denied` / `not_allowed` / `private_label` / null |
| `src/candidates.ts` | `buildOptions()` (one `Option` per product×retailer that passes), diagnostics, `bestPerRetailer()` |
| `src/optimize.ts` | `optimize()` — subset search + fees + minBasket + greedy free-delivery improvement |
| `src/plan.ts` | `buildPlan()`, item statuses, warnings, `purchasesFromPlan()` |
| `src/store.ts` | `PantryStore` (load/save/update) + pure ops (`addItem`, `setActuals`, `addToActuals`, `editRules`, `needOf`, …) |
| `src/service.ts` | `runPlan` (saves last plan), `applyLastPlan`, `searchProducts`, `formatSearch` |
| `src/config.ts` | `createRuntime(env)` — wires store + provider (+ disk cache) from env vars |
| `src/providers/posokanei.ts` | live provider; serialised requests, 300 ms gap, 15 s timeout |
| `src/providers/cached.ts` | on-disk cache wrapper (sha1 of provider/pages/query; TTL) |
| `src/providers/fixture.ts` | offline provider over a `Product[]` JSON file |
| `src/cli-core.ts` / `cli.ts` | `runCli(argv, rt)` returns a string; `cli.ts` is the bin wrapper |
| `src/mcp-server.ts` / `mcp.ts` | `createServer(rt)` (11 tools); `mcp.ts` is the stdio bin wrapper |
| `src/format.ts` | plain-text rendering, phone-friendly |

### Runtime state (all next to `PANTRY_FILE`)

- `pantry.json` — the pantry (git-ignored; see `pantry.example.json`)
- `last-plan.json` — most recent plan + `appliedAt` (prevents double-apply)
- `.pantry-cache/` — provider search cache (default TTL 360 min)

Env: `PANTRY_FILE` (default `./pantry.json`), `PANTRY_PROVIDER=posokanei|fixture`,
`PANTRY_FIXTURE`, `PANTRY_CACHE_TTL_MIN`, `POSOKANEI_API_BASE`.

## 4. Behaviour you must preserve (decisions already made)

**Quantities and units.** Each item has one unit: `kg`, `l` or `each`. Provider units are
normalised (g→kg, ml→l, cl→l, τεμ/pcs→each). A product whose measure differs from the item's unit
is rejected (`wrongMeasure`). Unit price is **computed** as `price / packAmount`; the provider's
`price_normalized` is only used to raise a "check the pack size" warning when it differs by >30%.

**Whole packs.** `packs = max(1, ceil(need/pack − packRoundingTolerance))`, default tolerance 0.1
(needing 1.08 packs buys 1). Overshoot is allowed and is not penalised beyond its price.

**Rules.**
- Entries match brand **or** name, case/accent-insensitive, **substring** semantics, all words in an
  entry must be present. `gtin:<barcode>` and `id:<productId>` match exactly.
- Deny beats allow. Non-empty allow = whitelist. `allowPrivateLabel:false` drops own-brand.
- `maxUnitPrice` / `maxPackPrice` are **hard** caps (1e-9 tolerance). Offers over a cap are dropped
  per retailer, and the cheapest such offer is reported ("Closest: … €14/kg") so the user can tune.

**Optimiser.**
1. Per item, keep the cheapest acceptable option at each retailer (`bestPerRetailer`).
2. Enumerate every retailer subset of size ≤ `maxRetailers` (search pruned to the best 14
   retailers by coverage if more appear).
3. Per subset: each item goes to its cheapest retailer in the subset; fees apply per used store,
   waived when subtotal ≥ `freeAbove`; a store below `minBasket` is a violation.
4. Greedy local search lifts stores over their `freeAbove` threshold when that lowers total cost.
5. Ranking: fewer `minBasket` violations → fewer uncovered items → lower total (to the cent) →
   fewer stores → retailer-id order.
6. `bySize` reports the best plan allowed at most k stores for each k (deduplicated), so the user
   sees the store-count trade-off.

Measured against exhaustive search (`test/optimize-bruteforce.test.ts`): exact without fees or
with flat fees; with `freeAbove` thresholds it is a heuristic (~0.4% of random cases suboptimal,
worst gap ≈ €1.43). Keep that test passing; its threshold bound is ≤2% of cases and ≤ €2.

**Item statuses** in a plan: `planned`, `stocked` (need ≤ 0), `paused`, `unfulfilled` (nothing
acceptable — reason includes rejection counts), `left_out` (acceptable products exist but not
within the store limit).

**Safety.** Tools never place orders. `pantry_apply_last_plan` refuses to apply the same plan
twice unless `force`.

## 5. PosoKanei API contract as implemented

Base `https://api.posokanei.gov.gr` (undocumented; taken from `posokanei-mcp` v0.1.1).

- `POST /products/search` body `{ title, page, page_size (max 50), sort_by: "name"|"price"|"unit_price", sort_order }`
  → `{ products[], total, page, page_size, total_pages, has_next, has_prev }`
- Each product: `id, name, brand, barcode|gtin, unit, unit_quantity, private_label, retailer_prices[]`
- Each price: `retailer, retailer_display_name, price, price_normalized, is_discount, discount_percentage, last_updated`
- Also exist but are **not implemented** here: `GET /products/{id}`, `/products/barcode/{barcode}`,
  `/products/{id}/competitors`, `/meta/retailers`, `/meta/categories[/tree]`, `/meta/stats`,
  `/products?category=…&countries=GR`.

Only `search` is used. Its results already include `retailer_prices` (posokanei-mcp's basket
comparison relies on that too).

## 6. CRITICAL: what has NOT been verified

The first session ran in a sandbox whose proxy blocked `api.posokanei.gov.gr` (HTTP 403 on
CONNECT). Therefore:

1. **The live provider has never talked to the real API.** It is covered only by a mocked-`fetch`
   test of request shape and field mapping.
2. **Real values are unknown**: what `unit` strings the API returns (g? gr? τεμ? something else),
   how complete `unit_quantity` is, the retailer ids (`sklavenitis`, `ab`, `mymarket`, `masoutis`,
   `lidl` are what the **synthetic fixture** uses — they are guesses), whether `search` honours
   Greek queries with/without accents, and how `private_label` / `barcode` are populated.
3. `fixtures/products.json` is **synthetic** (invented prices, generated by a throwaway script).
   Do not treat its prices as real, and do not cite them to the user.

### Do this first on a machine with network access

```bash
npm install && npm run build && npm test
export PANTRY_FILE=$PWD/pantry.json
node dist/cli.js search "γιαούρτι στραγγιστό"
```

Then:
- Capture the raw JSON of a few searches (γιαούρτι, γάλα, αυγά, φέτα, ελαιόλαδο, μακαρόνια).
- Check every `unit` value against `UNIT_TABLE` in `src/units.ts`; extend it for anything missing.
  Check how many products fall into `unknownPack` / `wrongMeasure`.
- Record the real retailer ids and update README/examples/tests that mention fixture ids.
- Save a **small, trimmed real response** as `fixtures/recorded/*.json` and add a contract test that
  runs `toProduct()` over it, so API drift is caught.
- If the API rejects the User-Agent, rate-limits, or needs headers/cookies, fix
  `PosoKaneiProvider` (and keep requests polite: serialised, ≥300 ms apart, cached).

## 7. Known limitations / ideas (rough priority)

1. **Verify live provider** (above). Everything else depends on it.
2. **Retailer discovery**: add `pantry retailers` (`GET /meta/retailers`) so the user can set
   `fee` / `freeAbove` / `minBasket` / `excludeRetailers` with real ids.
3. **Search coverage**: only the first page(s) of a name-sorted search are read (`searchPages`,
   50 per page). Broad queries may miss products. Consider `sort_by: unit_price`, or pinning
   favourites by `gtin:`/`id:` and refreshing them via `GET /products/{id}`.
4. **Promotions**: `is_discount` is shown but otherwise ignored; multi-buy deals, loyalty-card prices
   and per-customer limits are not modelled. Prices are assumed available in any quantity.
5. **Price semantics**: PosoKanei reports chain shelf prices, not necessarily the online-shop price
   or delivery fees. The planner cannot know the user's delivery slots or minimums except through
   `settings.retailers`.
6. **Matching quirks**: substring matching means deny `ΒΙΟ` also hits words containing "βιο"; Latin
   vs Greek look-alike letters (`DELTA` vs `ΔΕΛΤΑ`) are not unified; brand aliases don't exist.
   A `pantry explain <item>` command listing why each candidate was kept/dropped would help tuning.
7. **Staleness** is warning-only (`maxPriceAgeDays`); stale prices are still used.
8. **Concurrency**: `PantryStore.update` is read-modify-write, last writer wins. Fine for one user;
   add file locking if the CLI and MCP server are used simultaneously and that becomes real.
9. **Scheduling**: a weekly Claude Code scheduled task could run `plan` and notify the user.
10. **History**: no price/purchase history is stored. A small append-only log would enable
    "is this a good price?" and consumption-rate suggestions (expected vs actual drift).
11. **Ordering/cart-building** (only if the user explicitly asks): AB first (its API is the
    Ahold Delhaize platform). Would need login, bot-detection handling (ab.gr runs Dynatrace) and
    care around ToS/payment. Use reverse-api-engineer on captured HAR. Keep a human confirmation
    step before anything is submitted.

## 8. Working in the repo

- Node ≥ 20 (built on 22). TypeScript **7.0.2**, zod **4.6.7**, `@modelcontextprotocol/sdk` **1.32.1**,
  `tsx` for tests, `node:test` runner. ESM with `.js` import suffixes (NodeNext).
- `tsconfig.json` needs `"types": ["node"]` — TypeScript 7 does not auto-include `@types`.
- zod 4 note: `.default({...})` returns the literal as-is without re-parsing, so defaults for nested
  objects (`RulesSchema`, `SettingsSchema`) are spelled out in full. If you add a field there, update
  the default literal too.
- Commands: `npm test` (58 tests), `npm run typecheck`, `npm run build`, `npm run cli -- <args>`,
  `npm run mcp`.
- Tests: `test/optimize.test.ts` (hand-made scenarios), `test/optimize-bruteforce.test.ts`
  (seeded exhaustive comparison), `test/plan.test.ts` (end-to-end on fixtures; expected totals were
  computed by hand — if you change fixture prices, recompute them), `test/cli.test.ts`,
  `test/mcp.test.ts` (in-memory MCP client), `test/provider.test.ts` (mocked fetch + cache),
  `test/store.test.ts`, `test/rules.test.ts`, `test/units.test.ts`.
- Fixture generator was a throwaway script; the JSON is the source of truth now. Pack size for one
  fixture product (`ΜΑΚΑΡΟΝΙΑ … 500Γ`) is deliberately only in the name, and one Lidl price has a
  deliberately wrong `unitPrice` to exercise the mismatch warning.
- Not a git repo yet. If you `git init`, `.gitignore` already excludes `node_modules`, `dist`,
  `.pantry-cache`, `last-plan.json`, `pantry.json`.

### Wiring it into Claude (MCP)

```json
{
  "mcpServers": {
    "pantry": {
      "command": "node",
      "args": ["/absolute/path/to/pantry/dist/mcp.js"],
      "env": { "PANTRY_FILE": "/absolute/path/to/pantry.json" }
    }
  }
}
```

Tools: `pantry_list`, `pantry_add_item`, `pantry_set_stock`, `pantry_add_stock`,
`pantry_set_expected`, `pantry_edit_rules`, `pantry_set_item_state`, `pantry_update_settings`,
`pantry_search_products`, `pantry_plan`, `pantry_apply_last_plan`.

Weekly loop: user states what is left → `pantry_set_stock` → `pantry_plan` → user shops →
`pantry_apply_last_plan` (with `skip` for anything not bought).

## 9. Open questions for the user

- Which chains do they actually shop at, and do they use delivery or in-store pickup? (Determines
  fees/minimums/`excludeRetailers` and whether store count matters.)
- Is "best combination" purely cheapest total, or should travel/time/number of stops be weighted?
- Should unit-priced caps ever be per-pack for some items (e.g. eggs)? Both exist; confirm defaults.
- Do they want own-brand products allowed by default? (Currently yes.)
- Are Greek search terms in `query` fine, or should English item names map automatically?
