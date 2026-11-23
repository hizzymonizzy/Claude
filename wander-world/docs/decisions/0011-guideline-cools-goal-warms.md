# 0011 The guideline decides cooling; the adaptive goal only decides warming

**Status.** Accepted (2026-11-23). Built in alpha 0.13.0. Amends [0008](0008-weekly-cooling.md) and
[0009](0009-partial-decay.md): their Tuesday scoring, step scale, floor and homecoming stay.

**Decision.**
- **Cooling** is judged against the fixed 150-minute weekly guideline (WHO 2020, US 2018), never the adaptive goal:
  under 75 active minutes ¼ step, 75–119 ⅛, 120–149 1/40, 150 or more nothing.
- **Warming** is judged against the adaptive goal: a week at or over it brings back ¼ step.
- **The goal** adapts between 150 and 300 in ~10% steps; it rises at most once per four weeks (3 of 4 weeks at 120%+),
  never past the recent median + 10%; it eases to the average after 2 weeks under 70%. Only real weeks count (no rest
  weeks, no grace). Bands name ranges: Stroller 150, Rambler 200, Trekker 250, Pathfinder 300; the best band reached is
  kept as a title. The player can lock the goal or pick a band.
- **Rest:** for three weeks after a rest ends, only weeks under 75 minutes cool, by ⅛.
- **Words:** calm and seasonal ("frost on the outer lanes"), saying what was kept; no alarms, no "make up minutes".
- Not adopted (player's choice): a setting to turn cooling off, and a welcome-back bonus.

**Why.** Research report `docs/research/reports/Gamifying a weekly activity goal.md`: scoring cooling against a rising
goal punishes a player who meets the guideline ("punished while doing well"), the pattern linked to falling motivation
in decay-based habit apps. Adaptive goals help (small trials, ~+1,000 steps/day) when they reward rather than punish.
