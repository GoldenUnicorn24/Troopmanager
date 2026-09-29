# Realm of the Last Wall — Troopmanager

A fully offline Android strategy/RPG hybrid: start as an unknown soldier, build a warband, promote individual commanders, meet a fully customizable companion, buy land, grow a multi-racial fortress realm and defend it against Orcs, Uruk-hai and Tao-Tei-style monster swarms.

## Playable scope — v0.1.0

This version contains a complete persistent progression loop rather than a menu mockup:

- Human, Elf and Half-Elf character creation
- Custom local portrait import for the player
- Fully offline autosave/load
- Day-based economy and production
- Culture-specific population and recruit pools
- 10 / 25 / 50 / 100% batch recruitment
- Humans, Woodland Elves, Gold Elves and Great-Wall-inspired corps
- Training queues that become persistent regiments
- Regiment experience, morale, casualties and battle power
- Individual commanders with portraits, skills, traits, ranks and training
- Dynamic early companion encounter
- Companion name/portrait customization
- Trust, respect, affection, training, command delegation and later co-ruler role
- Missions, renown, ranks and titles
- Buyable territory and population growth
- Farms, sawmills, ironworks, markets, barracks, walls, towers and residence upgrades
- Orc raids, Uruk-hai warbands and Tao Tei swarms
- Tactical battle choices: hold, assault, ranged focus, flank and fortified defense
- Dynamic casualties, loot, threat and wall damage
- Persistent campaign chronicle
- Continuous High-Kingdom late game instead of a forced ending
- Original bundled vector artwork and local custom portrait support
- GitHub Actions debug APK build

## Game structure

The game deliberately does **not** simulate every inhabitant as an individual character. Population and normal soldiers are handled as pools and regiments so Android can support very large armies.

Only important characters are individually persistent:

1. The player
2. The companion / later co-ruler
3. Promoted commanders

That means a force can contain thousands of soldiers without creating thousands of portraits or character records.

## Half-Elf path

The Half-Elf path is the most open route. Humans, Woodland Elves, Gold Elves and Wall Corps can serve in the same realm from the start. Human and Elf origins can unlock other cultures later through renown.

## Build

Use JDK 17 and a recent Android Studio.

- compileSdk: 35
- targetSdk: 35
- minSdk: 26
- version: 0.1.0

Command-line build:

```bash
gradle :app:assembleDebug
```

Every push to `main` also runs the included GitHub Actions workflow and uploads the debug APK as a workflow artifact when the build succeeds.

## Offline design

There are no accounts, servers, subscriptions or in-app purchases. Saves use local Android storage. Imported player/companion images are referenced locally on the device.

## Visual direction

The project uses original dark-fantasy vector artwork for the built-in knight, Gold Elf, Woodland Elf, Wall Guard, companion, Orc, Uruk and monster archetypes. Players can replace the two main character portraits with their own device images.
