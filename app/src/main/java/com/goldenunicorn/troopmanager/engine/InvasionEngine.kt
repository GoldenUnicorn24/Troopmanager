package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object InvasionEngine {
    fun day(state: GameState): GameState {
        var next = state
        val old = next.realm.threat
        val threat = (old + 2 + next.realm.territory + next.victories / 4).coerceAtMost(100)
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
                                (next.chronicle + ChronicleEntry(next.day, label, detail)).takeLast(
                                    80
                                )
                        )
                }
            }
        if (threat >= 90 && next.invasion == null) {
            val enemy =
                when {
                    next.victories >= 6 || next.completedRealm -> EnemyType.TAO_TEI
                    next.realm.territory >= 3 || next.victories >= 3 -> EnemyType.URUK
                    else -> EnemyType.ORC
                }
            val strength =
                (250L +
                        next.realm.territory * 100L +
                        next.victories * 80L +
                        if (next.completedRealm) next.armySize / 2 else 0)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
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
                            .takeLast(80),
                )
            next = RelationshipEngine.onEvent(next, "attack")
        }
        val invasion = next.invasion
        if (invasion != null && next.day < invasion.arrivalDay)
            next = next.copy(realm = next.realm.copy(threat = next.realm.threat.coerceAtMost(99)))
        // A known arrival date guarantees the advertised preparation window even at 100% threat.
        if (
            invasion != null &&
                next.day >= invasion.arrivalDay &&
                next.battleSession?.isActive != true
        ) {
            next = next.copy(realm = next.realm.copy(threat = 100))
            if (next.homeArmySize > 0)
                next =
                    BattleEngine.start(
                            next,
                            invasion.enemy,
                            Tactic.FORTIFY,
                            enemyStrength = invasion.strength,
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
                                        "Die Invasion plündert die Festung. Missionstruppen bleiben unterwegs. Die Siedlung kann sich erholen.",
                                    ))
                                .takeLast(80),
                    )
                next = RelationshipEngine.onEvent(next, "defeat")
            }
        }
        return next
    }
}
