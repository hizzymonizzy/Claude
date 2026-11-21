# 0008 Decay is felt weekly: heat cools when you miss your goal

**Status.** Accepted (design, 2026-11-21). Supersedes [0006](0006-gentle-decay.md). Not built yet.

**Decision.** Every Monday, last week's health-goal ratio `r` cools all heat (hexes, roads, own paths, water routes)
by a share of its current value. Settlements keep their tier floors (the capital never below Village).

| Last week vs goal | Heat change |
|---|---|
| r < 50% | −30% |
| 50–80% | −30% sliding to −15% |
| 80–100% | −3% sliding to 0 |
| 100% | none |
| 100–150% | recovery: regain up to half of what was lost (scaled by how far over 100%) |

- **Floor:** a place never falls more than **two steps below its best**, and never back to fog. (Later: upgrades may
  raise floors or slow cooling; option "fade to step 1" kept in reserve.)
- **Homecoming:** walking a cooled place counts ×2 until it's back to the step it had.
- **Safety valves unchanged:** rest mode pauses cooling; regions away 14+ days cool at ×0.1; the goal adapts (rises
  slowly, falls fast, within 150–300).
- The Monday dispatch names what faded and what recovered.

With no walking at all: an avenue (100) is cobbled after 1 week, still cobbled at 2, a lane at 4 (its floor).

**Why.** The player found 0006 too slow: roads took about 14 months to drop a step, so weeks never mattered. "Missing
2 weeks is bad, 4 should be consequential." Recovery from exceeding the goal keeps it balanced, and rest mode keeps it
a health app that doesn't punish rest or injury.
