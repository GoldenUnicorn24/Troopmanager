# Claude Code collaboration guide — Realm of the Last Wall

This project is a real offline Android strategy/RPG, not a greenfield prototype.
**Working reference:** `codex/v1.0-realm-war-overhaul` (v1.0 Sprint A; Combat Engine 3.0; save schema 5).
Read `docs/V1.0-REALM-WAR-OVERHAUL.md` and `docs/V1.0-SPRINT-A.md` first.
Never branch from the old `main` or replace a working subsystem with a mock.

## Shared north star

Make a **coherent playable fantasy strategy game** rather than a collection of menu screens:
every march, supply shortage, battle loss, building, relationship and kingdom decision needs visible
player feedback and systemic consequences. Prioritize cause/effect, delight and readability over
feature-count inflation. Keep the game offline, no P2W, no subscriptions, minSdk 26.

## Parallel ownership / no conflicting edits

- **Claude — narrative and systemic design:** world-strategy AI, campaigns, co-ruler reactions,
  events, economy/gameplay balancing. Start by auditing existing implementations, supply
  reproducible defects and acceptance tests; implement only scoped fixes on a `claude/*` branch.
- **Claude — asset art direction (separate branch/PR):** propose a practical, unified art bible;
  inventory existing WebP/vector art; list exact replaceable file paths and recommended sizes.
  Do not commit copyrighted franchise imagery or uncontrolled replacement assets.
- **ChatGPT — integration and mobile experience:** shared UI components, visual information
  hierarchy, touch targets, actionable state-driven navigation, combat UI integration, and PR review.
- **Maintainer:** merges only after tests, lint, APK build, save migration and actual Android
  screen evidence where applicable.

This role split is a coordination proposal. This file does **not** automatically start
Claude or any other agent. To use Claude Code, open this repository and explicitly run it
on its assigned branch.

## Visual style / art bible 1.0

- Mood: **grounded dark fantasy**, handcrafted but readable; realistic physical materials,
  strong faction silhouettes, deliberate lighting, no pasted-on stock photography.
- Four factions must look distinct: human steel-and-leather frontier, gold elven ceremonial
  detail, wood-elven organic woodland gear, and disciplined wall legion architecture.
- Each drawable has a job: battlefield formations and effects, clickable city buildings,
  world-region geography, combat portraits, menu illustration. All should share a palette,
  horizon/perspective conventions and consistent asset quality.
- Separate **concept art** from **runtime assets**. For runtime assets require the correct
  aspect ratio, screen density, alpha treatment, ownership/license records, size budget and
  a procedural/fallback graphic. Keep image decoding out of per-frame draw calls.
- Battle visuals must reflect true engine state: range, contact, morale, projectiles,
  casualties, walls, breaches, weather and reserve arrival. Decorative VFX must never
  imply combat interactions that the engine did not resolve.
- UI hierarchy: one dominant active scene, a small readable state HUD, visible threat
  priority and one primary action. Detail should be an inspectable overlay, not invisible.

## Delivery order

1. **Reality check:** Inventory concrete defects and baseline screenshots on the v1.0 branch.
2. **First playable polish:** resolve misleading navigation, improve shared HUD consistency,
   ensure text scaling and touch targets remain readable; keep all current information accessible.
3. **World campaign pass:** make rival armies, scouting, supply lines, raids, relief and
   outposts visibly affect the existing world simulation.
4. **City pass:** context-aware building hotspots, coherent architecture and construction
   stages; a building click must open the relevant functional control.
5. **Battle pass:** physically believable troop formations, clear ranged exchanges and
   consequences; respect Combat 3.0 invariants.
6. **Narrative/ruling pair:** reactive presence-aware dialogue, governance decisions and
   long-term consequences without removing the fully supported friendship path.

## Definition of done for every PR

- State the exact files and gameplay behaviors changed.
- Include before/after screenshots or a deterministic scenario for visible changes.
- Add targeted automated tests for pure engines; run `:app:testDebugUnitTest`,
  `:app:lintDebug`, `:app:assembleDebug`.
- Verify Android large-font and small-viewport behavior where UI changed.
- Verify save schema 4 -> 5, unchanged current saves, battle replay compatibility where touched.
- Do not bump version for internal experiments, invent test-pass claims, or deliver an APK
  whose signature cannot update the already installed package.
- Submit separate pull requests. Never concurrently edit the same files from two agents.
