# Wander World: Design Doc

> **Status:** Draft v0.2. Builds on [`concept.md`](concept.md).
> **Target devices:** Pixel Fold 10 + Pixel Watch 4.
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
| D7 | **MapLibre for the real-map view**, drawing our own GeoJSON layers. | Full style control for the practical view; no Google Maps terms or costs. |
| D8 | **A 3D engine for the world view**: a tilt-shift realistic diorama on H3 hex tiles (§10.3). | A realistic miniature look needs real 3D, lighting and depth of field, which a map renderer can't do. Our hex grid doubles as the board for a civilization-style look. |
| D9 | **Two GPS sources:** the phone's own capture and watch workouts imported from Health Connect (with their routes). | The Pixel Watch 4 records GPS on its own, so runs without the phone still build the world. |
| D10 | **Vehicles and flights never reveal roads.** They create **waystones** where you first set foot (§9). | Exploration stays earned on foot; travel still connects the world. |

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
- **`app`**: UI, real-map rendering, permissions, WorkManager scheduling. Adaptive layouts for the Fold (§10.5).
- **`world-render`**: the 3D world view (§10.3), embedded in the app and fed by world state.

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
| Watch workouts | Pixel Watch 4 GPS workouts sync (via the Fitbit app) to Health Connect as `ExerciseSessionRecord` with an exercise route. Imported in the daily sync and run through the same filter → map matching pipeline. If a watch route and a phone segment overlap in time, keep the denser track. |

> **🧪 Spike S1 (highest risk): background capture on Android 14+.** Android limits starting a location foreground service from the background. We must verify, on the owner's actual phone, that an activity-transition broadcast can reliably start capture. Fallbacks, in order:
> (a) A long-running, low-priority foreground service during waking hours, idling cheaply until movement is detected.
> (b) Background location updates (throttled to a few per hour by Android, too sparse for roads, so only for familiarity at the cell level).
> (c) Manual sessions only, with everyday walking credited at the cell level from (b).
> The whole "passive idle" experience depends on S1. Do it first.

### 4.2 Filtering

- Drop fixes with reported accuracy > 30 m.
- Speed sanity: sustained > 7 m/s → treat as vehicle; those points never count toward exploration or familiarity. Cycling (`ON_BICYCLE`) is **for discovery, not establishing**: it reveals roads at full credit but adds little to settlements (weights in §7.1).
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
- An edge's **wear** goes up by 1 per **distinct day** it's covered by ≥ 50% (or ≥ 150 m) on foot; 0.25 by bike.

### 4.5 Observations written

| Observation | Fields |
|---|---|
| `EdgeCoverage` | edgeId, day, coveredIntervals |
| `CellPresence` | h3Cell, day, movingSeconds, mode (walk/run/cycle) |
| `ElevationGain` | h3Cell, day, meters |
| `RegionPresence` | regionCandidate (lat/lng coarse), timestamp |

Raw GPS points are kept for **90 days** (for re-matching after improvements), then deleted. Observations are kept forever.

---

## 4.6 Beyond roads: your path and what it passed through (alpha 0.1.4–0.1.5)

Field testing showed roads alone miss much of real walking: stores, parking lots, parks, bike trails. Discovery now has three layers:

| Layer | What it is | Effect |
|---|---|---|
| **Your path** | Every usable GPS run, simplified (Douglas–Peucker, 4 m) and kept forever | Fog clears 35 m either side; fogged pockets under ~2 ha enclosed by explored ground clear too |
| **Mapped ways** | OSM roads, footways, paths, cycleways, tracks | Charted by name, wear in (unchanged) |
| **Named places** | OSM parks, gardens, woods, beaches, nature reserves, cemeteries, campuses, zoos, big-box stores (≥ 2,000 m²) | ≥ 60 s inside on a day discovers it ("Discovered Thompson Park"); places ≤ 5 ha clear completely; listed in the Atlas |
| **Your own paths** | Stretches ≥ 30 m with no mapped way within 25 m (cut-throughs, park lawns, unmapped trails) | Drawn as footpaths; wear per H3 res-11 cell (~25 m) by distinct days, same levels and decay as roads |

