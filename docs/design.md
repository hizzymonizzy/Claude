# Design Doc: A World Built by Walking

> **Status:** Draft v0.1. Builds on [`concept.md`](concept.md).
> **Scope:** Phase 1 (core loop) in full detail. Phases 2–4 at the level of how each system works.
> **Convention:** Every number is a **starting value** and lives in one tuning file (§3.3). Items marked **🔶 Decision** need the owner's call. Items marked **🧪 Spike** need a proof-of-concept before we commit.

---

## 1. Goals and non-goals

**Goals**
- Turn everyday outdoor movement into a growing personal world, with no extra effort beyond carrying the phone.
- Reward consistency and frequency, not intensity or volume.
- Keep all location data on the device.
- Run all day on Android with an acceptable battery cost (target: **≤ 5% per day** for a typical day with about 60 minutes of movement).

**Non-goals (v1)**
- Multiplayer, social features, leaderboards, sharing.
- iOS.
- Importing past activity.
- A server backend. The app is fully on-device apart from fetching map data.

---

## 2. Key design decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **Event-sourced world.** Raw observations (what you walked, where, when, how many minutes) are the source of truth. All world state (settlements, wear, decay) is *derived* by a deterministic engine. | We can re-tune thresholds and rebuild the world from the real history without losing or corrupting anything. Essential while the numbers are still guesses. |
| D2 | **H3 resolution-9 cells** (~0.1 km², ~174 m edge) as the familiarity unit. | Roughly block-sized; standard, fast library; cells have stable IDs. |
| D3 | **OSM road "edges"** (a way split at intersections) as the exploration unit. | Matches how people think about streets ("I walked down Grove"); carries real names. |
| D4 | **Areas = neighborhoods** from OSM place nodes; falls back to H3 res-7 parents. | Gives real names; the fallback covers places with no neighborhood data. |
| D5 | **Daily tick.** Decay, restoration, goal checks and the dispatch run once per day; exploration and familiarity update live. | Simple, predictable, testable; matches the once-a-day rhythm. |
| D6 | **Two data sources, two jobs.** Health Connect drives *protection* (intensity minutes). The app's own GPS drives *growth* (exploration, familiarity, materials). | Concept §3: indoor exercise protects the world but doesn't build it. |
| D7 | **MapLibre for both map styles**, drawing our own GeoJSON layers. | One rendering stack; full style control; no Google Maps terms or costs. |

---

## 3. Architecture

### 3.1 Modules

```
┌─────────────────────────────────────────────────────────────────┐
│ app (Compose UI): MapScreen, WorldView, Dispatch, Atlas, Settings │
└──────────────┬───────────────────────────────┬──────────────────┘
               │                               │
      ┌────────▼────────┐             ┌────────▼────────┐
      │  world-engine   │◄────────────│   dispatch      │
      │ (pure Kotlin,   │  events     │ (text generator)│
      │  deterministic) │             └─────────────────┘
      └──┬──────────┬───┘
         │          │ observations
┌────────▼───┐  ┌───▼──────────────┐   ┌──────────────────┐
│  health    │  │  movement        │──►│  geo             │
│ (Health    │  │ (tracking, filter│   │ (OSM fetch/cache,│
│  Connect)  │  │  map matching)   │   │  road graph, H3, │
└────────────┘  └──────────────────┘   │  areas, biomes)  │
                                       └──────────────────┘
                     storage (Room / SQLite) underlies all
```

- **`movement`**: activity detection, GPS capture, filtering, splitting into segments, map matching. Outputs **observations**.
- **`geo`**: fetches and caches OSM data; builds the local road graph; assigns cells to areas; tags biomes; provides elevation.
- **`health`**: reads Health Connect; produces **daily intensity metrics**.
- **`world-engine`**: pure Kotlin, no Android dependencies. `(observations, metrics, params, clock) → world state + world events`. Fully unit-testable and replayable.
- **`dispatch`**: turns a day's world events into the daily dispatch text.
- **`app`**: UI, rendering, permissions, WorkManager scheduling.

### 3.2 Data flow

