# v0.6 People, Court and Dynasty

## Implemented simulation

`CourtState` owns persistent character details, service careers, offices, social links,
player perks and recognized legends. Existing `Commander` identities and army counts
remain unchanged. Character initialization is deterministic and preserves existing
history. Character details include age, culture/origin, personality, ambition,
courage, diplomacy, stewardship, intrigue, medicine, family, friends/rivals and opinion.

Promotions require recorded victories and gold. They increase leadership and loyalty;
eligible ambitious peers can lose loyalty and develop rivalries. Long overlooked
commanders react every thirty days. Ten victories or a recorded victory in inferior
numbers recognizes a legend once, writes the chronicle and adds a bounded morale bonus.
Battle results append career entries and shared companion memories without incrementing
the commander's existing battle statistics twice.

All seven offices use relevant skills. The treasurer contributes to the actual economic
production breakdown; marshal and legends affect battle morale; ambassador and spymaster
feed the diplomacy/espionage engines; builder periodically advances construction;
physician aids recovery; steward raises security and satisfaction. An unavailable,
disloyal or deceased office holder supplies no bonus. One person holds one office.

Thirteen player perks span WARFARE, LEADERSHIP, DIPLOMACY, ECONOMY, INTRIGUE and PERSONAL.
They use existing skill points and change battle behavior, reserve commands, court
mediation, negotiations, logistics, economic output, scouting, counterintelligence or
conversation/training effects. Courage, loyalty and rank produce the commander's
battle morale contribution.

## Relationships and boundaries

High trust, respect and affection establish friendship only. Romantic stages are stored
explicitly and require separate successful voluntary proposals: mutual interest, romance,
partnership, engagement, marriage/life partnership, co-rulership. NPCs can decline,
request time, maintain boundaries, disagree politically and end a conflicted partnership.
Marriage is optional; friendship and adoption support a non-romantic campaign.

`ConsentProfile` persists romantic/private/family preferences, relationship rules,
boundaries and privacy. Boundary discussions cannot purchase consent. Relationships
persist attraction, intimacy, loyalty, conflict, jealousy, commitment and significant
memories. Jealousy naturally decreases and is not a routine punitive trigger.

All romantic/private proposals gate both participants at age 18, respect OFF/ROMANCE/
MATURE settings and exclude captivity and unavailable participants. Intimacy additionally
requires an established adult partnership, sufficient trust, respect and commitment,
low conflict, permitted boundaries and fresh same-day consent. It is an offline narrative
choice with FADE_TO_BLACK presentation, consumes consent, has a seven-day cooldown, and
does not award affection or resources. Declining has no relationship penalty. Settings
normalization clears unavailable private events; action resolution rechecks every gate.

Memories record refusals, consent discussions, proposals, first kiss, reconciliation,
shared battle results, family planning, loss and separation. Memory/chronicle histories
are bounded. NPC court links develop bounded friendships/rivalries and, with settings
enabled and adult mutual preferences, romances/partnerships. The companion never enters
an automatically generated NPC romance.

Partners retain their own command and political opinions. Weekly independent action
can support food-policy satisfaction or diplomatic trade. Sustained severe conflict
can cause an NPC-initiated separation. Separation revokes private/family consent and
co-ruler trade support.

## Optional dynasty

Dynasty defaults OFF. Enabling it adds a persistent family without altering army
ownership. One campaign year is 365 days. Disabling pauses family ages and timers.
Family planning has independent mutual adult consent and a 270-day arrival timer,
with no explicit conception scene and no required intimacy event. Adoption works without
romance and costs actual care resources. Members and selected heirs persist in saves.

Old-age death at 85 triggers succession to a designated or eligible heir. A minor heir
has an adult commander/council regency and no romantic access. An heirless ruler has a
council-appointed adult successor; the campaign continues. Succession clears former
ruler perks/romance rather than inheriting a parent's partner. Legitimacy affects weekly
satisfaction; a loyal regent sustains diplomatic trade. This is an abstract family model:
it does not simulate medical pregnancy, genetics or individual childhood activities.

## Persistence and verification

New fields have serialization defaults. Migration preserves companion scores and never
infers marriage from previous score-based labels. `CharacterEngine.validate` rejects
invalid metrics, duplicate people/memories, invalid office/social links, minor romance,
malformed private events and invalid family/heir references. Fresh v0.6 save roundtrips
preserve all stored choices and memories.

Twenty `V06PeopleTest` tests cover friendship, separate voluntary stage progression, refusal and
boundaries, minors, private-event prerequisites, setting gates, renewed consent,
cooldowns, non-punitive refusal, player/NPC separation, court/skills/memory save roundtrip,
legacy migration, office suspension, promotions/rivals, one-time legends, optional
dynasty/pause/adoption, family consent/arrival, minor-heir regency and age-edit protection,
milestone retention across bounded casual memories and grief after a fallen companion.

Integrated test/build/lint evidence is recorded by the main v0.6 implementation report;
this subsystem document does not claim unexecuted checks.