## 4.7 Field-test tuning (alpha 0.4.1)

- **Moving around a building isn't a walk.** A stretch whose GPS fixes stay within 45 m of their centre (pottering at home, GPS drifting indoors) earns nothing: no roads, fog, familiarity or arrivals. Raw points are kept, so the threshold can change and walks get re-processed (`PROCESSING_VERSION`). No data is lost.
- **Worn roads clear more fog.** The cleared radius grows with wear level: 40 / 55 / 75 / 100 / 130 m for levels 1–5, and the same for your own paths (minimum 35 m). A loop you walk often fills in over time; new routes fill faster.
- **Great waystones** only appear when arriving in a different region than the one you came from.
- **Dispatch** groups waystone finds into one line per region.

## 4.9 Paddling is automatic (alpha 0.7.0)

A stretch counts as paddling when at least 60% of its fixes are inside mapped water (lakes, ponds, reservoirs, riverbanks, bays). On a mapped coast it also counts when 70% of fixes are more than 80 m from any road, at a median speed of 0.4–3.5 m/s, and not on a beach.

When the phone says "in vehicle", a GPS probe starts:
- Car speeds end it within 45 s, and no new probe starts for 20 minutes.
- A probe recording only keeps its time on water.
- If you start walking during a probe, it hands over to a normal walk.

"Still" no longer ends a recording while GPS shows movement. Watch paddling, rowing, surfing and open-water swims import as paddling. There is no paddle button.

## 4.10 Map clarity, waystone placement, resources on the map (alpha 0.8.0)

From field test 0.7.0 (capital icons piling up; a waystone floating in fog):

- **One medallion per settlement.** The capital star and the monument obelisk are baked into the settlement
  medallion at load time (`medal_tier_N_cap`, `_capmon`), so they scale together instead of drifting apart.
- **Marker priority.** Settlements always show and claim their space; then waystones, places, waypoints. A lesser
  marker that would overlap a greater one hides until you zoom in far enough for both. Names travel with their
  icon and drop first (`text-optional`).
- **Waystones sit where you set off on foot.** The arrival point is the median of the first ~2 minutes of fixes
  after a drive, and a waystone's position is the average of every arrival that found or returned to it, so
  repeat visits settle it onto the car park. No waystone within 2 km of home, even on day one. Each waystone
  clears a 45 m patch of fog around itself.
- **Inventory on the map.** A resource bar sits under the dispatch card. Tapping a resource flies to its nearest
  source.
- **Nearest-source pointers** (toggle in Settings): wood (woods/parks), food (water, beaches, harbours), stone
  (nearest street you haven't walked), metal (nearest place you haven't discovered, else one you have). Computed
  only from map data already on the phone, within ~8 km. On screen they mark the spot; off screen they wait at the
  map's edge with an arrow and the distance.

### 4.10.1 Pointers wherever you are (alpha 0.9.2)

- Pointers search from your current position. The app fetches roads and places for the tile you're standing in,
  even if you haven't walked there (a store car park). That's one area request, the same kind a walk makes.
- Stone points to the nearest mapped street you haven't walked, from the tiles around you, not only streets at the
  edge of explored ground.
- When nothing is mapped nearby, a line under the resource bar says so, instead of the map staying silently empty.
- The Settings toggle takes effect on the map immediately.
- Place downloads: relations (multi-part areas such as rivers and big lakes) are first fetched as bounding boxes;
  only those under 6 km across are fetched whole. One river-sized relation had blocked a home tile's places
  since 0.7.0.

## 4.8 Design objects v1 (approved direction, alpha 0.5.0, extended in 0.6.0)

The legend is in-app (map → **?**) and in `docs/art/legend.png`, generated by `tools/gen_legend.py` from the same textures the map uses.