1. **Live:** movement detected → GPS fixes → filtered track → map-matched edges + cell-minutes → observations written → engine applies *growth* updates incrementally → map refreshes.
2. **Daily (~04:00 local):** health sync → engine runs the *daily tick* (goal evaluation, decay or restoration, dormancy, tier changes) → world events → dispatch generated → one notification at the user's chosen morning time.
3. **On re-tune:** engine replays all observations from day 0 with the new params → new derived state replaces the old.

### 3.3 Tuning

All numbers live in a single `WorldParams` data class (versioned). Changing params triggers a replay (D1). A debug screen can override params and fast-forward simulated time.

---

## 4. Movement capture

### 4.1 Detection and capture

| Step | Mechanism |
|---|---|
| Detect movement | Activity Recognition **Transition API**: enter/exit for `WALKING`, `RUNNING`, `ON_FOOT`, `ON_BICYCLE`, `IN_VEHICLE`, `STILL` |
| Start capture | On enter WALKING/RUNNING/ON_FOOT → start **foreground service (type `location`)** with Fused Location Provider |
| Sampling | High accuracy; interval 5 s running / 10 s walking; minimum displacement 5 m |
| Stop capture | `STILL` for ≥ 2 min, or `IN_VEHICLE` entered → stop GPS, close the segment |
| Manual sessions | "Start a walk" button forces capture on, for intentional sessions |
| Coarse presence | Low-power passive/balanced location, a few fixes per day, used **only** for region and travel detection (§9) |

> **🧪 Spike S1 (highest risk): background capture on Android 14+.** Android limits starting a location foreground service from the background. We must verify, on the owner's actual phone, that an activity-transition broadcast can reliably start capture. Fallbacks, in order:
> (a) A long-running, low-priority foreground service during waking hours, idling cheaply until movement is detected.
> (b) Background location updates (throttled to a few per hour by Android, too sparse for roads, so only for familiarity at the cell level).
> (c) Manual sessions only, with everyday walking credited at the cell level from (b).
> The whole "passive idle" experience depends on S1. Do it first.

### 4.2 Filtering

- Drop fixes with reported accuracy > 30 m.
- Speed sanity: sustained > 7 m/s → treat as vehicle; those points never count toward exploration or familiarity. Cycling (`ON_BICYCLE`) counts toward familiarity and materials at **50%** weight and does reveal roads. **🔶 Decision:** confirm cycling should count at all.
- Split a track into segments where there's a gap of more than 60 s or 100 m.
- Smooth with a simple Kalman filter before map matching.

### 4.3 Map matching

- **Algorithm:** HMM map matching (Newson & Krumm 2009) using the Viterbi algorithm over candidate edges within 25 m of each fix. Emission cost = distance to edge; transition cost = |route distance − straight-line distance|.
- **Candidate edges** come from the local road graph (§5.2), including footways, paths, tracks, pedestrian streets and residential and service roads.
- **Output:** an ordered list of `(edgeId, coveredFromM, coveredToM, timestamp)`.
- **Off-network movement** (open fields, beaches, parks without paths) gives no edge credit but still counts for cell familiarity and materials.

> **🧪 Spike S2:** matching accuracy in dense blocks and on sidewalks that OSM maps as separate footways. Target: ≥ 90% of walked edges correctly identified on 10 recorded test walks.

### 4.4 Discovering an edge

- An edge is **discovered** when its total covered length (combined over all visits) reaches **60% of its length, or 150 m for long edges**, whichever is less.
- An edge's **wear** goes up by 1 per **distinct day** it's covered by ≥ 50% (or ≥ 150 m).

### 4.5 Observations written

| Observation | Fields |
|---|---|
| `EdgeCoverage` | edgeId, day, coveredIntervals |
| `CellPresence` | h3Cell, day, movingSeconds, mode (walk/run/cycle) |
| `ElevationGain` | h3Cell, day, meters |
| `RegionPresence` | regionCandidate (lat/lng coarse), timestamp |

Raw GPS points are kept for **90 days** (for re-matching after improvements), then deleted. Observations are kept forever.

---

## 5. Geo data

### 5.1 Sources

