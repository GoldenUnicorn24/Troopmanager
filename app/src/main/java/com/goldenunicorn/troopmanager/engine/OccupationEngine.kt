package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object OccupationEngine {
    fun onOwnershipChanged(
        state: GameState,
        regionId: String,
        previousOwnerId: String,
        newOwnerId: String,
    ): GameState {
        if (previousOwnerId == newOwnerId) return state
        if (newOwnerId == PLAYER_FACTION &&
            previousOwnerId != PLAYER_FACTION &&
            previousOwnerId != NEUTRAL_FACTION
        ) {
            if (state.occupations.any { it.regionId == regionId }) return state
            return state.copy(
                occupations =
                    state.occupations +
                        OccupiedRegion(
                            regionId = regionId,
                            previousOwnerId = previousOwnerId,
                            sinceDay = state.day,
                        ),
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Gebiet besetzt",
                                "${state.world.place(regionId)?.name ?: regionId} steht unter Militärverwaltung. Unruhe muss durch Garnison, Integration oder Autonomie gesenkt werden.",
                            ))
                        .takeLast(2000),
            )
        }
        if (previousOwnerId == PLAYER_FACTION && newOwnerId != PLAYER_FACTION)
            return state.copy(occupations = state.occupations.filterNot { it.regionId == regionId })
        return state
    }

    fun setPolicy(
        state: GameState,
        regionId: String,
        policy: OccupationPolicy,
    ): GameEngine.ActionResult {
        val occupation =
            state.occupations.firstOrNull { it.regionId == regionId }
                ?: return GameEngine.ActionResult(state, "Dieses Gebiet steht nicht unter Besatzungsverwaltung.")
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Besatzungspolitik kann nach der Schlacht geändert werden.")
        if (occupation.policy == policy)
            return GameEngine.ActionResult(state, "Diese Politik ist bereits aktiv.")
        return GameEngine.ActionResult(
            state.copy(
                occupations =
                    state.occupations.map {
                        if (it.regionId == regionId) it.copy(policy = policy) else it
                    },
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Besatzungspolitik",
                                "${state.world.place(regionId)?.name ?: regionId}: ${policy.label}.",
                            ))
                        .takeLast(2000),
            ),
            "${policy.label} gilt nun in ${state.world.place(regionId)?.name ?: regionId}.",
        )
    }

    fun assignGovernor(
        state: GameState,
        regionId: String,
        commanderId: Long?,
    ): GameEngine.ActionResult {
        val occupation =
            state.occupations.firstOrNull { it.regionId == regionId }
                ?: return GameEngine.ActionResult(state, "Dieses Gebiet steht nicht unter Besatzungsverwaltung.")
        if (commanderId != null) {
            val commander = state.commanders.firstOrNull { it.id == commanderId }
                ?: return GameEngine.ActionResult(state, "Kommandant nicht gefunden.")
            if (state.commanderAway(commanderId) || state.war.unavailableCommander(commanderId))
                return GameEngine.ActionResult(state, "${commander.name} ist derzeit nicht verfügbar.")
        }
        return GameEngine.ActionResult(
            state.copy(
                occupations =
                    state.occupations.map {
                        if (it.regionId == occupation.regionId)
                            it.copy(governorId = commanderId)
                        else it
                    }
            ),
            if (commanderId == null)
                "Statthalterposten freigegeben."
            else "${state.commanders.first { it.id == commanderId }.name} übernimmt die Verwaltung.",
        )
    }

    fun tick(state: GameState): GameState {
        if (state.occupations.isEmpty()) return state
        var next = state
        val active = mutableListOf<OccupiedRegion>()

        state.occupations.forEach { occupation ->
            val place = next.world.place(occupation.regionId)
            if (place?.ownerId != PLAYER_FACTION) return@forEach

            val garrison =
                next.world.armies
                    .filter {
                        it.factionId == PLAYER_FACTION &&
                            it.regionId == occupation.regionId &&
                            it.status != WorldArmyStatus.DESTROYED
                    }
                    .sumOf { it.total }
            val governorSkill =
                occupation.governorId?.let { id ->
                    val commander = next.commanders.firstOrNull { it.id == id }
                    val details = next.court.characters.firstOrNull { it.commanderId == id }
                    if (
                        commander == null ||
                            details == null ||
                            next.commanderAway(id) ||
                            next.war.unavailableCommander(id)
                    ) 0
                    else (details.stewardship + commander.leadership) / 2
                } ?: 0

            var unrestDelta =
                when (occupation.policy) {
                    OccupationPolicy.MILITARY_RULE ->
                        if (garrison >= 500) -6 else if (garrison >= 150) -3 else 3
                    OccupationPolicy.INTEGRATION -> -4
                    OccupationPolicy.AUTONOMY -> -3
                }
            unrestDelta -= governorSkill / 35
            if (next.society.culturalTension >= 60) unrestDelta += 2
            if (next.resources.food <= 0) unrestDelta += 3

            when (occupation.policy) {
                OccupationPolicy.MILITARY_RULE -> {
                    next =
                        next.copy(
                            resources =
                                next.resources.copy(
                                    gold = (next.resources.gold - 20).coerceAtLeast(0)
                                ),
                            world =
                                next.world.copy(
                                    places =
                                        next.world.places.map {
                                            if (it.id == occupation.regionId)
                                                it.copy(
                                                    prosperity =
                                                        (it.prosperity - 1).coerceAtLeast(0)
                                                )
                                            else it
                                        }
                                ),
                        )
                }
                OccupationPolicy.INTEGRATION -> {
                    val goldCost = minOf(15, next.resources.gold)
                    val foodCost = minOf(40, next.resources.food)
                    next =
                        next.copy(
                            resources =
                                next.resources.copy(
                                    gold = next.resources.gold - goldCost,
                                    food = next.resources.food - foodCost,
                                ),
                            society =
                                next.society.copy(
                                    culturalTension =
                                        (next.society.culturalTension - 1).coerceAtLeast(0)
                                ),
                        )
                    if (goldCost < 15 || foodCost < 40) unrestDelta += 3
                }
                OccupationPolicy.AUTONOMY -> {
                    next =
                        next.copy(
                            world =
                                next.world.copy(
                                    places =
                                        next.world.places.map {
                                            if (it.id == occupation.regionId)
                                                it.copy(
                                                    prosperity =
                                                        (it.prosperity + 1).coerceAtMost(100)
                                                )
                                            else it
                                        }
                                )
                        )
                }
            }

            val unrest = (occupation.unrest + unrestDelta).coerceIn(0, 100)
            if (unrest >= 95 && garrison < 100) {
                val regions =
                    next.regions.map {
                        if (it.id == occupation.regionId) it.copy(owned = false) else it
                    }
                next =
                    next.copy(
                        regions = regions,
                        realm =
                            next.realm.copy(
                                territory = (next.realm.territory - 1).coerceAtLeast(1)
                            ),
                        world =
                            next.world.copy(
                                places =
                                    next.world.places.map {
                                        if (it.id == occupation.regionId)
                                            it.copy(ownerId = NEUTRAL_FACTION)
                                        else it
                                    }
                            ),
                        chronicle =
                            (next.chronicle +
                                    ChronicleEntry(
                                        next.day,
                                        "Aufstand",
                                        "${place.name} entzieht sich deiner Herrschaft. Eine zu kleine Garnison konnte die Unruhe nicht kontrollieren.",
                                    ))
                                .takeLast(2000),
                    )
            } else if (unrest <= 10) {
                next =
                    next.copy(
                        chronicle =
                            (next.chronicle +
                                    ChronicleEntry(
                                        next.day,
                                        "Gebiet integriert",
                                        "${place.name} gilt nun als stabiler Teil des Reiches.",
                                    ))
                                .takeLast(2000)
                    )
            } else {
                active += occupation.copy(unrest = unrest)
            }
        }

        return next.copy(occupations = active)
    }
}
