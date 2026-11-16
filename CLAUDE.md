# Wander World: working notes for Claude

A personal (non-competitive) Android fitness app. GPS walks, runs, rides and paddles grow a civilization on a
stylized top-down map: fog of war, roads that wear in, settlements from camp to metropolis, a capital at home,
gentle decay tied to a weekly Health Connect goal. Concept: `docs/concept.md`. Design (source of truth for
rules): `docs/design.md`. Decisions: `docs/decisions/`.

## The player
- One tester: Pixel 10 Pro Fold (Android 17) + Pixel Watch 4. They install alpha APKs and send diagnostics
  files (Settings → Share diagnostics) and screenshots. **Read the diagnostics log before guessing a cause.**
- They like: the legend, top-down 0 A.D. look, automatic everything (no buttons for modes). They rejected the
  isometric diorama.

## Non-negotiables
- **Privacy.** Nothing personal leaves the phone (ADR 0001). Never put the player's name, email, home address or
  coordinates in commits, docs, requests or code. Only `overpass-api.de` and `tiles.openfreemap.org` are
  contacted (ADR 0002; no mirrors).
- Test fixtures use invented names and places (Elm Ave, Oakdale), never the player's real streets or
  neighborhood.
- **Intake.** Every new library, asset or host goes through `docs/security.md` §3 *before* use. Assets are fetched
  into quarantine and passed through `tools/asset_intake.py`. Don't add network hosts to the environment
  without asking the player (they declined kenney.nl).
- **Health app.** Never punish rest; gold is never reduced for routine (ADR 0005, 0006).
- **Player data survives updates.** Room migrations only, no destructive fallback.

## Layout
- `engine/`: pure Kotlin/JVM, no Android. `WorldEngine.build(input, today)` replays observations day by day
  (ADR 0003). The replay is in `engine/.../sim/`: `Simulation.kt` holds the state and the **order of a day's
  steps** (that order is part of the rules); each step has its own file (`Regions`, `RoadsAndPlaces`,
  `Settlements`, `Goal`, `Economy`), and `Snapshot.kt` builds the immutable `WorldState`. Track processing, map
  matching, economy tables (`Economy.kt`), dispatch text. All rules are unit-tested here; `RandomHistoryTest`
  checks determinism and invariants over busy random histories.
- `app/`: Android (Compose, MapLibre, Room, WorkManager, Health Connect, H3). `data/Repository.kt` is the hub;
  `ui/MapScreen.kt` draws the map; `ui/MapIcons.kt` names every image; `ui/Legend.kt` is generated.
- `tools/`: `check.sh` (the gate), `policy/` (allowed permissions, exported components, hosts),
  `security_scan.sh` (full inventory), `asset_intake.py`, `build_0ad_assets.py`, `gen_sprites.py`, `gen_legend.py`.

## Commands
```
export ANDROID_HOME=/opt/android-sdk
tools/check.sh                       # tests + build + lint + privacy policy; must pass before every commit
./gradlew :engine:test               # fast loop for engine rules
python3 -I tools/gen_legend.py       # after any new map symbol; then regenerate the LEGEND list in Legend.kt
tools/security_scan.sh               # full third-party inventory, for intake reviews
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the scratchpad as `wander-world-alpha-X.Y.Z.apk` and send it.
In a fresh cloud container the SDK may be missing: install cmdline-tools from dl.google.com, then
`sdkmanager "platforms;android-37" "build-tools;36.0.0" "platform-tools"`.

## How to ship
Follow `docs/process/release.md` step by step. Develop on branch `claude/fitness-gps-world-building-j6g4s6`.
Commit message: `Alpha X.Y.Z: summary` plus bullets. Report to the player what was not verified on the phone.

## Conventions
- Engine rule → test first or alongside. Field bug → a test that reproduces it.
- Walk-processing change → bump `Repository.PROCESSING_VERSION`.
- New subsystem → one diagnostics log line under its own tag (`repo.log("tag", ...)`).
- Map text must use the base map's font (`FONT`); other fonts silently hide a whole symbol layer.
- Map icons are registered once in `MapIcons.images`; the map, Atlas and legend all read from it.
- Tunable numbers live in `WorldParams` / `HarvestParams`, not inline.

## Known technical debt (pay down before it grows)
- `MapScreen.kt` mixes layer styling with UI; move style layers to their own file.
- No tests for Room migrations, Overpass parsing, or `MapLayersBuilder`.
