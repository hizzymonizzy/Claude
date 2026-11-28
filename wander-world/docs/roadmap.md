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

## 0.13 Villages with a story (shipped 0.13.0)
Everything held since 0.12.2 (backlog sections of 2026-11-23), in this order, one APK at the end:
1. **Health goal** (research report `docs/research/reports/`): the 150-minute guideline decides cooling (<75 ¼ step,
   75–119 ⅛, 120–149 1/40, 150+ holds); the adaptive goal only decides warming (at or over it: +¼ step). Goal moves in
   ~10% steps between 150 and 300 (bands Stroller/Rambler/Trekker/Pathfinder name ranges; highest band kept as a
   title); only real weeks count; rises at most 10% per 4 weeks, eases after 2 light weeks; Tuesday explains changes;
   lock or pick a band. Rest mode with presets and a 3-week ramp back. Calm, seasonal wording. (No cooling-off setting
   and no welcome-back bonus: player's choice.)
2. **Camps by overnight stay only** (a 3 am location check, nothing leaves the phone); capital stays at home.
3. **Waystones merge** within ~1 km (arrivals counted); existing duplicates merged.
4. **Development story:** 3–5 projects per tier with perks, the Wonder; encounters anywhere; rare-look pool and feats;
   achievements; trader requests.
5. **Screens:** JRPG shop; Atlas (settlements and camp-able areas, distance, Go); character sheet; achievements;
   settings in tabs with Credits; Goal screen bug fixed.
6. **Map:** icons ~2×; settlement hex medallions; resource hex badges and arrows; buildings as developed hexes at close zoom.

## 0.14 On every walk (shipped 0.14.0)
Agreed 2026-11-25: improve the walks the player does now, near home; camps wait until they start camping.
1. **Live gathering.** Every 5 minutes during a walk, the walk so far is processed in memory and the world rebuilt with
   it: stock, the request card and gathered nodes follow you, and the walk notification says what you've gathered
   ("+3 wood · +2 food · 2 places"). Nothing is stored; the end-of-walk processing replaces it and wins. No watch buzz.
2. **Road snap at 35 m** and **own-path merge** (20 m, beach-depth aware, 40 m max); every walk re-processed once
   (PROCESSING_VERSION 12). Per-walk `[paths]` diagnostics line: fixes on streets, snapped gaps, unmatched stretches.
3. **Map placement:** the worn banner is part of the capital's hex picture; buildings take the hexes touching the
   settlement, skipping street hexes; edge pointers stack.
4. **Atlas:** capital card with hex art and building badges, request card shows the perk, no trader line, no "Places
   you could camp", settlement rows that don't overlap.

## 0.14.1 Your taps, in order (shipped 0.14.1)
Field bug: Approve did nothing after a trade the same day. A day's purchases, trades and approvals now replay in the
order you tapped them, after the day's walking; every tap says what happened. Tap a building to see what it does.

## 0.14.2 Roads you can see (shipped 0.14.2)
Roads, own paths and water routes drawn at real width with a material per stage (mockup `docs/art/mockup_roads.png`).

## 0.15 Camps by stays, settlements you can see (shipped 0.15.0)
Player's choice: both in one release.
1. Camps per spot, grown by stays (1/3/10/30/100), reach by tier (2–6 neighbourhood hexes), merging within reach.
2. Night check at 3 am local to the zone you're in, rescheduled on a time-zone change (trip in mid-December).
3. Settlements drawn as buildings rendered by us from 0 A.D.'s 3D models (bird's-eye 35°): houses fill the paved
   hexes as they grow, projects stand on their hexes.
4. Culture per region (Britons, Romans, Persians), switchable at the trader.

## Next (proposed)
Regional specialties and trade; more cultures from 0 A.D.; richer city growth (streets and districts filling in).

## Later
Encrypted export/import of your world (player: low value while in alpha). Trade between networks and regions (trade
posts at waystones); regional specialty goods; townspeople.

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
