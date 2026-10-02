# v0.6 City, society presentation and local audio

CityScene keeps the original 2.5D projection, zoom, geometric building selection and
screenreader building list. Its landscape is decoded once on Dispatchers.IO and
published by a composition-owned produceState, with raw asset pixel scaling;
the procedural scene remains available until decoding finishes. Its bounded representative groups now use campaign
food coverage, wealth, security, satisfaction and war status: hungry streets empty,
wealth attracts merchants and wagons, war adds guards, returning world armies use
the gate road. At most 44 ambient groups are drawn, independently of army size.
Residential, market, military, workshop, palace, diplomatic and cultural districts
use their corresponding buildings; the academy and embassy have separate named
quarters. Society metrics display the persistent Kingdoms simulation;
the food forecast explicitly assumes unchanged income and expenses.

Campaign days cycle the visual clock through morning, day, evening and night.
The city preview can switch lighting without changing the campaign. Disabling
animations avoids creating an infinite transition and leaves a static scene.
Persistent WarState building damage draws cracks, debris and blackened structures;
active siege additionally emits bounded smoke and fire. The building sheet shows
the actual effective level and repair resources, invoking WarEngine.repairBuilding.
The city navigation callback opens the same army, court, diplomacy and campaign
screens as the app navigation. Market navigation opens actual local trade.

PresentationState is a separate serializable saved domain. A geometric heraldry
editor persists primary color, symbol color and symbol and exposes shared city,
map, battle and UI rendering primitives. The palette prevents identical colors;
symbols have geometric shapes plus names, rather than color-only identity.
Hall of Legends records generals (level 6, ten victories), veteran unit pools
(500 soldiers, 85 experience), underdog wins against at least 1,000 opponents and
high kings. Earned general statues restore +1 home troop morale per day each,
capped at +3, proportional to the home part of an aggregated pool and applied
once per campaign day. Chronicles record each legend once.

Ten local achievements use real campaign counters and persistent battle history.
Each awards its documented renown once; a clean victory excludes all four casualty
categories. Records retain maximum army, population, territories, gross production,
victory/defeat sizes and duration even after later losses. Tutorials use a saved
seen set and are shown on the first visit to a system, dismissed individually.

GameSoundscape plays 48 bundled original uncompressed WAV files without network.
Twelve effects cover swords, arrows, horns, horses, artillery, creatures, gates,
fire, city, market, forge and rain. Nine original eight-second music loops cover
menu, city, court tension, map, impending war, battle, critical battle, victory and
defeat, each with four culture-specific timbres. Music uses AudioFocus and a
1.5-second volume crossfade; asynchronous preparation avoids blocking the UI.
The lifecycle pauses all sounds while backgrounded and releases players/pool when
disposed. Persisted battle minute cues and world movement changes trigger effects.
Haptics use Android view feedback for battle start, gate impacts, level-ups and
important chronicle decisions and respect the independent settings toggle.

Audio assets were generated from the bundled stdlib-only generate_audio.py; no
third-party samples or compositions are present. They are CC0-1.0 original
procedural material (16 kHz mono signed 16-bit PCM), approximately 10 MB total.
They supply a working atmospheric score and effects, not a claim of recorded
orchestration or physical-device sound quality. Hardware audio focus, background
behavior, accessibility and frame timing still require the device profiles.

V06PresentationTest covers thresholds, one-time awards, casualty-sensitive
achievements, legend/home morale, monotonic records, save persistence, context
tutorials, responsive bounded activity and music scene/culture decisions. The
integrating root agent records fresh test/build/lint outcomes in the main v0.6
implementation document; this document does not claim checks before execution.
