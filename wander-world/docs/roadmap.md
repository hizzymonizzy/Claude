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

## 0.11 Villages you can see (proposed for final review, 2026-11-21)
1. **One shared stock** again: remove area banks, networks, the split tip and per-area Atlas lists.
2. **Trader (NPC).** A travelling trader stands near the capital on the map (original sprite). Tap → shop: buy wood,
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
## 0.12 Safe and sturdy (proposed)
Encrypted export/import of your world; tests for map-server retry; split `Repository.kt`.

## Later
Trade between networks and regions (trade posts at waystones); regional specialty goods; townspeople.

## Banks and connections (shipped in 0.10.0; reversal agreed 2026-11-21, see below)
- **Where things go.** Wood, food, stone and metal you gather go into the bank of the **area** where you gathered
  them (the waypoint or settlement for that neighbourhood).
- **What connects.** Two areas share their banks only when a chain of roads and paths **you have walked** joins
  them. Joined areas form a network with one shared stock. Driving, flying and waystones never connect banks.
- **Spending.** A settlement's project draws from its network's stock. Developing a far area means gathering there,
  or walking the roads that join it to home.
- **Gold.** One purse for the whole world (agreed 2026-11-17).
- **History.** Replayed like everything else: past gathering is assigned to the area where it happened.

## Shared stock again (folded into 0.11)
The player found per-area banks harder to manage than they're worth. Proposal: one shared stock of all five
resources everywhere; remove area banks, networks, the split-stock tip and the per-network Atlas list. Gathering
rules (nodes, shores, fields, thinning, bonuses) stay as they are. Market perks in 0.11 need rethinking (they were
about trade between banks).

## Gold sinks: market and cosmetics (folded into 0.11)
Player: the market is a place to buy resources; gold needs things to be spent on, including cosmetics.
- **Market buying.** Once a settlement has a market, buy wood, food, stone and metal with gold. Prices rise with each
  unit bought in a week and reset weekly, so gathering stays the better path but gold is never stuck.
  Open: a travelling trader's cart at the capital before any market, at higher prices?
- **Cosmetics.** Spend gold on looks only: wanderer outfits, capital banner colours, map tints, medallion frames,
  avenue lanterns. No gameplay effect.
- Gold stays effort-earned (ADR 0005), so buying with it still rewards activity.
