# Backlog

Candidates for upcoming packages. Each release is planned from here and agreed with the player before building
(see the working agreement in `CLAUDE.md`). Status: proposed → agreed → shipped (version).

## Open questions from field use
| Item | Why | Status |
|---|---|---|
| Daily dispatch run cancelled twice on 11-17 ("Daily run failed: Job was cancelled") | Two workers overlapping; the dispatch still went out, but health sync was cut short | shipped 0.10.0 |
| Overpass back-off reaches 30 min while you're standing somewhere new | Pointers and places stay empty for a long time after a 502/429 | shipped 0.10.0 |
| Places that are only nodes in OSM (a church, a fire station) | Their 35 m circle may sit off the building; check against walks | proposed |

## Next package focus: resources (player, 2026-11-17)
The player wants resources to be the next meaningful thing developed. Plan that package around a full
review of all five resources: sources, rates, how each is shown, gathered and spent.

| Item | Why | Status |
|---|---|---|
| More food sources, especially for landlocked areas | Food now comes only from shores, paddling and harbours; inland players can't earn it | shipped 0.10.0 |
| Ideas to discuss for food: farmland and orchards (walk along field edges, a line source like shores); allotments and community gardens (nodes); farmers' markets (nodes, today counted as civic → metal); foraging along trails through woods (a little food alongside wood) | Every region should have some food | proposed |

| Wood not credited after a walk past or through a park (0.9.6/0.9.7, node still "new") | Blocks the campfire (5 wood); needs diagnostics to confirm cause | shipped 0.10.0 |
| Per-walk gathering summary in the log: each place's seconds in zone and whether it paid | We can't tell from today's log why a node didn't pay | shipped 0.10.0 |
| Wood as a line source too: park edges and paths through woods | Walking beside a park currently pays nothing (wood needs you inside) | shipped 0.10.0 |
| Bigger resource icons; tap a node for details (name, what it gives, how to gather, state, regrowth) | Icons too small to read; no way to learn what a node is | shipped 0.10.0 |
| Starter projects affordable within a walk or two anywhere | Campfire's 5 wood has blocked the quest loop since day one | proposed |

## Villages that show on the map (player idea, 2026-11-17)
| Item | Why | Status |
|---|---|---|
| Built projects appear on the map at their settlement (campfire, well, market…), as 0 A.D.-style sprites | Villager requests should visibly change the world; buildings are now hidden in explored land | proposed |
| Upgrade chains: campfire → meeting hall → town hall, etc. | Progression you can see | proposed |
| Infrastructure grows the green: each built project widens the cleared, developed ground around the settlement | "The green fills in as the infrastructure develops around it" | proposed |

## Gameplay
| Item | Why | Status |
|---|---|---|
| Wood along wooded paths (a "trail through woods" line source), like food along shores | Woods often have paths through them; matches how stone and food work now | proposed |
| Projects you can actually afford: show what's missing and where to get it (links to nodes) | Campfire has been waiting since day one | proposed |
| Trade between settlements once two have markets | From the economy design, deferred in 0.7.0 | proposed |
| Regional specialty goods (coast → fish, woods → timber) | Makes regions feel different | proposed |
| Live gathering credit (count during the walk, not only at the end) | The ✓ is live, but the bar updates only when a walk ends | proposed |

## Map and art
| Item | Why | Status |
|---|---|---|
| Building footprints in explored ground as 0 A.D.-style roofs by settlement tier | Explored land now hides base-map buildings | proposed |
| Atlas: list of today's gathered nodes and where the next best ones are | Visibility of the economy | proposed |
| Encrypted export/import of your world | No backup by design; protects against a lost phone | proposed |

## Tech debt
| Item | Status |
|---|---|
| Split `Repository.kt` (processing, rebuild, diagnostics, resources) | proposed |
| Tests for Overpass network retry/back-off | proposed |
