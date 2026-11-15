# Wander World: Concept

> **Title:** Wander World
> **Status:** Concept agreed. Next step is the design doc.
> **Platform:** Android (native)
> **Audience:** One player. Solo, with no competition.

---

## 1. Vision

You build a civilization by moving.

Walks, runs and other outdoor cardio uncover real roads from the fog of war. Places you return to grow from camps into cities. Your weekly health habits protect what you've built, and your travels link far-apart settlements into a trade network. Over years, the world becomes a personal map of your life: a capital at home, an old city where you used to live, a small island village from a trip, all with their own history.

This is **not** a territory game. There are no rivals, leaderboards, clubs or capture. The only thing taking the world back is nature, and only when you stop moving.

### What makes it different

| Existing category | Examples | What it gets wrong for us |
|---|---|---|
| Territory capture | INTVL, KYRO, Stride, RunWar, Territory Conquest | Built on rivalry: other people take your ground |
| Coverage / fog of war | Wandrer, CityStrides, City Wanderer, Fogbreaker | Rewards only new streets, so your regular routes earn nothing once covered |
| Location games | Pokémon GO, Pikmin Bloom | Activity gains fade after about 6 weeks; the game is about the creatures, not your movement |

This concept rewards **both** returning to familiar places (habit) and going somewhere new (exploration). It turns your activity level into a world that reflects your real lifestyle.

---

## 2. Design principles

These guide every later decision. If a feature breaks one, the feature changes.

1. **Your job is to go outside; the world's job is to respond.**
2. **You can only earn things by moving.** Nothing grows, pays out or advances while you sit still.
3. **The phone is for looking at what you earned, not managing it.** No busywork, no optimizing, no supply chains to tend.
4. **Nothing expires.** No daily quests, countdown timers or limited-time events.
5. **Rest is never punished.** Rest weeks, illness and injury can pause decay entirely.
6. **Reward consistency over intensity.** A steady habit grows the world more than occasional big efforts. The rewards stop increasing at overtraining levels.
7. **Start at zero, honestly.** No importing past activity. The journey is part of the game.
8. **Private by default.** Location data stays on the device.

---

## 3. The core loop

```
 Move (walk / run / outdoor cardio)
        │
        ├─► Explore ──► real roads come out of the fog, with their real names
        ├─► Return  ──► trails wear in; settlements form and grow
        ├─► Earn    ──► gold + terrain materials
        └─► Count   ──► intensity minutes toward the weekly goal
                              │
                              ▼
                     Weekly goal protects / restores the world
                              │
                              ▼
   Daily dispatch: what was charted, what grew, what arrived, what's proposed
```

There are two separate axes:

- **Where you go grows the world:** what gets discovered, which settlements grow, what materials you collect.
- **How much you move protects the world:** total weekly intensity minutes, from anywhere, including indoor exercise.

Because protection doesn't depend on location, there's no need to plan routes to keep every neighborhood maintained.

---

## 4. Exploration and the map

### 4.1 Fog of war
- The whole world starts in fog.
- A road appears only once you've walked it, and gets its **real name** (from OpenStreetMap) when you do.
- **The fog never returns.** Discoveries are permanent.
- **Peek ahead:** streets right next to where you've been, about one block away, show faintly at the fog's edge as unnamed hints. You can see your options without the map being revealed.

### 4.2 Trail wear
Roads wear in with repeat use, like the dirt paths people wear into grass. Each road segment has a wear level based on how many separate days you've walked it:

| Wear level | Days walked (starting value) | Look |
|---|---|---|
| Faint trail | 1 | Thin dotted line |
| Path | 3 | Worn dirt path |
| Cobblestone | 10 | Laid stone |
| Road | 30 | Paved road |
| Avenue | 100 | Lit avenue with trees |

Wear counts separate days, not laps, so walking the same block repeatedly in one day doesn't add up. During long inactive stretches, wear can drop slowly (see §6) but never below a faint trail.

### 4.3 Two map styles
The **map is the home screen**, with a toggle between:

- **Real map:** actual street layout, fog overlay, worn trails, and hints at the fog's edge. Useful for planning a walk.
- **World view:** a civilization-style, tilt-shift realistic miniature of your world on hex tiles, with settlements, trade, ruins, biomes, islands and waystones. For browsing what you've built.