| Group | Object | Visual | Rule |
|---|---|---|---|
| Ground | Fog | parchment, translucent | unexplored |
| | Explored ground | 0 A.D. grass | cleared by walks; radius grows with road wear |
| | Woods & parks | 0 A.D. forest floor | discovered green places |
| Roads | Trail | sand dashes, 2.5 px | 1 day |
| | Footpath | dirt texture, 3.5 px | 3 days |
| | Lane | dirt texture + dark edge, 5 px | 10 days |
| | Cobbled road | cobblestone + stone edge, 6.5 px | 30 days |
| | Avenue | cobblestone + edge + tree dots both sides, 8.5 px | 100 days |
| | Your own path | pale sand dashes | off-map path; same wear rules |
| | Unwalked street | faint dotted ink | hint at the fog's edge |
| Own paths (0.6) | Faint track → Trodden path → Dirt track → Gravel trail → Ranger trail | dotted → dashed → dirt → gravel + edge → gravel + cairns | 1 / 3 / 10 / 30 / 100 days, per ~25 m cell |
| Water routes (0.6) | Wake → Paddle line → Paddle route → Marked route → Waterway | dotted → dashed → solid + edge → buoys one side → buoys both sides | same thresholds; paddling clears fog over water |
| Ground (0.6) | Sand & beaches · Water | 0 A.D. sand · original ripple texture | discovered beaches; base map water |
| Places (0.6) | Hospital, government, police & fire, civic, school, library, worship, culture, sports, station, harbour, lighthouse, historic, beach, lake | 0 A.D. portraits in gold (civic), blue (water) or sand (beach) rings; hospital cross is original | point POIs get a 35 m circle and need 15 s nearby |
| You (0.6) | Walking · Paddling | original pixel-art wanderer with lantern staff · on a paddleboard | paddle sprite while a Paddle recording runs |
| Settlements | Ruins → Metropolis | gold-ring medallions (0 A.D. portraits) | tiers §7.3 |
| | Capital | star badge on its medallion, pinned to home | |
| Markers | Waypoint | bronze-ring outpost medallion | every area walked in |
| | Waystone / Great waystone | blue ring / glowing double blue ring (original art) | §9.2 |
| | Places | green ring (park, woods), gold ring (store, other) | |
| | You | the wanderer sprite | |

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
- **Pixel Watch 4:** its activity data goes through the Fitbit app. Fitbit's **Active Zone Minutes** are heart-rate based and count cardio and peak zones twice, which is the same idea as our moderate + 2 × vigorous. If AZM reaches Health Connect as intensity records, we use it directly; heart rate makes it more accurate than step-based estimates.
- **🧪 Spike S3:** confirm what the Pixel Watch 4 / Fitbit app writes to Health Connect (intensity records, exercise sessions, **exercise routes**) and which read permissions the routes need.

### 6.2 The goal

- **Protection ratio** `r = Σ intensity minutes over the trailing 7 days ÷ goal`, recalculated daily.
- **Goal adjustments** run once a week on a fixed day (Monday), using calendar weeks:

| Rule | Logic |
|---|---|
| Increase | If ≥ 3 of the last 4 weeks have `minutes ≥ 1.2 × goal`, and the last raise was ≥ 4 weeks ago: `goal = min(ceiling, round10(goal × 1.10))` |
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

- **Mode weights** (discovery vs. establishing):

  | Mode | Reveals roads | Wear | Familiarity / ΔD | Materials | Counts toward founding a camp |
  |---|---|---|---|---|---|
  | Walk / run | Full | 1.0 | 1.0 | 1.0 | Yes |
  | Cycle | Full | 0.25 | 0.25 | 0.5 | No: only on-foot days found settlements |
  | Vehicle / flight | Never | — | — | — | No: creates waystones only (§9) |

### 7.2 Founding

An area founds a **Camp** once it has **≥ 3 on-foot area-days within any 30-day window** (so a weekly ritual counts). Founding is an event and goes into the history and the dispatch.

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

## 9. Regions, waystones and dormancy (Phase 2, built in alpha 0.3.0)

> **Implementation notes (0.3.0):**
> - Regions are seeded by the first place you walk in an area more than 100 km from existing regions.
> - Arrivals are detected two ways: walking resumes more than 1 km away after vehicle-speed travel within one recording, or a recording starts more than 2 km from where the previous one ended.
> - The home region is the one containing the capital.
> - Pockets joined by walking (lesser waystones becoming landmarks) are not built yet.

