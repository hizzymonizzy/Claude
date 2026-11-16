# Wander World: Privacy & Security

Wander World holds sensitive personal data: where you live, where you walk every day, and your health data. This page covers what leaves the phone, how third-party code and assets are vetted before they come in, and the results of the latest scan.

## 1. Principles

1. **Your data stays on your phone.** Nothing personal is uploaded, and there is no account, analytics, crash reporting or ads.
2. **Nothing comes in unreviewed.** Every library, SDK or asset pack is reviewed (§3) before it's added, and pinned by checksum so it can't change silently.
3. **The app only makes outgoing requests to a short, known list of servers** (§2). A new server means updating this page.
4. **No code is loaded at runtime.** No web views, no downloaded code, no plugins.

## 2. What leaves the phone

| Destination | What is sent | What they can learn | Why |
|---|---|---|---|
| `overpass-api.de` (official OpenStreetMap-backed server, run by FOSSGIS e.V.) | A rectangle of about 3 × 3 km around a map tile you walked in. No ID, generic app name | That someone asked about that area | Road, path and place data |
| `tiles.openfreemap.org` | Map tile requests for the area on screen | Which area you're viewing | Base map |
| Google Play services (`googleapis.com`) | Handled by Android itself | Standard Android location services | Location and activity detection |
| *(you choose)* **Share diagnostics** | A text report, only when you tap the button | Settings, world summary, intensity per day, recording times, map tile and area IDs (approximate location) | Debugging |

**Links in Settings → Credits** (`play0ad.com`, `creativecommons.org`, `openstreetmap.org`) open in your browser only when you tap them. The app never contacts them.

**Never sent anywhere:** GPS tracks, health data, your home location, settlements, dispatches.

Unofficial Overpass mirrors were removed in 0.2.0, because each request reveals where you walked.

**Possible later improvement:** downloadable offline map packs for home and frequent regions, so day-to-day use makes no map requests at all.

## 3. Intake review for any new library, SDK or asset

Before adding anything third-party:

| Check | Pass criteria |
|---|---|
| **Source** | Official publisher or repository; no unofficial mirrors or re-uploads |
| **License** | Compatible; recorded in this doc (CC0, Apache-2.0, MIT, BSD, CC BY-SA for art) |
| **Permissions** | `tools/security_scan.sh` §3 shows no new permission, or the new one is justified here |
| **Exported components** | §4 shows nothing new that other apps can call, or it's removed with `tools:node="remove"` |
| **Network** | §5 shows no new contacted host; any new one is justified here |
| **Telemetry** | No analytics or tracking SDKs, and none pulled in indirectly |
| **Code loading** | No dynamic code, no web views, no native libraries without a known source |
| **Assets** (models, sprites, sounds) | Only data formats (PNG, glTF/DAE, OGG); no scripts; licenses recorded |
| **Pinning** | `./gradlew --write-verification-metadata sha256 …` updated, and the diff of `gradle/verification-metadata.xml` reviewed |

**Dependency verification is on.** `gradle/verification-metadata.xml` pins SHA-256 checksums for all 412 build and runtime files. A changed or unreviewed library fails the build.

## 4. App hardening

- **Network:** HTTPS only, and only system-trusted certificates (`res/xml/network_security_config.xml`). User-installed certificates, which could be used to intercept traffic, are not trusted.
- **Exported components:** the launcher screen, Health Connect's required privacy screen, and its system-only alias, which is protected by `START_VIEW_PERMISSION_USAGE`. The Health Connect client's exported SDK service (only for Android ≤ 13) is removed.
- **Database:** Room with parameterized queries only; no SQL is built from strings.
- **Data from OpenStreetMap** (names, shapes) is treated as text and numbers only: parsed as JSON and shown as text, never run as code or used in queries.
- **Backups:** `allowBackup=false`. The database never goes to cloud backup.
- **Health Connect:** read-only. Watch routes before the world's start day are ignored.

## 5. Latest scan (alpha 0.2.0)

Library sources: AndroidX, Google Play services, Guava (Google); Kotlin (JetBrains); MapLibre; H3 (Uber); JTS (LocationTech); OkHttp/Okio (Square, used by MapLibre); Timber (used by MapLibre). No analytics, ad or telemetry SDKs.

Permissions added by libraries:
- `RECEIVE_BOOT_COMPLETED` (WorkManager: scheduled jobs survive a restart). Accepted.
- `ACCESS_WIFI_STATE` (MapLibre: connectivity checks). Accepted; it reads no location from Wi-Fi.

Hosts found in the compiled code:
- **Contacted:** `overpass-api.de`, `tiles.openfreemap.org`, `googleapis.com`.
- **Present but not contacted** with our map style: MapLibre's built-in `mapbox.com` / `maptiler.com` / `demotiles` handlers.
- **Just text in error messages:** JetBrains and Google issue trackers.

Risky API use in our code: none.

## 6. Asset intake: `tools/asset_intake.py`

Third-party images go through this before use:
1. Download into a quarantine folder outside the repo. Git sources are fetched blob-only, with no checkout, so nothing from them is ever run.
2. Run `python3 -I tools/asset_intake.py <raw> <clean>`. It checks:
   - PNG magic bytes
   - a full decode
   - no data after the end of the image (no hidden payload)
   - size ≤ 2048 px
3. Passing files are re-encoded from raw pixels, which drops metadata and ancillary chunks. **Only the re-encoded copies are used.**
4. The license is recorded below, and credits are added in the app.

### 0 A.D. review (Nov 2026)

- **Source:** the official GitHub mirror `github.com/0ad/0ad` (branch `master`), fetched with a blob-less shallow clone and no checkout.
- **License:** `LICENSE.txt` says `binaries/data/mods/*/art` is **CC BY-SA 3.0**. Using it requires crediting Wildfire Games and keeping modified art CC BY-SA. Our code is unaffected.
- **Sample of 54 files:** building and nature portraits, resource icons, terrain textures. 53 passed and were re-encoded; 1 was rejected (an empty file from a path that doesn't exist).
- **What's useful:**
  - terrain textures (seamless top-down grass, forest floor, dirt, cobblestone, farmland, sand, snow)
  - building portraits (good as markers)
  - resource icons
- **Not useful for a top-down map:** 3D models. Those would need rendering.
- **Status:** in use since alpha 0.4.0. There are 24 adapted files (`oad_*`), built by `tools/build_0ad_assets.py` from intake-cleaned copies. Provenance for each file is in `docs/art/0AD_ASSETS.md`, with credits in `CREDITS.md` and in Settings → Credits & licenses.

## 7. Art

- `oad_*` images: adapted from 0 A.D. (CC BY-SA 3.0, Wildfire Games), went through intake (§6).
- `sprite_wanderer`, `sprite_waystone` and `fog_parchment` are original (`tools/gen_sprites.py`).
- Any future art pack goes through §3 and §6 first.