The daily dispatch appears as a card on the map, one tap away. It never blocks opening the app and looking around.

---

## 5. Settlements

### 5.1 How they form
Settlements come from **how often you return**. You don't place them.

- The world is divided into hex cells (H3). Each cell builds **familiarity** when you move through it.
- Familiarity grows with **distinct days present**, with diminishing returns within a single day. Frequency matters, not volume.
- When an area (a neighborhood) passes a frequency threshold, a **camp** appears there by itself.
- **Exception:** your **capital** is founded at home on your first walk and is permanently fixed there.

### 5.2 Tiers

| Tier | Starting threshold: total visit-days in the area | Feel |
|---|---|---|
| Camp | 3 visit-days within 14 days | Tents, a fire |
| Hamlet | 10 | A few huts, a well |
| Village | 25 | Houses, a green |
| Town | 60 | Market, walls, an inn |
| City | 150 | Districts, towers, harbor or gates |
| Capital | Fixed at home; grows by the same rules | Seat of your civilization |

Early tiers come quickly (camp to hamlet in about a week of regular walking). Later tiers take a season or more. All numbers are starting values to tune.

### 5.3 Names and the atlas
- Settlements are named after the **real neighborhood** (OSM `place=neighbourhood` / `suburb` / `village`). If there isn't one, the nearest named place or a major street is used.
- Every name you earn goes into the **Atlas**, a collection of places you've built up, with their founding date, peak tier, distance walked there and last visit.

### 5.4 Liveliness (visual only)
Each settlement has a **liveliness** level based on how recently you visited (half-life about 30 days). It affects only visuals: people in the streets, lit windows, market stalls. A place you haven't visited lately goes quiet but **loses nothing**. Visiting brings it back to life.

---

## 6. Health, protection and decay

### 6.1 What gets measured
- The weekly goal uses **intensity minutes** from **Health Connect**: moderate minutes count once, vigorous minutes count twice, following standard health guidelines.
- **Any** cardio counts toward the goal, including treadmill, cycling, swimming and indoor sessions. Rainy weeks and winter are fully covered.
- Only **outdoor GPS movement** builds the world (exploration, familiarity, materials).

### 6.2 The weekly goal
- **Rolling 7-day window**, not daily, so a missed day is never a broken streak.
- **Starts at 150 minutes per week.**

### 6.3 How the goal adapts

| Rule | Value |
|---|---|
| Floor | 150 min/week |
| Ceiling | about 300 min/week (top of the health guidelines) |
| Goes up when | about 3 of the last 4 weeks are at or above 120% |
| Size of increase | at most 10% each time |
| Goes down | quickly and without penalty when recent weeks are lighter |
| Override | You can lock the goal at any time |

It goes up slowly and comes down quickly, so it can't push you toward something you can't sustain.

### 6.4 Protection tiers
Let **r** = this rolling week's intensity minutes ÷ goal.

| r | Effect |
|---|---|
| < 0.8 | Decay scales with the shortfall: rate = (1 − r) × base |
| 0.8 – 0.99 | Decay greatly reduced: rate = 0.5 × (1 − r) × base, at most 10% of base |
| 1.0 | **Decay stops completely** |
| 1.0 – 1.5 | **Restoration:** lost development rebuilds, faster the further above goal |
| > 1.5 | Nothing extra (the cap keeps overtraining from being rewarded) |

Extra minutes **restore**; they never **store** protection for later. Effort makes up for past slow periods instead of letting you coast in the future.

### 6.5 Decay rules
- **Starting base rate:** a fully inactive week removes about 5% of a settlement's development above its floor.
- **Bigger settlements are sturdier:** decay slows as tier rises. A camp fades in weeks; a city takes months.
- **Ruins are the floor:** a settlement can never drop more than **two tiers below its peak**, and never below **Ruins**. Ruins stay a visible, browsable part of the world.
- **The capital never drops below Village.**
- **Dormant regions** (see §7.2) decay at about 10% of the normal rate.
- **Ruins remember you:** rebuilding a decayed settlement costs much less than building it the first time.
- **Rest mode:** you can pause all decay for rest, illness, injury or travel with no penalty. After long pauses the app sends a gentle check-in, never a punishment.

