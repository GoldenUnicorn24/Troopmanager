package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object InvasionEngine {
    fun day(state: GameState): GameState {
        var next = state
        val old = next.realm.threat
        val threat = (old.toLong() + 2 + next.realm.territory + next.victories / 4).coerceAtMost(100).toInt()
        next = next.copy(realm = next.realm.copy(threat = threat))
        listOf(40 to "Späherwarnung", 60 to "Grenzüberfälle", 75 to "Große feindliche Bewegung")
            .forEach { (threshold, label) ->
                if (old < threshold && threat >= threshold) {
                    var detail = "Die Bedrohung erreicht $threshold %."
                    if (threshold == 60) {
                        next =
                            next.copy(
                                resources =
                                    EconomyEngine.add(
                                        next.resources,
                                        Resources(-150, -200, -100, 0, 0),
                                    )
                            )
                        detail += " Überfälle kosten Vorräte: 150 Gold, 200 Nahrung und 100 Holz."
                    }
                    next =
                        next.copy(
                            chronicle =
                                (next.chronicle + ChronicleEntry(next.day, label, detail)).takeLast(2000)
                        )
                }
            }
        if (threat >= 90 && next.invasion == null) {
            val enemy =
                when {
                    next.day < 25 -> EnemyType.ORC
                    next.day >= 60 && (next.victories >= 6 || next.completedRealm || next.day >= 90) -> EnemyType.TAO_TEI
                    next.realm.territory >= 3 || next.victories >= 3 || next.day >= 45 -> EnemyType.URUK
                    else -> EnemyType.ORC
                }
            val rawStrength =
                250L +
                    next.realm.territory * 100L +
                    next.victories * 80L +
                    if (next.completedRealm) next.armySize / 2 else 0
            val strength =
                (rawStrength * DifficultyEngine.invasionStrengthFactor(next))
                    .toLong()
                    .coerceIn(1, Int.MAX_VALUE.toLong())
                    .toInt()
            val delay = 6 + if (next.realm.scoutingDays > 0) 2 else 0
            val devices =
                when (enemy) {
                    EnemyType.ORC -> listOf(SiegeDevice.RAM, SiegeDevice.LADDERS)
                    EnemyType.URUK ->
                        listOf(SiegeDevice.RAM, SiegeDevice.TOWER, SiegeDevice.CATAPULT)
                    EnemyType.TAO_TEI -> listOf(SiegeDevice.CLIMBERS)
                }
            next =
                next.copy(
                    invasion = Invasion(enemy, next.day + delay, strength, next.day, devices),
                    chronicle =
                        (next.chronicle +
                                ChronicleEntry(
                                    next.day,
                                    "Invasion angekündigt",
                                    "${enemy.label}: $strength Gegner erreichen die Festung in $delay Tagen.",
                                ))
                            .takeLast(2000),
                )
            next = RelationshipEngine.onEvent(next, "attack")
        }
        if (next.world.initialized && next.invasion != null) next = WorldEngine.bindInvasion(next)
        val invasion = next.invasion
        if (invasion != null && next.day < invasion.arrivalDay)
            next = next.copy(realm = next.realm.copy(threat = next.realm.threat.coerceAtMost(99)))
        // A known arrival date guarantees the advertised preparation window even at 100% threat.
        if (
            invasion != null &&
                next.day >= invasion.arrivalDay &&
                next.battleSession?.isActive != true
        ) {
            val worldArmy = invasion.worldArmyId?.let { id -> next.world.armies.firstOrNull { it.id == id } }
            if (worldArmy != null && worldArmy.regionId != "keep") return next
            if (worldArmy != null) next = next.copy(world = next.world.copy(armies = next.world.armies.map {
                if (it.id == worldArmy.id) it.copy(status = WorldArmyStatus.ENGAGED) else it
            }))
            next = next.copy(realm = next.realm.copy(threat = 100))
            if (next.homeArmySize == 0) next = FrontierEngine.armNottruppe(next)
            if (next.homeArmySize > 0)
                next =
                    BattleEngine.start(
                            next,
                            invasion.enemy,
                            Tactic.FORTIFY,
                            enemyStrength = worldArmy?.total ?: invasion.strength,
                            enemyFactionId = worldArmy?.factionId,
                            enemyFactionName = worldArmy?.factionId?.let { next.world.faction(it)?.name },
                            enemyArmyName = worldArmy?.name,
                            enemyUnits = worldArmy?.units.orEmpty(),
                            enemyMorale = worldArmy?.morale ?: 80,
                            enemyExperience = worldArmy?.enemyCommanderId?.let { id -> next.world.enemyCommanders.firstOrNull { it.id == id }?.experience } ?: 0,
                            location = next.realm.settlementName,
                            rangedWeather = next.world.weather.at("keep").rangedFactor,
                            cavalryWeather = next.world.weather.at("keep").cavalryFactor,
                            seasonPenalty = if (next.world.weather.season == Season.WINTER) .8 else 1.0,
                        )
                        .state
            else {
                next =
                    next.copy(
                        invasion = null,
                        defeats = next.defeats + 1,
                        realm =
                            next.realm.copy(
                                threat = 45,
                                wallIntegrity = (next.realm.wallIntegrity - 35).coerceAtLeast(0),
                            ),
                        resources =
                            EconomyEngine.add(
                                next.resources,
                                Resources(
                                    -next.resources.gold / 4,
                                    -next.resources.food / 4,
                                    0,
                                    0,
                                    0,
                                ),
                            ),
                        chronicle =
                            (next.chronicle +
                                    ChronicleEntry(
                                        next.day,
                                        "Unverteidigte Festung",
                                        "Keine Soldaten und keine Nottruppe an der Mauer. Die Invasion plündert die Festung.",
                                    ))
                                .takeLast(2000),
                    )
                next = RelationshipEngine.onEvent(next, "defeat")
                if (worldArmy != null) next = next.copy(world = next.world.copy(invasionArmyId = null,
                    armies = next.world.armies.map { if (it.id == worldArmy.id) it.copy(status = WorldArmyStatus.HOLDING) else it }))
            }
        }
        return next
    }
}
