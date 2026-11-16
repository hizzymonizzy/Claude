# Wander World: Alpha 0.1 Test Guide

The first build you can carry around. It's the **Phase 1 core loop** with placeholder visuals: a working skeleton that later art and detail can be built into.

## What's in it

| Feature | State |
|---|---|
| GPS capture: manual walks, all-day mode, background-start test | ✅ |
| Matching your walk to real streets (OpenStreetMap) | ✅ |
| Fog of war, worn trails (5 levels), faint streets at the fog's edge, real road names | ✅ |
| Settlements forming from repeat visits, tiers Camp → City, capital at home | ✅ |
| Real neighborhood names, Atlas | ✅ |
| Health Connect: intensity minutes (step-cadence fallback), watch GPS workouts | ✅ |
| Weekly goal, protection tiers, decay with ruins as the floor, restoration, rest mode, goal lock | ✅ |
| Daily dispatch notification + archive | ✅ |
| Fold layout: map + side panel when unfolded | ✅ |
| Diagnostics log for the technical tests | ✅ |
| Waypoints for every area walked, Atlas search, tap to fly the map | ✅ 0.2.1 |
| Named places (parks, woods, stores) and your own paths | ✅ 0.1.5 |
| Regions, dormancy, waystones, portal links | ✅ 0.3.0 |
| Original pixel-art sprites, parchment fog | ✅ 0.2.0 (placeholder art) |
| World view (3D), economy, townspeople | ❌ later phases |

Visuals are deliberately plain (parchment fog, inked roads, dots for settlements). The look is a separate layer that can be swapped out later without touching the game logic.

## Install

1. On the Pixel Fold, download `wander-world-alpha.apk`.
2. Open it. Allow "Install unknown apps" for your browser or Files app when asked.
3. The app installs as **Wander World α** (package `world.wander.app.alpha`).

## First-time setup (Settings tab)

1. **Permissions:** grant Location (precise), then **Location all the time**, Physical activity, Notifications, and Health Connect (all items).
2. **Home:** stand at home and tap **Set home to my location**. It downloads your neighborhood's streets and names.
3. **Tracking:** turn on **Track my walks automatically**, mode **All-day (reliable)**.
4. In the **Fitbit app**, make sure it shares data with Health Connect (Fitbit → profile → Health Connect).

Then go for a walk. Your capital is founded on your first walk at home.

## What to test

### Test 1: Background tracking (Spike S1, the biggest risk)
- **All-day mode:** carry the phone for 3–5 days. Afterwards, check Settings → *Recent captures*. Did every real walk show up? Note battery use (Settings → Battery → Wander World α).
- **Background-start mode:** switch to *Background start (S1 test)* for 2–3 days. In the *Diagnostics log*, look for:
  - `S1: started tracking from the background ✓`: Android allows it, so we can drop the all-day notification.
  - `S1: background start refused` or `startForeground refused`: Android blocks it, so all-day mode stays.

### Test 2: Road matching (Spike S2)
- After a few walks, compare the inked roads to where you actually walked. Note streets that are wrongly lit, or walked streets that stayed fogged (especially sidewalks next to busy roads).

### Test 3: Health Connect and the watch (Spike S3)
- Settings → **Sync health**. The log says either `intensity records for N days` (great: Pixel Watch data is coming through) or `estimated from step cadence` (fallback).
- Compare **Goal** tab minutes with the Fitbit app's Active Zone Minutes for the same days.
- Do a run with only the watch (phone at home). After syncing, does it show as a `watch` capture and light up roads? If the log says `needs route consent`, note it.

### Test 4: How it feels
- Is the morning dispatch something you look forward to? Is the tone right?
- Do the pacing numbers feel right: first camp, first tier-up, how fast trails wear?
- Anything that feels like a chore or a guilt trip.

## Reporting back

Settings → Diagnostics log has everything technical. Screenshots of the log, the Atlas and the map are the easiest way to report.

## Known limits of this alpha

- Map data comes from the free public Overpass server and is fetched the first time you walk in a new ~5 km² area, so the first walk somewhere new needs signal.
- After a phone restart, open the app once to restart all-day tracking.
- The first few seconds of a capture can be dropped while the recording starts.
- No backup yet. Uninstalling deletes your world.