---

## 7. The world at large

### 7.1 Regions as islands
- Settlements group into **regions** by geography (starting value: activity more than about 100 km from any existing region starts a new one).
- **Inside a region**, the real layout is kept.
- **Between regions**, empty distance is compressed. The world view shows regions as separate landmasses, roughly keeping their compass direction from the capital, linked by waystones (see §8.2).
- So the home region is the home continent, Denver becomes a mountain region, and Honolulu literally becomes an island.

### 7.2 Dormancy
- A region you haven't been in for about 14 days, other than your home region, goes **dormant**.
- Time there almost stops (decay at about 10% of the normal rate). The townspeople keep living there, quietly.
- Visiting wakes it up, and the townspeople remember you.
- Decay pressure is aimed at places close enough to walk, not places you moved away from.

### 7.3 History
Every settlement keeps a record: founded, peak tier and when, distance walked there, last visit, monuments built. Over years, the world becomes a record of your past self that you can browse.

---

## 8. Economy: you are the infrastructure builder

You don't run the economy; you make it possible. The townspeople live, trade and grow. Your movement gives them the roads to do it on.

### 8.1 Earning
- **Gold** comes from movement: intensity minutes and distance. Daily earnings taper above about 90 minutes so long sessions aren't over-rewarded.
- **Materials** come from the terrain you move through, using OSM land use and elevation:

| Terrain | Material |
|---|---|
| Parks / woodland | Timber |
| Hills / elevation gain | Stone |
| Waterfront / coast | Fish, salt |
| Urban / dense streets | Brick |
| Farmland / open fields | Grain |
| Regional climate (e.g. tropical) | Specialty goods (spices, coral, …) |

This gives you a reason to vary your routes: need stone, take the hill loop.

### 8.2 Infrastructure and trade
- **Walking lays roads** inside a region (§4.2).
- **Vehicles and flights never reveal roads.** Instead, arriving somewhere by car or plane means **finding a waystone**: a portal stone where you first set foot. Waystones between regions link them into one network; within a region, they mark separate pockets of explored ground, which you can join up only by walking between them.
- **Trade moves along your network.** Connected settlements trade with each other.
- **Trade creates prosperity and access, never gold.**
  - *Prosperity* makes towns visibly thrive (busier markets, richer buildings, better project proposals).
  - *Access* means a connected town's goods can be bought with gold anywhere on its network. Honolulu goods at your capital require a route to Honolulu.
- **Settlement tier comes only from your movement.** Prosperity is a separate quality, so towns never grow while you sit still.

> **Firm rule:** gold comes only from movement, never from trade. Otherwise the game would pay you for sitting still.

### 8.3 Projects
- Towns **propose** projects; you approve or decline. You never place buildings by hand.
  - e.g. *"Kailua harbor wants a pier: 200 gold, 40 timber."*
- No more than about one proposal per day, in the dispatch. Declined proposals can come back later; none expire.
- Projects add character and strengthen the network: markets (wider trade reach), bridges, piers and harbors (enable sea routes), inns, lighthouses, walls.

### 8.4 Milestone monuments
Fitness milestones build monuments automatically, at no cost:
- First 5K / 10K / half marathon distance
- First 100 km month
- First new region founded
- A year of weekly goals met

---

## 9. The daily dispatch

Once a day (morning), a short summary of what happened since the last one:

> **Tuesday's Dispatch**
> You walked 6.2 km. Charted 3 new roads: Maple Ave, Ridge Rd, Cedar Ln.
> Hamlet of Ridgefield grew into a Village. The western woods yielded 12 timber.
> A trader from Old Denver arrived at the capital with silver.
> Weekly goal: 112 / 150 min, world holding steady.
> *Proposal:* Ridgefield asks for a market square (150 gold, 20 stone).

- One dispatch per day. No other routine notifications.
- Tone is warm and story-like, never guilt-tripping. Inactivity reads as "the world is quiet," not "you failed."

---

## 10. Townspeople (later phase)

