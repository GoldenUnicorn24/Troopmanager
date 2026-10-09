# Agent Instructions — Realm of the Last Wall

## Primary directive
Implement the complete creative and technical vision from
[docs/NEXT-LEVEL-SANDBOX-MASTER-AUFTRAG.md](docs/NEXT-LEVEL-SANDBOX-MASTER-AUFTRAG.md).
**This is an open-world/sandbox kingdom simulation, NOT a linear campaign.**
Player decisions are open-ended. AI factions and the simulated world react to
state and past actions; do not require scripted quests or prescribed chapters.

## Real repository baseline
- Use `codex/v1.0-realm-war-overhaul` as the real source base (v1.0 Sprint A).
- Consider already open PRs, particularly UX PR #10. Do not duplicate or discard
  its work. The default `main` is an obsolete version and must not be used as
  the source of truth for app/gameplay.
- Preserve Android Kotlin + Jetpack Compose and all existing engines; no mock
  replacement, server dependency, P2W, or unnecessary rearchitecture.
- Preserve 100 attribute points, 30 skill points, 3 construction slots,
  1–3 mission commanders, culture-based troops, temporary mission units,
  persistent assignments, hospitals, relationship choices, save schema 4→5
  migration and protected backups.

## Quality priorities
1. Beautiful, consistent original visual assets and unified UI design.
2. Interactive battle representation based on real Combat Engine 3.0 state.
3. Cohesive city/world maps and meaningful, reactive sandbox play.
4. Smooth and comprehensible Android experience.
5. Tests, build, lint, signing and save compatibility.

## Operational execution
- Do not merely output a plan. Implement the smallest complete vertical
  slice, run tests and create a reviewable PR per milestone.
- Never claim art, tests, screenshots or APK builds exist when they do not.
- Run `gradle :app:testDebugUnitTest`, `gradle :app:lintDebug`, and
  `gradle :app:assembleDebug` when changing executable code.
- Visual changes need before/after screenshots, 320×568 and enlarged-font
  checks, as well as memory/performance consideration.
- Parallel Codex/Claude agents may work in separate scoped branches but should
  not edit the same files concurrently. Claude must be started and connected
  separately; a collaboration note does not activate it.
- Don't merge to an obsolete base; never overwrite valuable player data.

## Immediate first technical target
Complete one battle-visual enhancement based on actual archer/ranged
engagement and fortress state, with deterministic balance tests. Follow with
the city visual pass. See the master brief for details and acceptance cases.
