# Realm of the Last Wall — Troopmanager v1.0.0 — Sprint A

An offline Android strategy RPG built with Kotlin and Jetpack Compose. You begin as a young border lord with one territory, a working fortress, supplies and a standing army. Grow a realm, send actual troops on multi-day missions, prepare for invasions and command interactive battles. Becoming High King opens continued progression rather than ending the campaign.

## v1.0 Sprint A: Combat Engine 3.0 and save schema 5

Sprint A extends the existing v0.97 combat, siege, wall weapons and hospital systems. It adds editable engagement/fire distances, targets and formations per front, commander reaction and reserve coordination, timed manual reinforcements, siege stages, visibility-dependent accuracy and exact losses by arrows, artillery, wall weapons, melee, breaches, pursuit and collapse. Battle details expose the full front state; the compact command layout remains in place.

Version **1.0.0 / 46**, save schema **5**. Schema-4 campaigns migrate without reinitializing the world or charging ammunition again; each slot keeps a protected byte-exact v0.97 backup. Historical reports remain unchanged, and older replays run safely under current combat rules. The realm/world overhaul beyond Sprint A remains planned in Sprints B–E.

See [Sprint A implementation, acceptance evidence and risks](docs/V1.0-SPRINT-A.md) and [the complete v1.0 specification](docs/V1.0-REALM-WAR-OVERHAUL.md).

The next battle-visual milestone adds actual formation rows, separate weapon
silhouettes, local masonry/gate damage and finite, ammunition-backed volleys.
The renderer projects Combat Engine 3.0 without changing combat rules or saves.
See [implementation, artwork budget and verification](docs/BATTLE-VISUALS-PHASE-1.md).

## v0.97: battle plans, fortress tactics and compact Android UI

- A persisted battle plan covers six tactic profiles, ranged and wall-weapon targets, finite ammunition, reserve rules, gate/sortie policy, breach reserves, fallback thresholds and pursuit. Preparation opens before the first exchange; changes during battle use command points and replay inputs.
- Local contact states remain authoritative. Distant melee cannot cause personnel damage. Range, wall height, tower firing capacity, target armor/shields, density, visibility/night, weather, fatigue, morale, equipment and command affect real shooting. Salvos suppress and can rout a small assault before contact. A large siege force still reaches the wall.
- Automatic reserves arrive after a deployment exchange; manual reserve orders retain their command-point cost. Gate defense, fallback and controlled sorties affect contact and frontage.
- The battlefield renders actual cohorts by role, focused curved volleys, siege devices, wall/gate damage, breaches, fleeing fronts and reserve movement. Optional neutral terrain assets have a complete procedural fallback.
- Compact unit cards expose counts, availability, quality, roles and commanders. Resources show complete adaptive cards and an explicit paging control; selected tabs are brought into view. Attack warnings open a defense dashboard with actual garrison, ammunition, weapons, damage and warnings.
- Reports separate losses by source/contact phase, wounded/dead/captured/fled troops, stocks used, first front break and observed tactic effects.

Version 0.97.0 uses versionCode 45 and remains on save schema 4 with additive defaults. Existing archived battle reports remain intact; replays without a v0.97 plan use the safe defensive defaults under the current combat rules.

See [v0.97 implementation and verification](docs/V0.97-BATTLE-TACTICS.md).

## v0.96: visual world, city and battle overhaul

v0.96 continues the v0.95 living-realm pass and focuses on the three screens that should feel like a game rather than a management form.

- The campaign map no longer places simulation markers over `world_map.webp`. Geography, terrain masses, river, roads, fog/unknown regions, armies, convoys, outposts and hordes are drawn in one coordinate system and the selected region is surfaced directly on the map.
- The live battlefield now uses a grounded terrain layer, visible soldier cohorts instead of only abstract blocks, cavalry silhouettes, curved arrow volleys, melee sparks/dust, formation shadows and direct front-cohesion feedback.
- The city now ties every constructed building into a shared street/plaza network and visibly scaffolds active construction, building on the v0.95 removal of the double-city background effect.
- Existing Combat Engine 2.0, saves, AI, ammunition, siege state, relationship, diplomacy and economy systems remain the authoritative simulation.

Version 0.96.0 uses versionCode 44 and stays on save schema 4.

## v0.95: living realm and coherent city

v0.95 builds on Combat Engine 2.0 instead of replacing it. The player's settlement is now rendered as one coherent procedural scene: the old photographic city backdrop is no longer drawn underneath the generated walls, districts and buildings. Terrain, roads, fields, river, outskirts, season, night lighting, population growth and war damage now share the same coordinate system, so upgrades look like part of one city rather than objects pasted over another image.

The city screen adds live prosperity, security, population and supply feedback. The market now reacts deterministically to real stock levels, storage capacity, season, threat and invasion demand while preserving a buy/sell spread to prevent arbitrage. The command hero no longer presents the old city image as if it were the player's exact settlement.