| Data | Source | Notes |
|---|---|---|
| Real-map basemap | Protomaps basemap (PMTiles) or a hosted vector-tile provider | Styled muted; used only in the real-map view |
| Road graph + names | OSM via **Overpass API**, fetched per H3 res-6 tile (~36 km²) on demand | Basemap tiles simplify geometry and drop way IDs, so a separate road-grade fetch is needed |
| Places / neighborhoods | OSM `place=neighbourhood/suburb/quarter/village/town/hamlet` nodes and polygons | Same Overpass fetch |
| Land use / biomes | OSM `landuse`, `leisure`, `natural`, `waterway`, coastline | Same fetch |
| Elevation | Open terrain tiles (Terrarium/AWS elevation) sampled along tracks | Cached per tile |

- **Prefetch:** the home tile ring (res-6 cell + neighbors) at setup; neighbors of any newly entered tile in the background on unmetered networks.
- **Offline:** if a tile is missing, observations are stored as raw segments and matched later once data arrives. Nothing is lost.
- **Attribution:** OSM (ODbL) and basemap attribution shown in-app.
- **🔶 Decision:** Overpass is free but rate-limited. That's fine for one user. If the app is ever published, we'd need our own extract service.

### 5.2 Road graph

- OSM ways split at intersections into **edges**: `edgeId = hash(wayId, startNodeId, endNodeId)`, which stays stable across re-fetches as long as OSM topology doesn't change.
- **Edge name:** `name` tag; for a separately mapped sidewalk, use the parent street's name where it can be inferred; otherwise "Unnamed path" or the name of the containing park.
- **If OSM changes later:** previously discovered edges are kept with their snapshot geometry and name; new edges match normally.

### 5.3 Areas (neighborhoods)

- Each res-9 cell is assigned to the **nearest place node within 2 km**, preferring the most specific place type (neighbourhood > quarter > suburb > hamlet > village > town). Effectively a Voronoi split, clipped by place polygons where they exist.
- If no place node is nearby, the cell falls back to its **res-7 parent** (~5 km²), named after the most-walked edge in it ("Ridge Road").
- An area's name and cell set are **frozen when the area first gets familiarity**, so names never shift later.

### 5.4 Biome tagging

Each res-9 cell gets a main biome from land use covering ≥ 30% of the cell, otherwise from roads and buildings:

| Biome | OSM signals | Material (Phase 3) |
|---|---|---|
| Woodland | `landuse=forest`, `natural=wood`, `leisure=park` + trees | Timber |
| Highland | elevation gain while moving, `natural=peak/ridge` | Stone |
| Coast | within 150 m of coastline / large water | Fish, salt |
| Urban | dense buildings / `landuse=commercial,retail,residential` high density | Brick |
| Fields | `landuse=farmland,meadow,grass` | Grain |
| Region climate (overlay) | latitude + coastline | Specialty goods |

---

## 6. Health and the weekly goal

### 6.1 Intensity minutes

- **Primary:** Health Connect `ActivityIntensityRecord`, aggregated per day into moderate and vigorous minutes.
- **Fallback** (if the device writes no intensity data): calculate from `StepsCadenceRecord` / per-minute steps. ≥ 100 steps/min = moderate, ≥ 130 = vigorous. Plus `ExerciseSessionRecord` durations (running = vigorous, other cardio = moderate).
- **Intensity minutes = moderate + 2 × vigorous.**
- **Deduplication:** use Health Connect's aggregate APIs, which apply the user's data-source priority settings.
- The app does **not** write its own sessions to Health Connect in v1, to avoid double counting.
- **🧪 Spike S3:** check what the owner's phone and any watch actually write to Health Connect. **🔶 Decision:** which phone and wearable do you use?

### 6.2 The goal

- **Protection ratio** `r = Σ intensity minutes over the trailing 7 days ÷ goal`, recalculated daily.
- **Goal adjustments** run once a week on a fixed day (Monday), using calendar weeks:

| Rule | Logic |
|---|---|
| Increase | If ≥ 3 of the last 4 weeks have `minutes ≥ 1.2 × goal`: `goal = min(ceiling, round10(goal × 1.10))` |
| Decrease | If the last 2 weeks are both `< 0.7 × goal`: `goal = max(150, round10(mean(last 2 weeks)))` |
| Bounds | floor 150, ceiling 300 |
| Lock | If the user has locked the goal, skip both rules |
| Notice | Changes are announced in the dispatch, never as a separate notification |

- **New-player grace:** no decay for the first **14 days**, while the rolling window fills up.

