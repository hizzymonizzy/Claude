# 0007 The world is a heat map of where you've been

**Status.** Accepted (design, 2026-11-21). Built in alpha 0.12.0.

**Decision.** One quantity underlies the world: **heat**, the number of distinct days you've walked a place
(cycling counts at its usual weight). Every system reads heat, changes it, or rewards the gap between hot and cold:
- Fog is no heat; explored ground is heat above zero.
- Roads, own paths and water routes are heat along a line (their wear).
- Settlements are hot clusters; the capital is the hottest, pinned at home.
- Discovery (new sources, double first yields, clearing fog) rewards cold.
- Decay is heat cooling (ADR 0008).

**Five steps, one rule.** Heat has the road stages' five steps: **1, 3, 10, 30, 100 days** (trail, footpath, lane,
cobbled road, avenue). A hex at a step looks the way a road at that step is worn: faded → normal → rich ground.

**Four zoom levels** (mockups: `docs/art/mockup_hex_tiers2.png`):

| Level | Hexes | What shows |
|---|---|---|
| Block | ~20 m (H3 res 12) | Ground from small hexes with a faint grid; settlements as paved hexes with their buildings around them; all roads and paths exactly as the map draws them; unwalked streets dashed |
| Neighbourhood | ~50–140 m (res 11 or 10, settle in prototyping) | Hexes of the same ground and step merge (no inner edges); same roads; unwalked streets dashed |
| County | ~1 km (res 8) | Main ground per hex at its step; settlements cover more hexes as they grow (village: centre + part ring, town: + ring, city: + 2 rings); worn links; waystones |
| Beyond | none | Regions as nodes sized by their best settlement's tier, linked by walks, paddles and waystones |

Edges: a heat-step change is a barely visible edge, a ground change a clear edge, the fog edge the strongest.
**Roads and paths are never hexes**: they're drawn on top, as they are.

**Why.** The player saw that this is a heat map game of places they've been, and liked it. Naming the one quantity
gives a test for every new rule (does it read heat, change it, or reward hot vs cold?), lets several counters merge
into one, and gives the map a strategy-board look that still keeps the real streets.

**Consequences.** Presence must be kept at a finer hex (res 12) than today's familiarity cell (res 9); coarser
levels count distinct days directly at their own resolution. The 0.11 round plaza and garden plots give way to paved
hexes. The rules audit is in `docs/design.md` §20.