The ruling-pair presentation now exposes trust, affection, respect and conflict directly in the hero instead of hiding those values deeper in menus. Existing consent, memories, shared government, delegation, court cases, presence, family and dynasty systems remain the source of gameplay effects.

This branch remains save-compatible with v0.90 save schema 4. Version 0.95.0 uses versionCode 43.

## v0.90: war and experience overhaul

Combat Engine 2.0 resolves distance, contact, cover and frontage independently for each front. Persistent siege devices, finite arrows/artillery, weapon priorities, local cohesion/fatigue and aggregate casualty allocation replace time-triggered melee and forced per-contingent losses. Exchange reports expose actual damage sources, prevented losses and ammunition; graded outcomes affect society, reputation and shared battle memories.

The battle screen keeps a fixed HUD, three front selectors, a dominant procedural battlefield and 48 dp actions. Context orders, preparation, army details and reports use sheets. Android UI tests cover a 320×568 viewport, landscape, large fonts and pending-event reactions.

Connected situations prioritize daily decisions. Outposts transfer real food, reserve existing field armies as garrisons, sustain raids and delay the same enemy army. Expeditions have leadership roles, operational decisions, scouting and regional reputation. City districts and world regions open contextual sheets; forecasts expose 7/14-day uncertainty and overflow. War objectives and actual losses/exhaustion influence peace; political demands show consequences and speaker roles. High-king campaigns gain coalition, frontier and reconstruction goals. Existing co-ruler advice, court, family, education and optional dynasty systems remain integrated.

Save version 4 gains safe additive defaults and idempotent battle migration. New battle replays use their original seed, stocks, roster and decisions. Pre-v0.90 replays are deterministic reinterpretations under the new contact rules; impossible legacy orders become Hold, while original reports remain unchanged.

See [v0.90 implementation and verification](docs/V0.90-WAR-EXPERIENCE.md) for architecture, balancing, compatibility and accepted adaptations.

## v0.83: campaign pulse and fun overhaul

v0.83 changes the campaign rhythm rather than piling on another isolated menu. The command center now exposes **Momentum** and **Pressure** derived from the real simulation, a selectable realm focus, streak rewards, short-lived strategic effects and recurring ruler dilemmas created from food, border, public-order and ruling-pair conditions.

Every few campaign days the current state generates a compact decision with three genuinely different approaches. Outcomes feed back into resources, satisfaction, security, threat, walls, commanders, relationship conflict, trust and renown. Ignored dilemmas expire into a conservative free option, so the game never blocks progression. Realm focus adds a small daily specialization for prosperity, defense, the people, the ruling pair or expansion while preserving the existing 100 creation attribute points and 30 skill points.

The goal of this pass is simple: fewer dead "next day" clicks, more visible cause-and-effect, and a stronger reason to react to what is happening in the realm.

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
- Persistent battles advance through local distance/contact exchanges rather than calculating a final report at the start. Deploy left, center, right and reserve; respond to battle events and choose pursuit after victory. Sound cues play bundled original synthesized effects.
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
- `versionName`: 0.96.0
- `versionCode`: 44

```bash
gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug --stacktrace
gradle :app:assembleDebug --stacktrace
gradle :app:connectedDebugAndroidTest --stacktrace
gradle :app:assembleRelease --stacktrace
test -f app/build/outputs/apk/debug/app-debug.apk
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
The unsigned release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`; sign it outside the repository with the existing update key before updating an installed campaign.

GitHub Actions runs checkout → JDK 17 → Gradle 8.9 → unit tests → Android lint → APK build → package/version/signature verification → artifact upload on pushes and pull requests to `codex/v0.90-war-experience-overhaul`, `codex/v0.85-modern-ui`, `main` and the maintained earlier branches, plus manual dispatch. The artifact is named `Realm-of-the-Last-Wall-v0.96.0-debug`.

A second CI job runs four battle-layout tests on Android 35 and uploads actual UI screenshots and instrumented test reports.

The debug artifact is intended for local installation and testing; store distribution and production signing are separate release tasks. The simulation uses 2D fortress and battlefield views, aggregate formations and deterministic state transitions; visible formations represent aggregate soldiers; local synthesized audio is bundled.


See [v0.6 implementation matrix and evidence](docs/V0.6.0-IMPLEMENTATION.md), [device test profiles](docs/V06-DEVICE-PROFILES.md), [v0.45 implementation and update evidence](docs/V0.45.0-IMPLEMENTATION.md) and [third-party icon notices](docs/THIRD-PARTY-NOTICES.md). The delivered v0.6 debug APK uses a new local debug certificate. Updating an existing v0.4/v0.45 installation requires its original signing key; never clear campaign data to bypass a signing mismatch. Fresh CI debug keys also require a persistent signing configuration for subsequent updates.
