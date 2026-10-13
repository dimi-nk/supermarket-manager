# HANDOVER: supermarket-manager (v2, Java/Spring Boot)

For an agent picking this up cold. State as of 2026-10-13.

## History

- v1 was a TypeScript CLI + MCP planner (pantry.json, PosoKanei provider, store optimiser). It is kept verbatim in
  `legacy/pantry-planner-ts/` and its handover is in `docs/HANDOVER-v1-typescript.md`.
- v2 (this repo) rewrites it in Java 25 / Spring Boot 4.1 as independent, chainable modules with SQLite, a React UI,
  MCP (Spring AI 2.0) and native desktop packaging. The user asked for:
  pantry management (target pantries over time, generic or concrete items, missing delta), optional price comparison,
  optional item rules (deny/allow, price ranges, one-brand), reverse-engineered supermarket APIs, an MCP server,
  modules that are independent and chainable, and free desktop downloads built on GitHub.

## What is verified

- `mvn verify`: 82 tests in 21 suites pass. They cover units, text, product matching, fill/knapsack, rules, delta and pantry
  services, the optimiser (ported hand-computed scenarios + brute-force bound), the PosoKanei client against a local stub,
  the AB contract test, export, pipeline integration over SQLite, and the whole app over HTTP (REST, MCP, the
  local-only guard, SPA routing).
  The frontend type-checks and builds.
- The jar runs in server and desktop-less modes. REST, the UI pages (no console errors), MCP Streamable HTTP
  (25 tools) and MCP stdio were all exercised manually.
- The AB live search works (172 candidates for the example pantry) and produced a sensible real basket.
- macOS `jpackage` app-image and DMG were built and the packaged app ran on this machine (Apple Silicon).

## What is NOT verified

1. **PosoKanei**: HTTP 403 from this machine for the API *and* the website (any User-Agent). Possibly geo/IP
   filtering or a WAF. Do not try to evade bot detection. Test from a Greek residential connection; if it works, record
   a response and add a contract test like `AbGrContractTest`.
2. **GitHub Actions release workflow**: written but never run. The Windows MSI step (WiX 5 via dotnet tool) is the
   riskiest, so it is `continue-on-error`, and the portable zip is always produced. Linux/Windows desktop mode (tray,
   browser opening) is untested.
3. Desktop mode (browser + tray) on macOS was not launched interactively, to avoid opening windows on the user's
   machine; only `--server` was run from the packaged app.

## Decisions (keep unless the user says otherwise)

- **Inclusive category targets**: a category target is a total; stock and targets below it count towards it (`DeltaCalculator`).
- **Word-prefix matching** for rules and queries (v1 used substring). "βιο" matches "βιολογικό" but not "αβιοτικό".
- **Query relevance filter** for generic lines: all query words must appear in brand + name. This was added after AB
  returned bread for "φέτα".
- **Mix policies** implement the user's "one brand only / can it mix" idea per line; the default is one product per line.
- Optimiser semantics are identical to v1 (see docs/ARCHITECTURE.md). Keep `OptimizerBruteForceTest` passing.
- Nothing logs in, fills carts or pays. "Record as bought" applies a run once (409 on repeat unless `force`).
- Local only: binds to 127.0.0.1; `LocalOnlyFilter` rejects non-loopback Host headers (DNS rebinding) and cross-site
  requests (Origin / Sec-Fetch-Site), since a web page could otherwise POST to e.g. /api/system/shutdown.
- Jackson 3 (Boot 4) rejects absent primitives by default; `fail-on-null-for-primitives: false` lets API clients omit
  optional flags (found by AppIntegrationTest).
- Deleting a category/product that targets or stock still use is refused.
- Licence: **MIT** (chosen by the user). Contributions require a DCO sign-off (CONTRIBUTING.md).

## Layout

```
modules/basket-core       contracts, Units/Text, LineFiller, CandidateFactory, PinnedOfferSource, CheapestPerLineResolver
modules/pantry            V100 migration; Catalog/Profile/Stock services; DeltaCalculator; CatalogCandidateSource; PantryTools
modules/item-rules        V200; RuleEngine (pure), RuleService, RuleTools
modules/price-comparison  V300; PosoKaneiProvider, DemoProvider (synthetic), CachedSearch, Optimizer (pure), StoreOptimizer
modules/supermarkets      AbGrAdapter (live), CartHandoff, BasketExport, SupermarketTools
modules/pipeline          V500; PipelineService (stage chain), runs, apply
modules/mcp-server        McpToolsConfig: all AgentTools beans -> MCP
app                       main + DataDir + DesktopLauncher + LocalOnlyFilter + ApiErrors + SPA forwarding; application.yml
frontend                  React/Vite; pages per module + Basket builder + Runs + Agents
packaging                 jpackage.sh, MakeIcons.java, icons/
.github/workflows         ci.yml (mvn verify), release.yml (3-OS jpackage on tag v*)
```

## Next steps (suggested)

1. Push to GitHub, run CI, then tag `v0.1.0` to exercise the release workflow and fix whatever the runners reveal.
2. Verify PosoKanei from Greece and replace guessed retailer ids in docs/examples with the real ones.
3. Pick features from docs/SIMILAR-PROJECTS.md (barcode + Open Food Facts, consumption-rate targets, price history, budget).
4. More adapters (Sklavenitis, My Market). Cart filling only on explicit request, with a confirmation step.

## Open questions for the user (carried over from v1, still open)

- Which chains do they shop at, delivery or pickup? This sets retailer fees and minimums in Price comparison → Retailer fees.
- Should the "best combination" weigh travel or number of stops beyond `maxRetailers`?
- Own-brand allowed by default? (Yes today; add a NO_PRIVATE_LABEL rule to change it.)
