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

## 0.11 Villages you can see (proposed)
1. Built projects drawn at their settlement as 0 A.D.-style buildings (art through intake).
2. Upgrade chains: campfire → meeting hall → town hall; well → fountain; market → market hall.
3. Buildings augment their area (perks), e.g. a well speeds regrowth of nearby sources; a market lets its bank trade
   along connected roads at full rate.
4. Settlement "development" drawn inside the settlement: plaza, gardens, worn ground. The green (explored ground)
   stays walking-only (option A, agreed 2026-11-17).

## 0.12 Safe and sturdy (proposed)
Encrypted export/import of your world; tests for map-server retry; split `Repository.kt`.

## Later
Trade between networks and regions (trade posts at waystones); regional specialty goods; townspeople.

## Banks and connections (draft for 0.10)
- **Where things go.** Wood, food, stone and metal you gather go into the bank of the **area** where you gathered
  them (the waypoint or settlement for that neighbourhood).
- **What connects.** Two areas share their banks only when a chain of roads and paths **you have walked** joins
  them. Joined areas form a network with one shared stock. Driving, flying and waystones never connect banks.
- **Spending.** A settlement's project draws from its network's stock. Developing a far area means gathering there,
  or walking the roads that join it to home.
- **Gold.** One purse for the whole world (agreed 2026-11-17).
- **History.** Replayed like everything else: past gathering is assigned to the area where it happened.