---

## 7. Settlements

### 7.1 Familiarity and development

- **Cell familiarity** `F(cell)` += 1 for each distinct day with ≥ 60 s of moving presence (only one credit per day). Drives fog shading and the "known" feel.
- **Area development** `D(area)` goes up each **area-day** (a day with ≥ 5 min of moving presence in the area):

  `ΔD = 1.0 + 0.5 × min(1, (minutes − 5) / 30)`

  So each day is worth 1.0–1.5. Frequency dominates; spending longer in one day adds at most 50%.

### 7.2 Founding

An area founds a **Camp** once it has **≥ 3 area-days within any 14-day window**. Founding is an event and goes into the history and the dispatch.

The **Capital** is founded on the first day with moving presence in the **home area** (set during onboarding).

### 7.3 Tiers

| Tier | Index | D threshold | Breadth: distinct cells explored in area |
|---|---|---|---|
| Ruins | 0 | (only reached by decay) | — |
| Camp | 1 | 3 | 1 |
| Hamlet | 2 | 10 | 3 |
| Village | 3 | 25 | 6 |
| Town | 4 | 60 | 12 |
| City | 5 | 150 | 25 |
| Metropolis (capital only) | 6 | 400 | 40 |

- **Breadth** requirements gently encourage exploring around a place, not just repeating one line through it.
- **Tier = highest tier whose D and breadth requirements are both met.**
- Track `peakTier` and `peakD` forever.

### 7.4 Placement

- The settlement's position is the familiarity-weighted centroid of its cells, snapped to the nearest cell center. It's fixed at founding and only moves if the centroid drifts by more than 2 cells (recalculated at tier-ups only).

### 7.5 Liveliness

`liveliness = 0.5 ^ (daysSinceLastAreaDay / 30)`. Visual only; it drives crowd density, lit windows and market stalls. It has no effect on game state.

---

## 8. Protection, decay and restoration

Runs in the **daily tick**, after the health sync.

### 8.1 Decay multiplier

```
m(r) = 1 − r                 if r < 0.8
     = 0.5 × (1 − r)         if 0.8 ≤ r < 1.0     // at most 0.10
     = 0                     if r ≥ 1.0
```

### 8.2 Development decay (per settlement, per day)

```
floorTier = max(0, peakTier − 2)        // capital: max(3, peakTier − 2)
D_floor   = threshold(floorTier)        // Ruins → 0
loss      = (D − D_floor) × baseDaily × m(r) × sturdiness(tier) × dormancy(region)
D        -= loss
```

| Param | Value |
|---|---|
| `baseDaily` | 0.0073 (≈ 5% per fully inactive week) |
| `sturdiness` | Camp 1.0, Hamlet 0.8, Village 0.6, Town 0.45, City 0.3, Metropolis 0.2 |
| `dormancy` | 1.0 active, 0.1 dormant |

If D falls below the current tier's threshold, the tier drops (it can't go below `floorTier`). Each drop is an event.

**Example:** a City (D = 200, peak City) during a fully inactive month (r = 0): floor = Village (25). Weekly loss ≈ 5% × 0.3 = 1.5% of the 175 above floor, so roughly **10 D per month**. Still a City after a month, and around a year to hit its floor. A Camp in the same month loses most of what it had above Ruins.

> Sturdy cities can feel unbothered by short slumps. That's intended. Decay pressure mostly lands on young, small settlements, which recover quickly anyway.

### 8.3 Restoration

```
if 1.0 < r ≤ 1.5:
    gain = (peakD − D) × restoreDaily × (r − 1) / 0.5
    D   += gain
```

- `restoreDaily` = 0.04 (at r = 1.5, about 25% of what was lost comes back per week).
- Restoration only returns you toward `peakD`; new growth beyond the peak comes only from area-days.
- **Ruins remember:** for a settlement that has fallen two or more tiers, area-days count **×2** until it's back to `peakTier − 1`. A homecoming feels fast.

### 8.4 Trail wear decay

- Wear `W(edge)` decays only when `r < 0.8`: `W -= W × 0.02/7 × m(r)` per day, floor 1.
- Visual levels come from `W` (1 / 3 / 10 / 30 / 100). Fog never returns.

### 8.5 Rest mode

