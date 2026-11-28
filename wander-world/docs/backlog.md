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
| Live gathering credit (count during the walk, not only at the end) | The ✓ is live, but the bar updates only when a walk ends | shipped 0.14.0 (every 5 min, in memory; no watch buzz: player's choice) |

## Map and art
| Item | Why | Status |
|---|---|---|
| Building footprints in explored ground as 0 A.D.-style roofs by settlement tier | Explored land now hides base-map buildings | proposed |
| Atlas: list of today's gathered nodes and where the next best ones are | Visibility of the economy | proposed |
| Encrypted export/import of your world | No backup by design; protects against a lost phone | later (player 2026-11-25: low value in alpha; starting fresh is fine) |

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
| Icons about 2× (buildings, banner, trader, resources), still easing down when zoomed out | Fire, banner and icons read at about half the size they should | shipped 0.13.0 |
| Buildings claim a block hex around their settlement and draw it as a developed cell (own ground + building art; upgrades change it) — at close zoom only, badges further out | "Unique cell development" | shipped 0.13.0 |
| Settlement medallions become hex-shaped | Consistent with the hex map | shipped 0.13.0 |
| Resource nodes and edge arrows as hex badges (ring colours become hex outlines) | Consistent with the hex map | shipped 0.13.0 |

## Shop, JRPG-style (player, 2026-11-23; held for more feedback; mockup docs/art/mockup_shop.png)
| Item | Why | Status |
|---|---|---|
| ✕ close top right; Wares / Looks tabs; framed parchment windows with a ▶ cursor on the selected row | JRPG menu feel; close at the bottom was awkward | shipped 0.13.0 |
| No rules text: prices, totals and greyed buttons show the mechanics (2×, market hall, weekly climb) | Brown mechanics text was overkill | shipped 0.13.0 |
| Quantity: − / + stepper, tap the number to type it (pad with Max), total in gold, Buy | Buy any amount, not just 1 or 5 | shipped 0.13.0 |
| One-line flavour text for each item (MMORPG-tooltip tone); none elsewhere for now | Charm; future NPC talk may use the same voice | shipped 0.13.0 |
| Merchant greeting (one or two lines) | Life in the shop | shipped 0.13.0 |
| Greeting mentions today's weather | Fun; needs a weather source (see question) | shipped 0.13.0 |
| Occasional trade request ("Wanted: 3 stone → a metal ingot") | Gives spare resources a use | shipped 0.13.0 |

## Development story, encounters, Atlas (player, 2026-11-23; held; docs/development-story.md, mockup_atlas.png)
| Item | Why | Status |
|---|---|---|
| Something to build at every tier (3–5 projects each, a Wonder at the capital), each a developed hex with a small perk | Requests stopped after the campfire: a Camp had only one project | shipped 0.13.0 |
| Random encounters anywhere after walks (~1 a week, more with new ground; never a penalty) | Life and surprises | shipped 0.13.0 |
| Two look pools: shop (bought) and rare (encounters and feats only, never sold) | Rare things should feel earned | shipped 0.13.0 |
| Atlas: settlements only for now; capital card with next step; distance, direction, Go; sort chips; "becoming a settlement" | Wayfinding; long lists weren't useful | shipped 0.13.0 |
| Merge a new waystone into an existing one within ~1 km (count arrivals); merge existing duplicates | Five separate waystones in one area | shipped 0.13.0 |

## Camps, character sheet, achievements, settings (player, 2026-11-23; held)
| Item | Why | Status |
|---|---|---|
| Camps founded only by staying overnight (a 3 am location check in the area, away from home); maybe a voluntary "Pitch camp here" | "I actually camp there then"; 3 walking days was too automatic | shipped 0.13.0 |
| Goal screen as a character sheet: wanderer, title and level, this week's quest, stats, week history | Feels like a game, not a chart | shipped 0.13.0 |
| Target goal developed: named bands (Stroller 150 … Pathfinder 300), what moves it next, maybe choose your band | Goal felt opaque | shipped 0.13.0 |
| Achievements (ladders: km, places, roads, hexes, settlements, waystones, paddling, streaks) separate from quests; some give rare looks; monuments fold in | Long-term goals | shipped 0.13.0 |
| Settings paginated with tabs (Tracking, Health, Map, Data & privacy, Credits) | Long page; credits deserve their own tab | shipped 0.13.0 |
| Bug: Goal screen shows pre-0.12 rules text and a rolling-7-day total that disagrees with the map card | Outdated since ADR 0008/0009 | shipped 0.13.0 |
| Check: the home region is now named after a waypoint area instead of the capital's area | Diagnostics 2026-11-23 | shipped 0.13.0 |

## Field feedback (2026-11-24, after 0.14.0)
| Item | Why | Status |
|---|---|---|
| Approve did nothing (6 taps; stock on screen covered the cost) | Same-day trades were credited after purchases and approvals; the request trade was also misreported as failed | shipped 0.14.1 |
| Tapping a building should say what it does | Badges in the Atlas did nothing | shipped 0.14.1 |
| Roads too thin to tell stages apart; paths should look like more than a coloured line | Screenshot 2026-11-25 | shipped 0.14.2 |
| Richer city development, SimCity-like: as infrastructure is built, the town grows around it (blocks, houses and streets filling in around buildings and roads) | Player, 2026-11-25: avenue trees are fine "for now" | proposed (discuss after camps) |
| Buildings rendered by us from 0 A.D.'s 3D models (concepts `docs/art/concept_growth.png`, `concept_projects.png`, `concept_closeup.png`): settlements grow building by building on their hexes; bird's-eye 35° | Player 2026-11-28: hex feel good, buildings wanting; "make them ourselves"; angle B chosen | shipped 0.15.0 |
| Culture per region (Britons, Romans, Persians … 0 A.D. has a dozen), changeable at the trader | Player 2026-11-28 | shipped 0.15.0 |
| Walking inside big stores is recorded as your own paths | `[paths]` lines for store visits: "joined an own path" | proposed |

## Placement and path normalization (player, 2026-11-24; shipped 0.14.0)
| Item | Why | Status |
|---|---|---|
| Worn banner drawn onto the capital hex (one composite for the worn banner, rebuilt when it changes) | Banner drifted away from the capital when zoomed in (fixed screen offset) | shipped 0.14.0 |
| Buildings take hexes touching the settlement's hex (or its outer paved ring), skipping hexes a road crosses; one ring further out only if all six are crossed | Upgrades sat two hexes out, detached from the capital | shipped 0.14.0 |
| Snap to the road: an unmatched stretch that starts and ends on matched roads and stays within 35 m of the street between them credits the street | A connector between two worn roads stayed dashed (GPS drift recorded it as an own path) | shipped 0.14.0 |
| Merge own paths: a new stretch within ~20 m of an existing one, same direction, wears it; on beaches limited by depth (narrow < 40 m: one path; deep: within a third of the depth); never beyond ~40 m | Parallel beach paths from different days | shipped 0.14.0 |
| Pointer labels at the screen edge stack instead of overlapping | "530 m · new m · new" overlap | shipped 0.14.0 |
| Diagnostics: per walk, unmatched stretches (count, longest, farthest from a road) | Old logs only give totals (e.g. 43 of 82 points matched), not where | shipped 0.14.0 |

## Camps by nights, Atlas tidy-up (player, 2026-11-25; Atlas shipped 0.14.0, camps planned for 0.15 before mid-December)
| Item | Why | Status |
|---|---|---|
| A camp is pitched where you spend a night (not one per area): a night within a camp's reach counts toward it; two spots further apart in one area make two camps | "A camp would be anywhere I have spent an evening" | shipped 0.15.0 |
| Camps grow by **stays** (one or more nights in a row at the same spot; a week away is one stay), never by walking: 1 Camp, 3 Hamlet, 10 Village, 30 Town, 100 City (Metropolis capital only); weekly cooling and floors as now | "Stays, not nights: I'm actually camping there" | shipped 0.15.0 |
| Camp reach grows with tier in neighbourhood hexes (~130 m): Camp 2, Hamlet 3, Village 4, Town 5, City 6; a stay within reach counts toward that camp; camps whose reaches meet merge and add up their stays (more stays keeps name, spot, buildings); camps elsewhere in an area stay separate | Hex reach like roads; areas develop differently | shipped 0.15.0 |
| The capital keeps growing from walking days at home (nights at home don't count) | Otherwise sleeping at home would grow it | shipped 0.15.0 |
| Camp names from the nearest named place ("Camp at Oakdale Park"), else area name + number | Several camps per area need names | shipped 0.15.0 ("Camp at <place>", numbered) |
| Atlas: remove "Places you could camp"; camps appear once you've slept there; a camp's history lists its nights | The list was odd | removal shipped 0.14.0; camp history with camps (0.15) |
| Request card: drop the trader line; show the building's perk instead of "+1 prosperity" | Trader line unnecessary; prosperity meaningless | shipped 0.14.0 |
| Atlas uses hex settlement art; settlement row layout fixed (distance and last visit no longer overlap the bar); capital card as in the mockup | Screenshot 2026-11-25 | shipped 0.14.0 |
| Night check at 3 am **local to the zone you're in**: schedule each check one night at a time for the next local 3 am; reschedule when the phone's time zone changes; credit the stay to the local evening before; log time and zone | International travel: today's 24-hour repeat stays on home time | shipped 0.15.0 |

