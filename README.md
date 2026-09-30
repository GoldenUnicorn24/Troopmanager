# Realm of the Last Wall — Troopmanager v0.4.0

An offline Android strategy RPG built with Kotlin and Jetpack Compose. You begin as a young border lord with one territory, a working fortress, supplies and a standing army. Grow a realm, send actual troops on multi-day missions, prepare for invasions and command interactive battles. Becoming High King opens continued progression rather than ending the campaign.

## Core overhaul

- Human, Elf and Half-Elf origins start with distinct armies; Half-Elves can use all four cultures immediately.
- Start buildings include farm and barracks level 2 plus sawmill, quarry, ironworks, market, wall, tower and residence level 1.
- Initial supplies: 5,000 gold, 8,000 food, 3,500 wood, 2,500 stone and 1,500 iron.
- Faster daily production from buildings and territory, adjusted by available workers. The UI shows production, army food upkeep and net change. Food shortages reduce morale, growth and training progress.
- Population distinguishes civilians, workers, recruits, free population, soldiers and trainees. Soldiers do not also provide civilian labor.
- Soldiers are aggregated into one pool per unit type. Morale, experience and equipment affect power; ordinary soldiers have no individual character objects.
- Four culture cards show army totals and training. Commanders receive explicit unit quantities without duplicate assignments. Unassigned troops remain under the player's direct command.
- Six mission types: border patrol, caravan escort, bandit suppression, monster hunt, village defense and reconnaissance. Choose soldiers and an optional commander, pay supplies and wait for the required days. Mission troops are unavailable at home, and outcomes include real casualties and rewards. Recall requires travel time.
- Threat produces warnings, raids and announced invasions. The arrival countdown supports wall repairs, troop preparation, recalls and allied requests. Siege devices and fortress defenses affect the attack.
- Persistent battles advance in phases rather than calculating a final report at the start. Deploy left, center, right and reserve; respond to battle events and choose pursuit after victory. Sound cues provide an extension point without placeholder audio assets.
- Character experience and skill points, meaningful commander stats and a companion who can command troops and missions.
- Relationships allow at most one large or two small actions per day, with decisions and stages from companions to ruling couple.
- Realm events offer consequential choices; cultures unlock through actual population and diplomacy. World regions connect locations with mission types.
- A detailed, clickable fortress map opens building details and upgrades. Custom settlement names remain separate from settlement tiers.
- Updated ten-step tutorial, help, dynamic realm goals and a campaign chronicle.

## Structure

`model/` contains serializable army pools, unit allocations, missions, realm events, invasions, relationships and battle sessions. `engine/` separates economy, army, mission, battle, relationship, event and progression logic. Compose screens present the realm, army, world, court and live battle.

Simulation operates on quantities, not individual soldiers, so large armies remain compact. Only the player, companion and commanders are individual characters. Original bundled army category images, menu artwork and the app icon are retained. Local custom portraits are supported; see [CREDITS.md](CREDITS.md).

## Offline saves

Campaigns autosave in local Android storage with a backup and error handling. Save version 2 migrates older regiment-based saves by summing soldiers per type and weighting morale and experience. Commander allocations and current missions/battles persist. No account, server, subscription, online requirement or in-app purchases. Imported portraits remain local device references.

## Build and verification

Use JDK 17, Android SDK 35 and Gradle 8.9. Android Studio can import the project directly.

- `compileSdk` / `targetSdk`: 35
- `minSdk`: 26
- `versionName`: 0.4.0
- `versionCode`: 4

```bash
gradle :app:testDebugUnitTest --stacktrace
gradle :app:assembleDebug --stacktrace
test -f app/build/outputs/apk/debug/app-debug.apk
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs checkout → JDK 17 → Gradle 8.9 → unit tests → APK build → file verification → artifact upload on pushes and pull requests to `main` and manual dispatch. The artifact is named `Realm-of-the-Last-Wall-v0.4.0-debug`.

The debug artifact is intended for local installation and testing; store distribution and production signing are separate release tasks. The simulation uses 2D fortress and battlefield views, aggregate formations and deterministic state transitions; individual soldier movement and recorded battle audio are not part of this version.