- A toggle in settings: "Rest mode: pause my world." It freezes decay, wear decay and goal adjustments. It does not freeze growth: walking during rest mode still counts.
- After 28 days on, the dispatch asks once, gently: *"Your world is resting peacefully. Ready to wake it, or keep resting?"* No other prompts.

---

## 9. Regions, travel and dormancy (Phase 2)

### 9.1 Regions
- A **region** is a cluster of areas. The home region is created at onboarding.
- A **new region** is created when coarse presence stays > 100 km from every existing region's centroid for ≥ 6 hours (so passing through an airport doesn't count).
- Areas join the region whose centroid is nearest (within 100 km).

### 9.2 Travel routes
- A route opens when presence moves from region A to region B within 48 hours.
- **Route type:** < 800 km → **road**; ≥ 800 km → **sea lane** if either region is coastal, else **air route**. These are cosmetic and only affect how the route is drawn and which projects are possible. **🔶 Decision:** fine as is, or should every long route be a sea lane, for a consistent "islands" feel?
- Repeat trips strengthen a route (cosmetic: busier shipping).

### 9.3 Dormancy
- Any region other than home with no presence for **14 days** becomes dormant (decay ×0.1).
- On arrival, the region wakes immediately. The dispatch says something like *"Old Denver stirs. The townspeople remember you."*

---

## 10. Rendering and UX

### 10.1 Screens
- **Map (home):** real map or world view (toggle). The dispatch card is pinned at the top until dismissed.
- **Dispatch archive:** scrollable history of past dispatches.
- **Atlas:** all settlements grouped by region, each with its history card.
- **Settlement card:** tier, history, liveliness, biome, projects (Phase 3), notes (Phase 4).
- **Goal:** this week's minutes vs. goal, protection state, goal history, lock toggle, rest mode.
- **Settings:** home location, permissions, data export, tuning debug (developer build only).

### 10.2 Real-map view (Phase 1)
Layers, bottom to top:
1. Muted basemap.
2. **Fog:** a parchment-textured polygon covering everything except the *revealed area* (walked edges buffered 40 m, plus cells with familiarity ≥ 1 at low opacity).
3. **Peek ahead:** undiscovered edges sharing a node with a discovered edge, drawn faint and unnamed through the fog.
4. **Worn roads:** discovered edges styled by wear level, with name labels.
5. **Settlements:** tier icons with names.
6. **You:** current position and the live track.

### 10.3 World view (Phase 2)
- **Landmasses:** each region's explored cells plus a 1-cell ring, merged and smoothed into a coastline.
- **Layout between regions:** start from the capital; place each region at its **real compass direction** at a distance of `k × ln(1 + d / 100 km)`; then gently push overlapping regions apart while keeping their direction within ±20°. The layout is recalculated only when a region is added, with an animated transition.
- **Inside a region:** real geometry, re-styled (roads by wear, settlements as illustrated tiles, biome textures on cells).
- **Zoom levels:** world (islands and routes), region (settlements and roads), settlement (close-up illustration).

> **🔶 Decision: visual style.** Recommendation: **hand-drawn cartography**, ink and watercolor in the style of an old explorer's chart. It fits "charting roads," "the Atlas" and parchment fog, it works well for a small team (2D, tile-based assets), and both map views can share it. Alternatives: cozy low-poly 3D (more appealing, much more expensive to make) or flat modern illustration.

### 10.4 Notifications
- **One per day:** the dispatch, at the user's chosen time (default 07:30).
- **Required by Android:** the tracking foreground-service notification while capturing. Kept minimal ("Charting your walk · 1.2 km").
- **Nothing else.** No nudges, no reminders, no "you're about to lose…" messages.

---

## 11. The daily dispatch

### 11.1 Generation
- Built from the previous day's **world events** in priority order: founding / tier-up → new region / route → charted roads (up to 3 named) → materials and arrivals (Phase 3+) → goal status → one proposal (Phase 3+) → townspeople note (Phase 4).
- **v1:** templates with several phrasings per event type, chosen by a seeded random draw. Tone rules: warm, observational, no guilt-tripping, no exclamation marks about losses.
- **Quiet days:** *"A quiet day. The world rests, and so can you."* Under `r < 0.8` it notes softly what's happening (*"Moss is creeping over the eastern camp."*) without blame.
- **Later:** optional on-device LLM rewriting for variety. Not in v1.