### 9.1 Regions
- A **region** is a cluster of areas. The home region is created at onboarding.
- A **new region** is created when coarse presence stays > 100 km from every existing region's centroid for ≥ 6 hours (so passing through an airport doesn't count).
- Areas join the region whose centroid is nearest (within 100 km).

### 9.2 Waystones
Vehicles and flights **never reveal roads**. Instead, travel means **finding waystones**: portal stones that appear where you first set foot after the trip.

- **Placement:** after a vehicle segment (`IN_VEHICLE`, sustained > 7 m/s) or a flight (a large position jump with no samples in between), the first on-foot fix becomes a waystone candidate. It's placed if that spot is **≥ 2 km from any discovered edge** (not reachable through your explored roads). Otherwise you've just returned to known ground and nothing is created.
- **Two kinds:**
  - **Great waystones** sit between regions. Finding one in a new region creates the region and a portal link back to the region you came from. These portal links are what connect the islands of the world view.
  - **Lesser waystones** sit within a region. They mark a separate **pocket** of explored ground (e.g. a park you drive to). Pockets connect to the rest of the region **only by walking** between them: once a chain of discovered edges joins two pockets, they merge and the waystone becomes a landmark.
- **Repeat trips** brighten a waystone and its portal link (cosmetic), and the dispatch notes it ("The Honolulu stone hums again").
- **Trade:** portal links connect regional trade networks (§12.2). Inside a region, trade flows only along walked roads, which gives you a reason to walk the gap between pockets.

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

**Style: civilization-style world development, tilt-shift realistic.** The world reads as a living miniature: realistic materials and lighting, viewed from an oblique, overhead camera, with shallow depth of field so it looks like a model on a table.

**The board is the hex grid.** Each H3 res-9 cell is a terrain tile, which gives the world a civilization-game board for free:
- **Terrain** comes from the cell's biome (§5.4) and real elevation (gentle relief): woods, fields, highlands, coast, urban ground.
- **Roads** are drawn across tiles along their real paths, styled by wear: trodden dirt → gravel → cobbles → paved → lamplit avenue.
- **Settlements** occupy their center tile and **spread onto neighboring tiles as they grow**: Camp and Hamlet 1 tile, Village + 1 ring, Town + 2 rings, City and Metropolis + 3 rings, built with buildings that fit the biome and region.
- **Ruins** keep their footprint: broken walls and overgrown foundations at the peak size.
- **Liveliness** shows as tiny figures, carts, smoke from chimneys and lit windows.
- **Fog** is low cloud and mist over unexplored tiles; the peek-ahead edges show as faint tracks disappearing into the mist.
- **Waystones** are standing stones that glow; portal links are arcs of light between islands.

**Layout:**
- **Landmasses:** each region's explored tiles plus a 1-tile ring, surrounded by sea.
- **Between regions:** place each region at its **real compass direction** from the capital, at a distance of `k × ln(1 + d / 100 km)`; then gently push overlapping regions apart while keeping their direction within ±20°. Recalculated only when a region is added, with an animated transition.
- **Zoom levels:** world (islands and portal links), region (settlements and roads), settlement (close-up of the miniature).

**Look recipe:** physically based materials, warm directional sunlight with soft shadows, a slightly raised camera angle (~35–50°), **depth-of-field blur** focused on the center of the screen, a little extra saturation and contrast. Optional time of day matching your local time.

