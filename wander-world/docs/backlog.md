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
| Built projects appear on the map at their settlement (campfire, well, market…), as 0 A.D.-style sprites | Villager requests should visibly change the world; buildings are now hidden in explored land | shipped 0.11.0 |
| Upgrade chains: campfire → meeting hall → town hall, etc. | Progression you can see | shipped 0.11.0 |
| Infrastructure grows the green: each built project widens the cleared, developed ground around the settlement | "The green fills in as the infrastructure develops around it" | shipped 0.11.0 as option A (plaza, gardens, paths drawn on explored ground; the green stays walking-only) |

## Gameplay
| Item | Why | Status |
|---|---|---|
| Back to one shared stock (remove area banks) | Player: easier to manage than counts per territory | shipped 0.11.0 |
| Market: buy resources with gold (weekly rising prices) | Player: gold needs uses; market becomes a shop | shipped 0.11.0 |
| Cosmetics bought with gold (outfits, banners, map tints, frames) | Player: a gold sink with no gameplay effect | banners and outfits shipped 0.11.0; tints and frames proposed |
| Wood along wooded paths (a "trail through woods" line source), like food along shores | Woods often have paths through them; matches how stone and food work now | shipped 0.12.0 |
| Projects you can actually afford: show what's missing and where to get it (links to nodes) | Campfire has been waiting since day one | shipped 0.11.0 |
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

## Heat map (2026-11-21)
| Item | Why | Status |
|---|---|---|
| Heat per hex, four zoom levels, five steps (ADR 0007) | The world is a heat map; one rule for how ground looks | shipped 0.12.0 |
| Weekly cooling with recovery, floor two steps below best (ADR 0008) | Decay was too slow to feel; weeks should matter | shipped 0.12.0 |
| Settlement decay on the same weekly tick | One rule for everything | shipped 0.12.0 |
| Settlement tier from heat clusters | Fewer counters | proposed |
| Upgrades that raise heat floors or slow cooling | Later option: fade to step 1, with upgrades to protect places | proposed |

## Hex icons and developed cells (player, 2026-11-23; held for more feedback)
| Item | Why | Status |
|---|---|---|
| Icons about 2× (buildings, banner, trader, resources), still easing down when zoomed out | Fire, banner and icons read at about half the size they should | agreed |
| Buildings claim a block hex around their settlement and draw it as a developed cell (own ground + building art; upgrades change it) — at close zoom only, badges further out | "Unique cell development" | agreed |
| Settlement medallions become hex-shaped | Consistent with the hex map | agreed |
| Resource nodes and edge arrows as hex badges (ring colours become hex outlines) | Consistent with the hex map | agreed |

## Shop, JRPG-style (player, 2026-11-23; held for more feedback; mockup docs/art/mockup_shop.png)
| Item | Why | Status |
|---|---|---|
| ✕ close top right; Wares / Looks tabs; framed parchment windows with a ▶ cursor on the selected row | JRPG menu feel; close at the bottom was awkward | agreed |
| No rules text: prices, totals and greyed buttons show the mechanics (2×, market hall, weekly climb) | Brown mechanics text was overkill | agreed |
| Quantity: − / + stepper, tap the number to type it (pad with Max), total in gold, Buy | Buy any amount, not just 1 or 5 | agreed |
| One-line flavour text for each item (MMORPG-tooltip tone); none elsewhere for now | Charm; future NPC talk may use the same voice | agreed |
| Merchant greeting (one or two lines) | Life in the shop | agreed |
| Greeting mentions today's weather | Fun; needs a weather source (see question) | open: needs a new host or an offline stand-in |
| Occasional trade request ("Wanted: 3 stone → a metal ingot") | Gives spare resources a use | proposed: rules to agree |

## Development story, encounters, Atlas (player, 2026-11-23; held; docs/development-story.md, mockup_atlas.png)
| Item | Why | Status |
|---|---|---|
| Something to build at every tier (3–5 projects each, a Wonder at the capital), each a developed hex with a small perk | Requests stopped after the campfire: a Camp had only one project | agreed (design for review) |
| Random encounters anywhere after walks (~1 a week, more with new ground; never a penalty) | Life and surprises | agreed |
| Two look pools: shop (bought) and rare (encounters and feats only, never sold) | Rare things should feel earned | agreed |
| Atlas: settlements only for now; capital card with next step; distance, direction, Go; sort chips; "becoming a settlement" | Wayfinding; long lists weren't useful | agreed |
| Merge a new waystone into an existing one within ~1 km (count arrivals); merge existing duplicates | Five separate waystones in one area | agreed |

## Camps, character sheet, achievements, settings (player, 2026-11-23; held)
| Item | Why | Status |
|---|---|---|
| Camps founded only by staying overnight (a 3 am location check in the area, away from home); maybe a voluntary "Pitch camp here" | "I actually camp there then"; 3 walking days was too automatic | agreed in principle; overnight-only vs + pitch camp open |
| Goal screen as a character sheet: wanderer, title and level, this week's quest, stats, week history | Feels like a game, not a chart | proposed |
| Target goal developed: named bands (Stroller 150 … Pathfinder 300), what moves it next, maybe choose your band | Goal felt opaque | proposed; auto vs chosen open |
| Achievements (ladders: km, places, roads, hexes, settlements, waystones, paddling, streaks) separate from quests; some give rare looks; monuments fold in | Long-term goals | proposed |
| Settings paginated with tabs (Tracking, Health, Map, Data & privacy, Credits) | Long page; credits deserve their own tab | agreed |
| Bug: Goal screen shows pre-0.12 rules text and a rolling-7-day total that disagrees with the map card | Outdated since ADR 0008/0009 | to fix |
| Check: the home region is now named after a waypoint area instead of the capital's area | Diagnostics 2026-11-23 | to investigate |

