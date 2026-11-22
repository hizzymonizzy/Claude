# 0009 Partial decay: a quarter step a week, counted from the best

**Status.** Accepted (2026-11-22). Built in alpha 0.12.1. Supersedes the cooling amounts in [0008](0008-weekly-cooling.md)
(its Tuesday scoring, floor, homecoming and safety valves stay).

**Decision.** Cooling and recovery are measured in steps (roads, paths, water routes and hexes: 1/3/10/30/100 days;
settlements: their tiers), not as a share of heat.
- A badly missed week (under half the goal) costs **¼ step**; 50–80% of the goal costs ¼ sliding to ⅛; a near miss
  (80–100%) at most 1/40; the goal met, nothing.
- A step shows as lost only once a **whole step** has cooled since the place's best: a place that has just reached a
  step doesn't drop back after one missed week. So 1–3 missed weeks show no step change; 4 cost one step; 8 reach the
  floor (two steps below the best).
- Any week **over the goal** brings back **¼ step** everywhere (never past the best).
- Walking a cooled place counts **double** until it's back (homecoming), for settlements as soon as they drop a tier.

**Why.** The player found 0.12.0's simulation too harsh: a 30% cut took places that had just reached a step (an
avenue at 112 days, a city just over its line) down a whole step after one missed week. They wanted partial decay:
"2 missed weeks is bad, 4 consequential", with catching up restoring at least as fast as decay.
