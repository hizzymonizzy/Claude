# Development story (built in alpha 0.13.0)

Designed 2026-11-23 and built in 0.13.0 with these differences from the draft below:
- Camps are founded only by spending a night in an area (player's choice), not by 3 walking days.
- Perks as built: town hall +3 food a walked day (no weekly town request yet); meeting hall +2 food (no second
  request choice yet); great hall +1 wood and +2 food; signpost shows the way (text only for now); mill boosts shore
  and field food; water buildings boost paddling food (bridge/lighthouse +25%, pier +50%, harbour ×2).
- Lantern outfit (walk before sunrise) is replaced by the Wayfarer coat (find 5 waystones): the engine has no time of day.
- New projects without their own art yet reuse the nearest existing picture.
- Simulation (StorySimulationTest): 12 encounters in 90 days of walking 5 days a week.

## 1. How settlements are made and grow
- **Founding:** an area (a neighbourhood the map names, e.g. "Elm Hollow") becomes a **Camp** once you've spent at
  least **5 minutes on foot there on 3 different days within 30 days**. Your home area becomes the capital.
- **Growth:** visit-days in a month raise the tier: Hamlet 10, Village 25, Town 60, City 150, Metropolis 400 (capital).
- **The Atlas shows both** (see the mockup): each settlement's progress to its next tier, and areas that are
  "becoming a settlement" (1/3, 2/3 days) with distance and Go, so making more is a visible, walkable goal.

## 2. Something to build at every tier
Each settlement gets requests from its own ladder, cheapest first; a declined request returns after 14 days. Every
building claims a developed hex next to its settlement (hex design, section C) and gives a small, local perk.
✓ = exists today, ↑ = upgrade of the building above it.

| Tier | Project | Cost (draft) | Perk | Developed hex |
|---|---|---|---|---|
| Camp | Campfire ✓ | 20 gold, 5 wood | +1 food each day you walk here | packed earth, fire |
| Camp | Signpost | 10 gold, 3 stone | Atlas and map show the way to this settlement's next tier and the nearest unexplored area | earth, wooden sign |
| Camp | Forager's cache | 20 gold, 3 wood, 2 food | +1 food on days you walk 2 km+ here | earth, baskets |
| Camp | Lean-to | 15 gold, 4 wood, 2 stone | Visit-days here count 25% extra | earth, shelter |
| Hamlet | Well ✓ | 50 gold, 10 stone | Thinned sources here regrow 1.5× | stone, well |
| Hamlet | Meeting hall ↑ ✓ | 80 gold, 15 wood, 10 stone | +2 food a walked day; you choose between two requests | earth, hall |
| Hamlet | Woodshed | 60 gold, 10 wood, 5 stone | Trail wood here +25% | earth, log stack |
| Hamlet | Vegetable patch | 50 gold, 5 wood, 5 food | +1 food a walked day | field rows, crops |
| Village | Market ✓ | 150 gold, 10 wood, 20 stone | Shop at base prices | cobbles, stalls |
| Village | Fountain ↑ ✓ | 120 gold, 30 stone, 5 metal | Regrowth 2× | stone, fountain |
| Village | Mill | 140 gold, 20 wood, 15 stone | Field-edge food here +50% | field, mill |
| Village | Shrine | 100 gold, 20 stone, 5 metal | Homecoming here counts ×3 (not ×2) | stone, shrine |
| Village | Bridge ✓ (near water) | 120 gold, 30 wood | Paddle routes here clear wider | planks over water |
| Town | Town hall ↑ ✓ | 250 gold, 30 wood, 60 stone, 10 metal | A weekly request from the town (see 3) | cobbles, hall |
| Town | Inn ✓ | 200 gold, 20 wood, 40 stone | Encounters twice as likely near here | cobbles, inn |
| Town | Market hall ↑ ✓ | 300 gold, 20 wood, 40 stone, 10 metal | Shop 10% off | cobbles, covered market |
| Town | Walls ✓ | 300 gold, 80 stone, 20 metal | This settlement cools half as fast | stone ring |
| Town | Pier ✓ / Lighthouse ✓ (near water) | 200–300 gold, wood/stone | Paddling food +50% / water routes glow | planks / tower |
| City | Great hall ✓ | 800 gold, 60 wood, 120 stone, 40 metal | +1 request choice everywhere | marble, hall |
| City | Library | 500 gold, 40 wood, 60 stone, 30 metal | Metal from civic places +50%; lore lines in the dispatch | marble, library |
| City | Guildhall | 600 gold, 40 wood, 80 stone, 40 metal | Shop a further 10% off | marble, guild |
| City | Harbour (near water) | 700 gold, 80 wood, 60 stone | Pier and lighthouse perks across the region | quay |
| Metropolis | **Wonder** (capital only), 4 stages: Foundation, Walls, Spire, Crown | 1,000+ gold each, all four resources | Each stage: a legendary look; complete: the Wonder on the map | 7 hexes, grows by stage |

**Simulation before shipping:** a Camp should always have something within 1–2 walks of resources; one request per
settlement at a time; the ladder should take a steady walker about a month per tier at Hamlet–Village.

## 3. Random encounters (anywhere, optional, never a penalty)
- **When:** after a walk ends, with a small chance (about one a week on average). The chance rises with how much new
  ground the walk covered, so discovery is rewarded even though encounters can appear anywhere. An Inn doubles it nearby.
- **How:** a figure appears on the map where the walk ended (or along it) for 3 days, plus a dispatch line. Tap to talk.
  Ignoring it costs nothing.

| Encounter | Asks | Gives |
|---|---|---|
| Wandering bard | Walk somewhere you've never been in the next 3 days | 30 gold and a verse in the dispatch |
| Lost peddler | Resources (e.g. 10 wood + 10 stone) | A look from the rare pool |
| Collector | Metal (15–30) | A rare look, or gold at a good rate |
| Courier | Walk to a named settlement within 7 days | Gold + food |
| Trader's request | 3–5 of a resource you have or can gather (weekly, expires Monday) | ~20% better than market, in another resource |
| Lost pet | Walk through a nearby park within 2 days | Gold; a "good neighbour" line |
| Harvest festival (village+, after a strong week) | Nothing: celebrate | Bonus food and the season's festival banner |

## 4. Looks: two separate pools
- **Shop pool (buy with gold, always available):** the five banners and four outfits sold today, later frames and map
  tints. Common.
- **Rare pool (never sold; only from encounters and feats):**

| Look | Rarity | How |
|---|---|---|
| Patchwork cloak | Rare | Lost peddler |
| Ironbound banner | Rare | Collector |
| Festival banners (one per season) | Rare | Harvest festival |
| Pathfinder cloak | Rare | Chart 25 unwalked trails |
| Tidewalker banner | Rare | Paddle 10 km in total |
| Evergreen banner | Rare | Discover 10 woods or parks |
| Lantern outfit | Rare | Walk before sunrise on 5 days |
| Cartographer's hat | Legendary | Chart 250 roads |
| Laurel outfit | Legendary | 12 weeks in a row over the goal |
| Wonder banners (four) | Legendary | Each Wonder stage |

The Looks list in the shop shows both pools; rare ones you don't own show how to earn them, never a price.
