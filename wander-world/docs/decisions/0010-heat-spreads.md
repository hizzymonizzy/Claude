# 0010 Heat spreads from walked hexes; fog clears to hex edges

**Status.** Accepted (2026-11-22). Built in alpha 0.12.2. Extends [0007](0007-world-is-a-heat-map.md).

**Decision.**
- A hex walked to step N (1, 3, 10, 30, 100 days) enriches the hexes up to **N rings** around it, one level poorer
  per ring: the hex itself shows its step and the outermost ring is pale explored ground. Overlaps take the richest.
- The ladder runs pale (0, edge of a path's reach) → normal (1, walked once) → richest (5), so walked ground is always
  greener than what's around it. Roads and paths are separate lines on top; they never change a tile.
- **Fog clears to the outline of those hexes** (plus a small hex clearing at waystones, small places, paddled water),
  and small enclosed pockets still fill in.
- Neighbourhood and county hexes are drawn only where at least a fifth of the hex is covered, at the mean level.
- Cooling shrinks the reach with the step (a step lost = a ring lost), never below the floor of ADR 0009.

**Why.** In 0.12.0/0.12.1 walked hexes used the faded end of the ladder while explored ground around them kept the
normal texture, so walking made the ground look worse, and the fog still cleared in smooth bands. The player's rule:
"tier 1 converts tiles adjacent by 1, tier 5 by five", which fills in map coverage where traffic is high.
