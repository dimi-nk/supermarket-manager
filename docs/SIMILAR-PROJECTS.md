# Similar projects and feature ideas

Research done 2026-10-13. Directory listings (Glama, mcp.so, AlternativeTo) were the main sources, so check each
repository's current state before reusing anything.

## Pantry and inventory

| Project | Relevant features |
|---|---|
| **Grocy** (MIT, self-hosted) | Stock transactions (purchase/consume/transfer), quantity-unit conversions, locations, best-before dates, a **minimum stock amount** per product with auto-add to the shopping list, **parent products** whose minimum accumulates over sub-products (the same idea as our inclusive category targets), price history, barcode support, REST API. Often described as powerful but fiddly. |
| **KitchenOwl** (AGPL) | Shared household lists, recipes → shopping list, multi-user, mobile apps. |
| **Mealie** | Recipe-first: URL import, meal plans → shopping lists. No pantry inventory. |
| **Pantrie**, **EverShelf**, **Pantry (netz-sg)** | Expiry tracking, barcode scanning with **Open Food Facts** lookup, locations, SQLite + REST. |
| Grocy MCP servers (`rusty4444/grocy-mcp`, `anishanilkumar/grocy-mcp`) | Agent tools for stock, product catalog, shopping list and barcode lookup. |

## Price comparison and basket optimisation

| Project | Relevant features |
|---|---|
| **posokanei-mcp** (MIT) | Read-only MCP over PosoKanei: search, barcode lookup, categories, retailers, price history, competitors, small-basket comparison. The source of our PosoKanei contract. |
| **e-katanalotis** | The earlier Greek government app (~1,000 products, price-drop alerts). PosoKanei replaces it. |
| **dutch-supermarkets-mcp** | `optimize_shopping_list` (split across the cheapest stores), `plan_store_route`, price alerts, **budgets**, meal planning, scheduled syncs every 12 h, PostgreSQL. |
| **Swiss groceries MCP** | Multi-store planner with **single-store / split-cart / cheapest** modes. These correspond to our `maxRetailers` and the store-count alternatives. |
| **Superprecio MCP** (Argentina) | Shopping-list optimisation across chains. |
| **SuperX** (Israel) | Compares a personal cart across chains using legally mandated price-transparency files. |
| **Pepesto** (paid) | Recipe/photo → matched basket of real products across 26 European supermarkets. |
| "Optimal" (Universidad Complutense) | Academic web app for shopping-list optimisation over the stores the user picks. |
| UK Supermarkets MCP, Coles/Woolworths MCP | Search and compare across a country's chains. |

## Retailer cart integrations

| Project | How |
|---|---|
| **ah-mcp** (Albert Heijn, AGPL) | Uses the retailer's mobile API: shopping list, adds bonus items to the cart. |
| **Shufersal MCP** (Israel) | Browser automation on a logged-in session to add to the cart. |
| **Kroger MCP** | Official API with OAuth; cart management. |
| **H-E-B MCP** | Product search, cart, coupons. |
| Apify `ab-gr-scraper` | Scrapes AB's own product API (the endpoint our AB adapter uses). |

## What this project already covers

Versioned target pantries (diet changes); generic and concrete targets with inclusive category roll-up; stock with an
event log; deny/allow, price, pack-size, retailer, offers-only and mix-policy rules scoped by category; a multi-source
candidate model; a multi-store optimiser with fees, free-delivery thresholds and minimum baskets; store-count
trade-offs; a hand-made basket path that skips any stage; live AB prices; MCP over HTTP and stdio; native desktop builds.

## Ideas worth adopting (rough priority)

1. **Verify PosoKanei from a Greek residential network.** It returned 403 from the build machine.
2. **Barcode scanning + Open Food Facts** (Pantrie, EverShelf, Grocy): scan with the phone camera in the web UI to create
   catalog products with EAN, brand and size. Barcodes make price matching exact.
3. **Consumption-rate suggestions** (Grocy-style history): the stock-event log already holds what is needed to suggest
   targets ("you use about 1.8 kg of yoghurt a week").
4. **Price history and alerts** (posokanei-mcp, e-katanalotis, dutch-supermarkets-mcp): store the offers seen in each
   run, answer "is this a good price?", and alert on drops for pinned products.
5. **Weekly budget cap** (dutch-supermarkets-mcp): optimise under a budget and drop or downgrade low-priority lines.
6. **Expiry / best-before dates** (Grocy, EverShelf) on stock entries, with use-first hints.
7. **Recipes / meal plans → temporary targets** (Mealie, KitchenOwl, Pepesto).
8. **Promotions and multi-buy** (`1+1`, AB `potentialPromotions`): model them in the fill step.
9. **More adapters**: Sklavenitis, My Market, Masoutis. Then **cart filling** with the user's session, behind an
   explicit confirmation (ah-mcp and Shufersal show the two approaches: mobile API and browser automation).
10. **Scheduled weekly run** with a notification (dutch-supermarkets-mcp syncs; a Claude scheduled task could call `pipeline_run`).
11. **Shared household access** (KitchenOwl): today the app is single-user and local only.
