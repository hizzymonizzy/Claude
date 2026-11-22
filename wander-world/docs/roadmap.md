# Roadmap

Packages proposed by Claude as product lead, agreed with the player before each is built (see the working
agreement in `CLAUDE.md`). Items come from `docs/backlog.md`. Status: proposed → agreed → shipped.

## 0.10 Resources you can trust, banked where you earn them (shipped 0.10.0)
1. **Gathering is explained.** A per-walk gathering summary in the log: each place, its seconds in zone, whether it
   paid and why not.
2. **Wood as a line.** Park edges and paths through woods gather wood, like shores give food. Inside still counts.
3. **Food inland.** Farmland and orchard edges (lines); allotments and community gardens (nodes); farmers' markets
   move from metal to food; a little food from woodland paths.
4. **Readable nodes.** Icons twice the size; tap a node for its details (what it gives, how to gather it, state,
   regrowth, expected yield, distance).
5. **Proposal card shows the way.** What's missing and the nearest nodes for it, tap to fly there.
6. **Area banks** (see "Banks and connections" below).
7. **Reliability.** The morning run survives being stopped by Android (log why, resume safely). Place downloads
   back off less when you're standing somewhere new.
8. **Simulation** covers all five resources, an inland walk, and banks.

## 0.11 Villages you can see (shipped 0.11.0)
1. **One shared stock** again: remove area banks, networks, the split tip and per-area Atlas lists.
2. **Trader (NPC).** A travelling trader stands near the most recently visited village on the map (original sprite). Tap → shop: buy wood,
   food, stone, metal with gold at 2× base price.
3. **Market (shop).** A settlement's built market shows a market icon on the map. Tap → shop at base prices:
   stone 6, food 8, wood 10, metal 25 gold; each unit bought this week +10%, reset every Monday. Purchases are stored
   as events and replayed (DB v10).
4. **Cosmetics** in the same shop: capital banners (colours) and wanderer outfits (cloaks, hat). Original art.
   Looks only.
5. **Buildings on the map.** Each built project is drawn at its settlement as a 0 A.D. building badge (art through
   intake), arranged around the medallion.
6. **Upgrade chains:** campfire → meeting hall → town hall; well → fountain; market → market hall (better prices).
7. **The village fills in** (option A: the green stays walking-only). Development is drawn on top inside explored
   ground: a plaza that grows with tier and buildings, garden plots, worn paths.
8. **Building perks** (small): well/fountain → thinned sources in its area regrow 50% faster; campfire → +1 food
   per day you walk in its area; market/market hall → shop prices (base / −10%).
9. **Simulation** extended: buying and weekly price climb, shared stock, perks, the campfire bought into existence.
## 0.11.1 Safety and speed (shipped 0.11.1)
Review fixes shipped ahead of 0.12 so walk history is protected now: every walk's points kept, packed per walk after a
week (H1, DB v11); one coalesced rebuild lane with timing in diagnostics, redundant rebuilds removed (H2); fog only
recomputed when map data changes (H3); decoded tile geometry cached for the resource search (H4); duplicate presence
query removed (M3, part); stray doc comment removed (L1).

## 0.12 Heat map, trails and a faster engine (shipped 0.12.0)
Everything agreed since 0.11.0, plus the review's fixes (`docs/process/review-2026-11-21.md`).

**A. Trails and woods** (was 0.11.1)
1. Unwalked streets cut to explored ground and drawn visible on grass (light dashes, dark edge).
2. Wood from unpaved woodland trails, about 1 per km, double on a new trail; paved paths don't count.
3. Large parks, woods and reserves fetched one at a time instead of skipped; bounding box fallback.
4. One arrow per resource.

**B. Weekly cooling** (ADR 0008)
5. Monday tick: heat on roads, own paths, water routes and hexes cools by `L(r)`; recovery up to half of what was
   lost for 100–150% weeks; homecoming ×2; floor two steps below best; rest mode and dormancy as before.
6. Settlements on the same Monday tick (× sturdiness, tier floors unchanged). Agreed.
7. Monday dispatch: what faded and what recovered.

**C. Heat map** (ADR 0007)
8. Presence recorded at H3 res 12 (DB migration; every walk re-processed, all history kept).
9. Map drawn in four zoom levels: Block (res 12, faint grid), Neighbourhood (res 10/11, merged), County (res 8,
   towns spread by tier), Beyond (region nodes by tier). Five heat steps per ground type. Roads stay as they are.
10. Settlements as paved hexes (replaces the 0.11 plaza and gardens).

**D. Engineering** (review)
11–14. Shipped early in 0.11.1 (H1–H4). Remaining: cache decoded geometry in rebuild (M3), split `Repository.kt`
(M2), Overpass retry tests (M4), clean-ups L2–L4.

**Simulation and checks:** cooling over 1/2/4/8 missed weeks and recovery weeks; trail wood inland without a park
outline; rebuild time on a year of synthetic walks; map frame time on the Fold at each zoom level.

**Order of work** (each step passes `tools/check.sh`): B (cooling, engine and simulation), A, C, then the remaining D
items. One APK at the end. Settlement cooling confirmed by the player (2026-11-21).

## 0.13 Safe and sturdy (proposed)
Encrypted export/import of your world. (Map-server retry tests and the `Repository.kt` split moved into 0.12.)

## Later
Trade between networks and regions (trade posts at waystones); regional specialty goods; townspeople.

## Banks and connections (shipped in 0.10.0; removed in 0.11.0)
- **Where things go.** Wood, food, stone and metal you gather go into the bank of the **area** where you gathered
  them (the waypoint or settlement for that neighbourhood).
- **What connects.** Two areas share their banks only when a chain of roads and paths **you have walked** joins
  them. Joined areas form a network with one shared stock. Driving, flying and waystones never connect banks.
- **Spending.** A settlement's project draws from its network's stock. Developing a far area means gathering there,
  or walking the roads that join it to home.
- **Gold.** One purse for the whole world (agreed 2026-11-17).
- **History.** Replayed like everything else: past gathering is assigned to the area where it happened.

## Shared stock again (shipped 0.11.0)
The player found per-area banks harder to manage than they're worth. Proposal: one shared stock of all five
resources everywhere; remove area banks, networks, the split-stock tip and the per-network Atlas list. Gathering
rules (nodes, shores, fields, thinning, bonuses) stay as they are. Market perks in 0.11 need rethinking (they were
about trade between banks).

## Gold sinks: market and cosmetics (shipped 0.11.0)
Player: the market is a place to buy resources; gold needs things to be spent on, including cosmetics.
- **Market buying.** Once a settlement has a market, buy wood, food, stone and metal with gold. Prices rise with each
  unit bought in a week and reset weekly, so gathering stays the better path but gold is never stuck.
  Open: a travelling trader's cart at the capital before any market, at higher prices?
- **Cosmetics.** Spend gold on looks only: wanderer outfits, capital banner colours, map tints, medallion frames,
  avenue lanterns. No gameplay effect.
- Gold stays effort-earned (ADR 0005), so buying with it still rewards activity.