### 11.2 Goal line examples

| State | Line |
|---|---|
| r ≥ 1.0 | "Weekly goal: 162 / 150 min. Your world holds strong." |
| 1.0 < r ≤ 1.5 | "…The old quarter is being rebuilt." |
| 0.8–0.99 | "Weekly goal: 128 / 150 min. Barely a whisper of weather." |
| < 0.8 | "Weekly goal: 60 / 150 min. Nature is stirring at the edges." |

---

## 12. Economy (Phase 3)

### 12.1 Earning
- **Gold:** `1 × moderate min + 2 × vigorous min + 2 × outdoor km`, at full rate for the first 90 intensity-minutes per day, 25% after that.
- **Materials:** 1 unit per 0.5 km moved in a cell of that biome; stone also gets 1 unit per 20 m of elevation gain.
- Gold and materials come from movement only. Trade creates neither.

### 12.2 Trade network
- A graph of settlements connected by worn roads (inside a region, any path of edges with W ≥ 3 between two settlements) and travel routes (between regions).
- **Access:** a settlement's **goods** (its biome materials plus regional specialties) can be bought with gold at any settlement on the same connected network.
- **Prosperity** (separate from tier): `f(tier, connected settlements, distinct goods reachable)`. It drives visuals, project variety and townspeople flavor. **It never raises tier.**

### 12.3 Projects
- At most **one proposal per day**, from a settlement chosen by recency of visits and tier. Approve or decline; declined proposals come back after a cooldown; nothing expires.
- **Starting catalog:**

| Project | Min tier | Cost (example) | Effect |
|---|---|---|---|
| Well | Hamlet | 50 g, 10 stone | Cosmetic; +prosperity |
| Market square | Village | 150 g, 20 stone | Network goods reach +1 hop |
| Bridge | Village | 120 g, 30 timber | Connects networks across water edges |
| Inn | Town | 200 g, 40 brick | Travelers in dispatches; +liveliness ceiling |
| Pier / Harbor | Town, coastal | 200 g, 40 timber | Enables sea lane visuals and goods |
| Lighthouse | Town, coastal | 300 g, 60 stone | Landmark |
| Walls | Town | 300 g, 80 stone | Cosmetic |
| Cathedral / Observatory | City | 800 g, mixed + specialty goods | Landmark |

### 12.4 Monuments (Phase 3, free)
Automatic on milestones: first 5K / 10K / half-marathon distance in one outdoor session, first 100 km calendar month, first new region, 52 weeks of goals met (not necessarily consecutive).

---

## 13. Townspeople (Phase 4), outline only

- **Settlement character** from movement mix: mostly running → *messenger hub*; long slow weekend walks → *market town*; hilly → *highland outpost*; coastal → *fishing village*.
- **Notes** are generated from world state: things like quiet roads near the fog, long-unvisited settlements, undiscovered edges next to familiar ones. They're never required, never expire, never penalize.
- **Travelers** in dispatches show trade activity along routes ("A trader from Old Denver arrived with silver").

---

## 14. Data model (Room / SQLite)

**Observations (source of truth, append-only)**
| Table | Key columns |
|---|---|
| `track_point` | id, ts, lat, lng, accuracy, speed, segmentId (deleted after 90 days) |
| `segment` | id, startTs, endTs, mode, matched (bool) |
| `edge_coverage` | edgeId, day, coveredIntervals (blob) |
| `cell_presence` | h3, day, movingSeconds, mode |
| `elevation_gain` | h3, day, meters |
| `region_presence` | ts, lat, lng (coarse) |
| `daily_intensity` | day, moderateMin, vigorousMin, source |

**Geo cache**
| Table | Key columns |
|---|---|
| `edge` | edgeId, wayId, geometry, lengthM, name, highwayType, snapshotTs |
| `area` | areaId, name, placeType, frozenAt |
| `cell_area` | h3 → areaId |
| `cell_biome` | h3 → biome |
| `geo_tile` | h3res6, fetchedAt, version |

