# Wander World

**Scope:** this file covers everything under `wander-world/` and nothing else in the repository. Run all
commands from this folder. Other top-level folders are unrelated projects; don't read, change or borrow from them.

## What it is
A personal, non-competitive Android fitness app for one player. GPS walks, runs, rides and paddles
(SUP/canoe) grow a civilization on a stylized top-down map:
- **The map:** fog of war; roads that wear in from trail to avenue; your own paths and water routes.
- **Places and settlements:** discovered places; settlements from camp to metropolis; a capital pinned at home.
- **Travel:** regions linked by waystones.
- **Economy:** gold, wood, food, stone and metal, plus projects and monuments.
- **Decay:** gentle, tied to a weekly Health Connect intensity goal. Ruins are the floor.

Concept: `docs/concept.md`. Rules and their history, the source of truth: `docs/design.md`. Settled
choices: `docs/decisions/` (read before reopening a question). Shipping: `docs/process/release.md`.

## The player and the feedback loop
- One tester: Pixel 10 Pro Fold (Android 17) + Pixel Watch 4. Health Connect intensity, exercise sessions and
  routes are granted. Exercise routes needed "Always allow" in Health Connect settings, because the in-app
  consent screen black-screens on Android 17.
- They install each alpha APK over the last and send **diagnostics files** (Settings → Share diagnostics) plus
  screenshots. Read the log first: tags `[process] [geo] [map] [activity] [track] [health] [economy] [home]`.
  Find the line that proves a cause before changing code.
- Likes: the legend, the top-down 0 A.D. look, everything automatic (no mode buttons), discovery over routine.
  Rejected: the isometric diorama, manual start/stop for paddling.
- Field facts learned so far:
  - Overpass often returns 502/429. Large geometry (sea, river-sized relations) times out a tile's whole
    request, so relations come as bounding boxes first (ADR 0002).
  - Map data exists only for tiles you've walked in or stood in; "no walk" stops (stores) used to leave no data.
  - Activity Recognition starts tracking from the background reliably.
  - Indoor pottering yields "no walk" captures; that's expected.

## Non-negotiables
- **Privacy (ADR 0001).** Nothing personal leaves the phone. Only `overpass-api.de` and `tiles.openfreemap.org`
  are contacted. No mirrors (ADR 0002). Never put the player's name, email, street, neighborhood or coordinates in
  code, tests, docs, commits or requests. Test fixtures use invented places (Elm Ave, Oakdale).
- **Intake.** A new library, asset or host goes through `docs/security.md` §3 before use. Assets are fetched into
  quarantine and passed through `tools/asset_intake.py`. Ask the player before widening the environment's network
  (they declined kenney.nl).
- **Health app (ADR 0005, 0006).** Never punish rest; gold is never reduced for routine.
- **Player data survives updates.** Room migrations only, with schemas exported to `app/schemas/` and covered by
  `MigrationTest`. No destructive fallback.

## Code map
- `engine/`: pure Kotlin/JVM, no Android; all game rules, fully unit-tested.
  - `WorldEngine.build(input, today)` replays stored observations day by day (ADR 0003).
  - `sim/Simulation.kt` holds the state and the **order of a day's steps**; that order is part of the rules.
  - Each step has its own file in `sim/`: `Regions`, `RoadsAndPlaces`, `Settlements`, `Goal`, `Economy`.
    `Snapshot.kt` builds the immutable `WorldState`.
  - `TrackProcessor` (GPS filtering, pottering, water detection), `MapMatcher` (HMM map matching),
    `Economy.kt` (yields, projects, monuments, harvest thinning), `Resources.kt` (resource nodes and pointers),
    `Dispatch.kt` (daily text). `GatheringSimulationTest` walks through nodes end to end; extend it when gathering
    or pointers change.
  - Tunable numbers live in `WorldParams` / `HarvestParams`.
- `app/` (`world.wander.app`): Android (Compose, MapLibre, Room, WorkManager, Health Connect, H3).
  - `data/Repository.kt` is the hub (processing, rebuild, diagnostics, nearest resources).
  - `data/Db.kt` holds entities and DAO; `data/DbMigrations.kt` holds the migration SQL.
  - `geo/Overpass.kt` fetches and parses map data.
  - Map UI:
    - `ui/MapScreen.kt` is the map screen and its controls.
    - `ui/MapStyle.kt` defines every source, layer and sprite.
    - `ui/MapLayersBuilder.kt` turns the world into GeoJSON.
    - `ui/MapIcons.kt` is the single registry of images.
  - `ui/Legend.kt` is generated.
- `tools/`:
  - `check.sh`: the gate.
  - `policy/`: allowed permissions, exported components and hosts.
  - `security_scan.sh`: full third-party inventory.
  - `asset_intake.py`, `build_0ad_assets.py`, `gen_sprites.py`, `gen_legend.py`.

## Commands (from `wander-world/`)
```
export ANDROID_HOME=/opt/android-sdk
tools/check.sh                       # tests + build + lint + privacy policy; must pass before every commit
./gradlew :engine:test               # fast loop for game rules
./gradlew :app:testDebugUnitTest     # migrations, Overpass parsing, map layers
python3 -I tools/gen_legend.py       # after any new map symbol; then regenerate the LEGEND list in Legend.kt
```
- The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Send it as `wander-world-alpha-X.Y.Z.apk` from the scratchpad.
- A fresh cloud container may lack the SDK: install cmdline-tools from dl.google.com, then
  `sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"`.
- CI: `.github/workflows/wander-world.yml` runs `tools/check.sh` for changes under `wander-world/`.

## Shipping
Follow `docs/process/release.md`. Work on branch `claude/fitness-gps-world-building-j6g4s6`.
- Commit messages: `Alpha X.Y.Z: summary` plus bullets.
- Bump `PROCESSING_VERSION` when walk processing changes.
- Bump the DB version, add a migration and commit the new schema JSON when an entity changes.
- Each new subsystem logs one diagnostics line under its own tag.
- Map text must use the base map's font (`FONT` in `MapStyle.kt`). Any other font silently hides the whole layer.
- When you report back, say what was not verified on the phone.

## Known technical debt
- Overpass network code (`postWithRetry`) is untested; only parsing is covered.
- `Repository.kt` is large (processing, rebuild, diagnostics, resources); split it when it next grows.
