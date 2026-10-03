# Realm of the Last Wall — Troopmanager v0.82.0

An offline Android strategy RPG built with Kotlin and Jetpack Compose. You begin as a young border lord with one territory, a working fortress, supplies and a standing army. Grow a realm, send actual troops on multi-day missions, prepare for invasions and command interactive battles. Becoming High King opens continued progression rather than ending the campaign.

## v0.82: frontier, realm UI and systemic polish

v0.82 builds on the ruling-pair/world simulation and focuses on correctness and integration. Frontier camps now persist and grow correctly, hunts and failed assaults consume real soldiers, camp loot is awarded only after victory, daily objectives are objective-specific, immigration respects the realm's cultures, and the frontier has a dedicated UI with world-coordinate markers and threat previews.

New persistent systems include player-facing frontier decisions, culture loyalty/integration, buildable outposts, captain candidates, and regiment legacy (wins, losses, veteran levels, epithets and captain assignment). The permanent navigation is reduced to five areas with contextual warning routing.

The deeper v0.82 pass adds 60 deterministic frontier-event presentations, weekly culture consequences, culture cases in the co-ruler council, succession tension and resolution, family-linked dynastic alliance treaties, visible physical supply convoys, aging fog-of-war estimates, a consolidated realm situation card, and culture-shaped city districts.

## v0.65: the ruling pair and a living frontier

Additive local-save models connect companion personality and thematic conflicts, contextual dialogue and personal arcs, optional joint government with bounded delegation, live presence, child education and mentors, the court network, the quest journal and annual chronicle chapters. Frontier now reserves real patrol troops, moves allied reinforcements over campaign days, equips delayed custom formations and uses ammunition-bearing wall weapons in deterministic siege exchanges and replay. Existing 100 creation attribute points and 30 immediately usable skill points remain.

See [v0.65 architecture and controls](docs/V0.65-RELATIONSHIP-CO-RULER.md) for the implementation and save fields. Android tests, lint, APK verification and external signing instructions remain required; a debug build does not carry the user's existing update certificate.

## Living-world v0.6 update

- Persistent original realms, named rivals, world armies, travel, scouting and faction-specific fog, seasons, weather, prepaid logistics, depots and convoys.
- Tactical formations, command points, functional terrain and morale routing, casualties/captives, arsenal equipment, siege damage and deterministic battle replay.
- Court offices and commander careers, six perk paths, explicit optional adult romance with consent gates, memories, autonomous advice and optional dynasty/succession.
- Negotiated treaties, finite resource transfers, alliances/vassals, spies, social groups, disease, migration and delayed story chains.
- Dynamic local music, 48 original local audio cues, haptics, heraldry, achievements, records and a hall of legends.
- Immutable StateFlow/ViewModel UI; background simulation and save IO; three campaign slots, checksums, protected migrations, atomic writes and SAF export/import.

## City and interface

- Eight campaign domains: command center, city, military, world, court, ruling pair, family and character. The palace links its throne room, war council, private rooms, family and court. Help remains in the top menu.
- Isometric city with pan/zoom, precise building targets, six districts, accessible building list, three architectural tiers, bounded ambient groups and the same scene during sieges.
- Construction takes campaign days with upfront costs and one to three slots. Residential districts and warehouses expand housing/storage; tax, workers, satisfaction, prosperity and security affect the economy.
- Market purchases/sales use different prices. Mine, forest and village ownership contributes daily production; region purchase and diplomacy retain stable IDs.
- Hospital, officer school, stables, arsenal and embassy supply actual morale, training, upkeep, repair and diplomatic/economic effects.
- Separate commander profiles, persistent service statistics, fullscreen quantity assignment and mission planning, meaningful new battle orders, and reports showing XP, loot, casualties and wear.
- New original army/world/city/menu/portrait artwork, adaptive icon, resource pictograms and contextual explanations.

## Campaign core

