# pantry-planner

A recurring "pantry" list for your weekly shop, with per-item rules, that finds the cheapest
combination of Greek supermarkets. Use it from the command line or let Claude drive it as an
MCP server.

**It reads prices only. It never logs in, builds a cart or places an order.**

## How it works

1. **Pantry** (`pantry.json`): each item has an `expected` quantity (what you want at the start
   of the week) and an `actual` quantity (what you have now). Need = expected − actual.
2. **Rules** per item:
   - `allow`: if non-empty, only products whose brand or name matches an entry.
   - `deny`: never buy products matching an entry. **Deny always beats allow.**
   - `maxUnitPrice`: hard cap in € per kg / l / piece. `maxPackPrice`, `minPackSize`,
     `maxPackSize` and `allowPrivateLabel: false` (no own-brand) also available.
3. **Planner** searches prices (PosoKanei, the government price database covering Sklavenitis,
   AB, My Market, Masoutis, Lidl, Kritikos, Market In, …), drops products that break the rules,
   rounds up to whole packs, then picks the cheapest split across up to `maxRetailers` stores,
   including delivery fees, free-delivery thresholds and minimum baskets.
4. After shopping, `received` adds the plan's quantities to your stock.

## Quick start

```bash
npm install && npm run build
export PANTRY_FILE=$PWD/pantry.json
node dist/cli.js add "Strained yoghurt" --id yoghurt --unit kg --expected 2 \
  --query "γιαούρτι στραγγιστό" --allow "ΔΕΛΤΑ,ΟΛΥΜΠΟΣ" --deny "ΒΙΟ" --max-unit-price 5
node dist/cli.js stock yoghurt=0.5      # weekly check: what I have now
node dist/cli.js search "γιαούρτι στραγγιστό"   # see brands + real unit prices before setting caps
node dist/cli.js plan
node dist/cli.js received               # after shopping
```

Try it offline with the bundled (synthetic) data:

```bash
PANTRY_PROVIDER=fixture PANTRY_FIXTURE=fixtures/products.json node dist/cli.js plan
```

`pantry.example.json` shows the file format; run `node dist/cli.js help` for all commands.

## Use with Claude (MCP)

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

Weekly flow with Claude: tell it what's left in the house, ask it to plan, review, then say
what you bought.

## Matching details worth knowing

- Matching ignores case and Greek accents (`Δέλτα` = `ΔΕΛΤΑ`). Entries match as **substrings** of
  the brand or product name, and all words in an entry must appear. `ΒΙΟ` therefore also matches
  any name containing "βιο" — use `search` to see what you're excluding.
- Use `gtin:5201234567890` or `id:123` for an exact barcode / product id.
- Pack sizes come from the provider's unit fields, falling back to the name (`2x200g`, `1,5L`,
  `500Γ`). Products whose size can't be read are skipped and counted in the "Not planned" reason.
- Whole packs: with the default `packRoundingTolerance` of 0.1, needing 1.08 packs buys 1.
  Set 0 to always round up.
- Items that can't be planned (nothing under your cap, everything denied, …) are listed with the
  reason and the closest offer, so you can tune the cap instead of guessing.

## Optimizer accuracy

Tested against exhaustive search on thousands of random baskets: exact without fees and with flat
fees. With free-delivery thresholds it uses a greedy improvement step (worst gap seen: about
€1.50, in under 1% of cases). Minimum baskets are respected by avoiding stores that fall below them.

## Caveats

- **The live PosoKanei provider is unverified.** It was written from the open-source
  `posokanei-mcp` package (which wraps the same undocumented public API the PosoKanei web app
  uses) and tested against a mocked `fetch`, because the build environment couldn't reach the API.
  Run `search` once on your machine to confirm it works. Undocumented endpoints can change.
- `fixtures/products.json` is **synthetic** test data, not real prices.
- PosoKanei shows shelf prices per chain, not per-store or online-shop prices; online prices and
  delivery charges can differ. Treat plans as a guide and check before paying.
- Requests are rate-limited and cached on disk (6 h by default, `PANTRY_CACHE_TTL_MIN`).

## Development

```bash
npm test         # 58 tests: rules, units, optimizer (incl. brute-force check), CLI, MCP, provider
npm run typecheck
```
