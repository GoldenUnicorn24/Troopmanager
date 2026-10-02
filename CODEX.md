# Codex handoff — stand of the signed 0.68.0 APK

Branch: codex/v0.68
Playable build: versionName 0.68.0, versionCode 23, signed with the update keystore.

Continue from codex/v0.65 plus the commits already on this branch. The signed APK also contains these code changes. Apply them before new work.

## Required model changes
- BattleContingent: displayName: String? = null, designId: Long? = null
- Population.count(culture) and Population.takeRecruits(culture, amount)

## BattleEngine
- On deploy, if a custom design of that UnitType has soldiers, set displayName and designId.
- Fortify opening volley: subtract sum(weapon.defense * count) for weapons with ammo, floor 40.
- On battle end, subtract contingent losses from the matching design.soldiers.
- Fortify also spends 1 ammo and 4 integrity per used weapon stock.

## FrontierEngine
- designUnit costs 40 + role.ordinal * 15 gold and 2 recruits. Training stays expensive.
- deleteDesign if not training. Soldiers stay in the base pool.
- trainChild from age 10, combat note from 16.
- huntHorde: up to 40 soldiers, delays the banner, Tao Tei only if daysToArrival <= 2.
- assaultCamp: needs 20 home soldiers, removes the horde, starts BattleEngine.start with Tactic.AGGRESSIVE, enemyFortification 25, location = horde name.

## UI
- Settlement name click opens PopulationDialog.
- Army card shows design name when that regiment has soldiers.
- First 7 days: Erster Abend card.
- Frontier map with tappable banners: Lager stürmen / Nur jagen.
- House dialog from Kampfgefährten, joint training stats, children from 10.
- Live battle label uses contingent.displayName.

Do not commit gradle.properties memory overrides.