**Engine:** this needs a real 3D renderer embedded in the Android app.
| Option | Pros | Cons |
|---|---|---|
| **Godot 4 (embedded as an Android library)** (recommended) | Full scene editor for building assets and tiles, built-in depth of field, free and open source, small enough to embed | Bridge between Kotlin and Godot to build; extra app size |
| Filament (Google's renderer) | Very light, native to Android, strong materials and depth of field | Only a renderer: terrain, instancing, scene tools all written by hand |
| Unity as a Library | Mature tools and asset store | Heavy, licensing costs, awkward to embed |

> **🧪 Spike S4:** embed Godot in a Compose app on the Pixel Fold 10. Render a 2,000-tile hex region with settlements and depth of field at 60 fps, feeding it world state from Kotlin.

**Assets** are the biggest cost of this style. Plan: a modular kit (terrain tiles per biome, road pieces per wear level, building sets per biome × tier, ruins variants, props), starting from licensed realistic asset packs and adjusted for a consistent look. Phase 2 starts with one biome set (temperate, home) and adds others as regions appear.

### 10.4 Pixel Fold layouts
- **Folded (phone):** single pane; the map fills the screen with the dispatch card on top.
- **Unfolded (tablet):** map or world view on the large pane, with a side panel for the dispatch, Atlas or settlement card. Exploring the world is best unfolded.
- **Half-folded (tabletop):** world view on the top half, controls and the dispatch on the bottom half.
- Built with Compose window size classes; no fixed layouts.
- **Later idea:** a Pixel Watch tile showing weekly goal progress and protection state.

### 10.5 Notifications
- **One per day:** the dispatch, at the user's chosen time (default 07:30).
- **Required by Android:** the tracking foreground-service notification while capturing. Kept minimal ("Charting your walk · 1.2 km").
- **Nothing else.** No nudges, no reminders, no "you're about to lose…" messages.

---

## 11. The daily dispatch

### 11.1 Generation
- Built from the previous day's **world events** in priority order: founding / tier-up → new region / waystone → charted roads (up to 3 named) → materials and arrivals (Phase 3+) → goal status → one proposal (Phase 3+) → townspeople note (Phase 4).
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

## 12. Economy (Phase 3, built in alpha 0.7.0)

> **As built (0.7.0).** Rules live in `engine/.../Economy.kt`:
> - **Gold:** intensity minutes (vigorous ×2), full rate to 90 a day then ¼, plus 2 per outdoor km.
> - **Wood:** 1 per 5 min in woods or parks.
> - **Food:** 2 per paddled km, plus 1 per 10 min at beaches or lakes.
> - **Stone:** 1 per km walked on mapped roads.
> - **Metal:** 3 for discovering a point of interest, 1 per revisit day.
> - **Projects:** campfire, well, market, bridge*, inn, pier*, lighthouse*, walls, great hall (*only near water). One proposal at a time, from the most recently visited settlement, cheapest first. Declined projects return after 14 days; nothing expires. Decisions are stored as inputs and replayed.
> - **Prosperity:** the sum of built projects. It never changes tier.
> - **Monuments:** 5 km / 10 km / half-marathon days; 100 / 500 km total; first paddle, waystone and region; 4 / 12 / 52 weeks with the goal met.
> - **Trade networks and specialty goods** are not built yet.


### 12.1 Earning
- **Gold:** `1 × moderate min + 2 × vigorous min + 2 × outdoor km`, at full rate for the first 90 intensity-minutes per day, 25% after that.
- **Materials:** 1 unit per 0.5 km moved in a cell of that biome; stone also gets 1 unit per 20 m of elevation gain.
- Gold and materials come from movement only. Trade creates neither.

### 12.2 Trade network
- A graph of settlements connected by worn roads (inside a region, any path of edges with W ≥ 3 between two settlements) and portal links between great waystones (between regions).
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
| Pier / Harbor | Town, coastal | 200 g, 40 timber | Coastal goods; harbor visuals |
| Waystone shrine | Village with a waystone | 250 g, 50 stone | Doubles goods reach through that waystone's portal links |
| Lighthouse | Town, coastal | 300 g, 60 stone | Landmark |
| Walls | Town | 300 g, 80 stone | Cosmetic |
| Cathedral / Observatory | City | 800 g, mixed + specialty goods | Landmark |

### 12.4 Monuments (Phase 3, free)
Automatic on milestones: first 5K / 10K / half-marathon distance in one outdoor session, first 100 km calendar month, first new region, 52 weeks of goals met (not necessarily consecutive).

---

### 12.x Discovery bonus and harvest pressure (alpha 0.9.0)

Finding new ground should pay better than frequenting the same spot, and an old haunt left alone should become
worth revisiting. Every source has an **abundance** from 0 to 1: each wood, park, beach or lake (wood/food), each
place of interest (metal), each street (stone) and each ~25 m patch of water you paddle (food).

- **Discovery bonus:** the first harvest from a source you've never touched pays ×2 (metal: 3 on discovery).
- **Thinning:** each day you harvest a source, its abundance drops 0.15, down to a floor of 0.3.
- **Regrowth:** each day it rests, abundance climbs 0.05 back toward 1 (from the floor, ~2 weeks).
- Yield = base × abundance at the time. A daily park walk settles at about a third of the yield; the same park
  every third day stays near full.
- The dispatch notes when a place drops below half ("thinning… give it a rest"). The Atlas shows each place as
  flourishing, recovering, worn, or thinned.
- Map pointers pick the best value, yield per distance, so a fresh wood a little further away beats a thinned
  one next door, and they label never-harvested sources "new".
- Gold is untouched: effort is never penalized for being routine.

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
| `vehicle_segment` | startTs, endTs, kind (vehicle/flight), endLat, endLng |
| `watch_route` | hcSessionId, startTs, endTs, imported (bool) |
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
| `waystone` | id, h3, regionId, kind (great/lesser), foundDay, trips, merged (bool) |
| `portal_link` | waystoneA, waystoneB, trips |
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
| Health Connect reads: activity intensity, exercise, steps, steps cadence, **exercise routes** | Weekly goal; watch workouts build the world | Onboarding (verify exact permission names in S3) |

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
| S3 | Health Connect data from the Pixel Watch 4 (intensity, sessions, routes) | A daily intensity number within 10% of the Fitbit app; watch routes import and match |
| S4 | Godot embedded on the Pixel Fold 10 (§10.3) | 2,000-tile region with settlements and depth of field at 60 fps, driven from Kotlin (needed before Phase 2) |

### 18.2 Phase 1 milestones
1. **M1 Capture:** detection, foreground service, filtering, segments, raw storage, live track on a plain map.
2. **M2 Geo:** Overpass fetch and cache, road graph, areas, biomes, map matching, edge coverage.
3. **M3 Engine:** observations → discovery, wear, familiarity, founding and tiers; replay; unit tests.
4. **M4 Real-map view:** fog, peek ahead, worn roads, names, settlements.
5. **M5 Health + protection:** Health Connect sync, watch route import, goal, decay, restoration, rest mode, grace period, goal screen.
6. **M6 Dispatch + Atlas:** daily tick scheduling, templated dispatch, notification, archive, Atlas and settlement cards.
7. **M7 Polish + field test:** onboarding, permission flows, encrypted backup, battery tuning, then live with it for 4 weeks and tune.

---

## 19. Decisions

| # | Question | Decision |
|---|---|---|
| Q1 | Devices | Pixel Fold 10 + Pixel Watch 4 (§6.1, §10.4) |
| Q2 | Visual style | Civilization-style world development, tilt-shift realistic, on hex tiles (§10.3) |
| Q3 | Cycling | Counts for discovery; minimal establishing; can't found settlements (§7.1) |
| Q4 | Long-distance travel | Vehicles and flights reveal nothing; they create waystones / portals (§9.2) |
| Q5 | Dispatch time | 07:30 default, adjustable |
| Q6 | Title | **Wander World** |
| Q8 | Art source | **0 A.D. (Wildfire Games)**: open-source RTS in the Age-of-Empires style. Art is CC BY-SA 3.0: commercial use allowed with attribution; modified art must stay CC BY-SA. Models are COLLADA (.dae), importable into Godot. Use as the base kit for the tilt-shift world view (§10.3). |
| Q7 | Visual detail | **Deferred.** Build a lightweight chassis with placeholder visuals; rendering stays separate from game logic so art can be added later without touching the engine. |

**Still open**
- Engine choice confirmed by Spike S4 (Godot recommended), when visuals come into scope.
- Source and budget for 3D assets (deferred with Q7).
- Whether the real-map view gets a light touch of the diorama style (e.g. settlement miniatures as map pins).