**Derived (rebuilt by replay)**
| Table | Key columns |
|---|---|
| `edge_state` | edgeId, discovered, wear W, discoveredDay |
| `cell_state` | h3, familiarity F |
| `settlement` | areaId, regionId, D, peakD, tier, peakTier, foundedDay, lastAreaDay, position |
| `region` | regionId, centroid, isHome, dormant, layoutX/Y |
| `route` | regionA, regionB, type, trips |
| `goal_state` | weekStart, goal, locked, minutes |
| `world_event` | id, day, type, payload (feeds dispatch and history) |

**Player state (not derived)**
`settings` (home area, rest mode, dispatch time, goal lock), `inventory` and `project` (Phase 3: approvals are player decisions, so they're logged as inputs and replayed too).

---

## 15. Privacy and security

- All data stays on the device. Network calls only fetch map, place and elevation tiles, using **tile-level** bounding boxes (never the exact track).
- Database encrypted at rest (SQLCipher or Android's encrypted storage).
- **Backup:** encrypted export file the user saves where they like; Android Auto Backup with client-side encryption as an option.
- No analytics or crash reporting that includes location.

---

## 16. Permissions

| Permission | Why | When requested |
|---|---|---|
| `ACCESS_FINE_LOCATION` | GPS capture | Onboarding |
| `ACCESS_BACKGROUND_LOCATION` | Passive capture and coarse presence | After the first walk, with an explanation |
| `ACTIVITY_RECOGNITION` | Movement detection | Onboarding |
| `FOREGROUND_SERVICE_LOCATION` | Capture service | Manifest |
| `POST_NOTIFICATIONS` | Dispatch and capture notification | Onboarding |
| Health Connect reads: activity intensity, exercise, steps, steps cadence | Weekly goal | Onboarding (verify exact permission names in S3) |

---

## 17. Testing and tuning

- **Engine unit tests:** pure Kotlin. Founding, tier changes, decay curves, floors, restoration, goal adjustments, rest mode, dormancy.
- **Simulation harness:** synthetic personas (daily commuter, weekend hiker, traveler, injured for a month, overtrainer) run for 2 simulated years. Results are checked against intended feel, e.g. "a 3×/week walker reaches Town at home in about 3 months," "an injured month never drops a City," "an overtrainer earns nothing extra above 150%."
- **Replay tests:** recorded real tracks → map matching → expected edges.
- **Debug fast-forward:** apply params and view the world at day N.
- **Field test:** the owner uses it daily from Phase 1; the params get tuned from real experience.

---

## 18. Plan

### 18.1 Spikes (before Phase 1 build)
| # | Spike | Exit criteria |
|---|---|---|
| S1 | Background capture triggered by activity detection on Android 14+ | Reliably captures ≥ 90% of real walks over 1 week with ≤ 5% battery/day |
| S2 | On-device HMM map matching | ≥ 90% edge accuracy on 10 test walks |
| S3 | Health Connect intensity data on the owner's devices | A daily intensity number that matches the source app within 10% |

### 18.2 Phase 1 milestones
1. **M1 Capture:** detection, foreground service, filtering, segments, raw storage, live track on a plain map.
2. **M2 Geo:** Overpass fetch and cache, road graph, areas, biomes, map matching, edge coverage.
3. **M3 Engine:** observations → discovery, wear, familiarity, founding and tiers; replay; unit tests.
4. **M4 Real-map view:** fog, peek ahead, worn roads, names, settlements.
5. **M5 Health + protection:** Health Connect sync, goal, decay, restoration, rest mode, grace period, goal screen.
6. **M6 Dispatch + Atlas:** daily tick scheduling, templated dispatch, notification, archive, Atlas and settlement cards.
7. **M7 Polish + field test:** onboarding, permission flows, encrypted backup, battery tuning, then live with it for 4 weeks and tune.

---

## 19. Decisions needed

| # | Question | Recommendation |
|---|---|---|
| Q1 | Which phone and wearable do you use? (affects S3) | — |
| Q2 | Visual style | Hand-drawn cartography (§10.3) |
| Q3 | Does cycling count? | Yes for revealing roads; 50% weight for familiarity and materials (§4.2) |
| Q4 | Long-distance route types | Road / sea lane / air by distance and coast (§9.2) |
| Q5 | Dispatch time | 07:30 default, user-adjustable |
| Q6 | Working title | Open |