- Human, Elf and Half-Elf origins start with distinct armies; Half-Elves can use all four cultures immediately.
- Start buildings include farm and barracks level 2 plus sawmill, quarry, ironworks, market, wall, tower and residence level 1.
- Initial supplies: 5,000 gold, 8,000 food, 3,500 wood, 2,500 stone and 1,500 iron.
- Faster daily production from buildings and territory, adjusted by available workers. The UI shows production, army food upkeep and net change. Food shortages reduce morale, growth and training progress.
- Population distinguishes civilians, workers, recruits, free population, soldiers and trainees. Soldiers do not also provide civilian labor.
- Soldiers are aggregated into one pool per unit type. Morale, experience and equipment affect power; ordinary soldiers have no individual character objects.
- Four culture cards show army totals and training. Commanders receive explicit unit quantities without duplicate assignments. Unassigned troops remain under the player's direct command.
- Six mission types: border patrol, caravan escort, bandit suppression, monster hunt, village defense and reconnaissance. Choose soldiers and an optional commander, pay supplies and wait for the required days. Mission troops are unavailable at home, and outcomes include real casualties and rewards. Recall requires travel time.
- Threat produces warnings, raids and announced invasions. The arrival countdown supports wall repairs, troop preparation, recalls and allied requests. Siege devices and fortress defenses affect the attack.
- Persistent battles advance in phases rather than calculating a final report at the start. Deploy left, center, right and reserve; respond to battle events and choose pursuit after victory. Sound cues play bundled original synthesized effects.
- Character experience and skill points, meaningful commander stats and a companion who can command troops and missions.
- Relationships allow at most one large or two small actions per day, with decisions and stages from companions to ruling couple.
- Realm events offer consequential choices; cultures unlock through actual population and diplomacy. World regions connect locations with mission types.
- A detailed, clickable fortress map opens building details and upgrades. Custom settlement names remain separate from settlement tiers.
- Updated ten-step tutorial, help, dynamic realm goals and a campaign chronicle.

## Structure

`model/` contains serializable army pools, unit allocations, missions, realm events, invasions, relationships and battle sessions. `engine/` separates economy, army, mission, battle, relationship, event and progression logic. Compose screens present the realm, army, world, court and live battle.

Simulation operates on quantities, not individual soldiers, so large armies remain compact. Only the player, companion and commanders are individual characters. Original high-resolution illustrations replace the small v0.4 category/menu assets and portrait placeholders. Local custom portraits are supported; see [CREDITS.md](CREDITS.md).

## Offline saves

Campaigns autosave in local Android storage with a backup and error handling. Save version 4 first migrates older regiment-based saves by summing soldiers per type and weighting morale and experience, then adds neutral city management and persistent world/court/diplomacy domains. A byte-exact protected copy of the original v0.4 save survives rolling autosaves. All original v2 fields are preserved; capacities protect existing population and supplies. Future saves are rejected safely. Commander allocations and current missions/battles persist. No account, server, subscription, online requirement or in-app purchases. Imported portraits remain local device references.

## Build and verification

Use JDK 17, Android SDK 35 and Gradle 8.9. Android Studio can import the project directly.

- `compileSdk` / `targetSdk`: 35
- `minSdk`: 26
- `versionName`: 0.82.0
- `versionCode`: 38

```bash
gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug --stacktrace
gradle :app:assembleDebug --stacktrace
gradle :app:assembleRelease --stacktrace
test -f app/build/outputs/apk/debug/app-debug.apk
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
The unsigned release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`; sign it outside the repository with the existing update key before updating an installed campaign.

GitHub Actions runs checkout → JDK 17 → Gradle 8.9 → unit tests → Android lint → APK build → package/version/signature verification → artifact upload on pushes and pull requests to `main`, `codex/v0.81`, `codex/v0.82-overhaul` and the maintained earlier branches, plus manual dispatch. The artifact is named `Realm-of-the-Last-Wall-v0.82.0-debug`.

The debug artifact is intended for local installation and testing; store distribution and production signing are separate release tasks. The simulation uses 2D fortress and battlefield views, aggregate formations and deterministic state transitions; visible formations represent aggregate soldiers; local synthesized audio is bundled.


See [v0.6 implementation matrix and evidence](docs/V0.6.0-IMPLEMENTATION.md), [device test profiles](docs/V06-DEVICE-PROFILES.md), [v0.45 implementation and update evidence](docs/V0.45.0-IMPLEMENTATION.md) and [third-party icon notices](docs/THIRD-PARTY-NOTICES.md). The delivered v0.6 debug APK uses a new local debug certificate. Updating an existing v0.4/v0.45 installation requires its original signing key; never clear campaign data to bypass a signing mismatch. Fresh CI debug keys also require a persistent signing configuration for subsequent updates.
