# Backlog

Candidates for upcoming packages. Each release is planned from here and agreed with the player before building
(see the working agreement in `CLAUDE.md`). Status: proposed → agreed → shipped (version).

## Open questions from field use
| Item | Why | Status |
|---|---|---|
| Daily dispatch run cancelled twice on 11-17 ("Daily run failed: Job was cancelled") | Two workers overlapping; the dispatch still went out, but health sync was cut short | proposed |
| Overpass back-off reaches 30 min while you're standing somewhere new | Pointers and places stay empty for a long time after a 502/429 | proposed |
| Places that are only nodes in OSM (a church, a fire station) | Their 35 m circle may sit off the building; check against walks | proposed |

## Next package focus: resources (player, 2026-11-17)
The player wants resources to be the next meaningful thing developed. Plan that package around a full
review of all five resources: sources, rates, how each is shown, gathered and spent.

| Item | Why | Status |
|---|---|---|
| More food sources, especially for landlocked areas | Food now comes only from shores, paddling and harbours; inland players can't earn it | proposed |
| Ideas to discuss for food: farmland and orchards (walk along field edges, a line source like shores); allotments and community gardens (nodes); farmers' markets (nodes, today counted as civic → metal); foraging along trails through woods (a little food alongside wood) | Every region should have some food | proposed |

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
