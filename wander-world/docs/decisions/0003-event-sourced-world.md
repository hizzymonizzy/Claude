# 0003 The world is replayed from stored observations

**Context.** The rules (decay, tiers, economy) will keep changing during the alpha.

**Decision.** Store only observations (road coverage, cell presence, intensity, visits, arrivals, the player's
decisions). `WorldEngine.build` replays them day by day into the current world. The engine is pure Kotlin, with
no Android, so it's fully unit-testable.

**Consequences.** Changing a rule changes history consistently. Player choices must be stored as events (e.g.
project decisions) so they replay too. Rebuild cost grows with history, so add snapshots when it shows up in
profiling.
