# Wander World

A solo, no-competition fitness app where walking and running build a world. Roads come out of the fog only when you walk them, places you return to grow from camps into cities, and your weekly activity protects what you've built.

- [Concept](docs/concept.md)
- [Design doc](docs/design.md)
- [Alpha test guide](docs/alpha.md)

## Project layout

| Module | What |
|---|---|
| `engine/` | Pure Kotlin game logic: map matching, track processing, the deterministic world replay (settlements, decay, goal), the daily dispatch. No Android dependencies; fully unit-tested. |
| `app/` | Android app (Kotlin, Jetpack Compose, MapLibre, Room, WorkManager, Health Connect). |

## Build

Requires JDK 21 and the Android SDK (platform 37).

```bash
./gradlew :engine:test          # engine unit tests + persona simulations
./gradlew :app:assembleDebug    # APK at app/build/outputs/apk/debug/app-debug.apk
```

Print the persona simulations (how worlds grow over a year):

```bash
./gradlew :engine:test --tests '*Persona*' -i | grep -A8 '==='
```

## Map data

Roads and place names © OpenStreetMap contributors (ODbL), fetched via the Overpass API. Base map tiles from OpenFreeMap.