- Each settlement develops a **character** from how you move there: a town you mostly run through becomes a messenger hub; a weekend strolling spot becomes a market town.
- Townspeople leave small notes that act as **gentle route suggestions in story form**:
  - "The harbor road has gone quiet."
  - "Someone has heard of land past the river."
  - "The old quarter misses you."
- Notes are **never required, never expire and never carry penalties.**

---

## 11. Technical approach (Android)

| Area | Approach |
|---|---|
| Language / UI | Kotlin, Jetpack Compose |
| Map rendering | Real map: MapLibre Native. World view: a 3D engine with a tilt-shift diorama look (see design doc) |
| Map data | OpenStreetMap: vector tiles (e.g. Protomaps/PMTiles or a hosted provider) plus place names, land use and road geometry |
| Elevation | Open elevation data (for hills and the stone biome) |
| Spatial grid | Uber H3 (`h3-java`); about resolution 9 (~0.1 km²) for familiarity cells, grouped into neighborhoods via OSM boundaries |
| Road matching | Snap GPS tracks to OSM road segments on the device |
| Health data | **Health Connect:** `ActivityIntensityRecord` for intensity minutes (verify against current docs), plus `ExerciseSessionRecord` and `StepsRecord`. Fallback: estimate moderate minutes from step cadence (about 100+ steps/min) |
| Passive tracking | Activity Recognition Transition API detects walking or running and switches on location updates only then (a foreground service of type `location`); GPS off when still |
| Storage | Room / SQLite on device |
| Privacy | All location data stays on device; optional encrypted backup |
| Background jobs | WorkManager for the daily dispatch, weekly goal checks and decay ticks |

**Key technical risks**
- **Battery vs. accuracy** of all-day passive tracking. GPS must run only while you're moving.
- **Background location permissions** and Android's foreground-service restrictions (and Play Store policy, if it's ever published).
- **Health Connect data quality** depends on which apps and devices write to it.
- **Laying out the islands** in the world view so it reads well while keeping rough geography.
- **Map matching** noisy GPS in dense city areas.

---

## 12. Phased plan

**Phase 1: Core loop.** A playable app.
- Passive and session tracking, H3 familiarity, map matching
- Fog of war, peek ahead, trail wear, real road names
- Settlements forming, tiers, capital at home, neighborhood names
- Health Connect weekly goal, protection tiers, decay, ruins floor, rest mode
- A basic daily dispatch; the real map view

**Phase 2: The world.**
- Stylized world view and toggle
- Regions as islands, dormancy, long-distance routes from travel
- The Atlas and settlement histories; liveliness visuals

**Phase 3: Economy.**
- Gold and terrain materials
- Projects and proposals; milestone monuments
- Trade access along the network

**Phase 4: People.**
- Townspeople, settlement character, story notes
- Prosperity visuals and richer dispatch stories

---

## 13. Open questions for the design doc

- Exact tuning: familiarity curve, tier thresholds, decay base rate, wear levels.
- Visual style for the world view (illustrated, low-poly, hand-drawn cartography, …).
- Peek-ahead radius: one block, or based on distance?
- How detailed the gold and materials amounts are, and what the full project catalog is.
- How vehicle and flight trips are detected so waystones are placed correctly.
- Offline behavior and how much map data to cache.
- Backup / device migration story.

---

## Appendix: Research notes

- **Territory-capture apps are crowded and competitive** (INTVL, KYRO, Stride, RunWar, Territory Conquest, LandRush). Worth borrowing: going back to a place makes it stronger. Not worth borrowing: rivals.
- **Exploration apps** (Wandrer, City Wanderer, Fogbreaker, FogMap) show how satisfying a growing map is, but give nothing for familiar routes once covered. FogMap's on-device privacy approach is a good model.
- **Pokémon GO** (BMJ, 2016): about +1,000 steps a day at first, halved by week 4, nearly gone by week 6. Novelty alone doesn't keep people moving.
- **Streaks** (Silverman & Barasch, *JCR* 2023, as summarized second-hand): people who break a streak are more likely to quit entirely. Hence the rolling weekly window, proportional decay, ruins floor and rest mode.
- **Google Maps Timeline** moved on-device in 2024 with no third-party API, so the app does its own passive tracking.
