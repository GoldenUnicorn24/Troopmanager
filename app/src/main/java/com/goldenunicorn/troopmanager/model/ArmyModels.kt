package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class ArmyUnitPool(
    val type: UnitType,
    val soldiers: Int,
    val experience: Int = 15,
    val morale: Int = 80,
    val equipment: Int = 80,
) {
    val power: Int
        get() =
            (soldiers.toDouble() *
                    (type.attack + type.defense + type.ranged) *
                    (1.0 + experience / 200.0) *
                    (0.5 + morale / 200.0) *
                    (0.5 + equipment / 200.0))
                .toInt()

    val foodPerSoldier: Int
        get() =
            when {
                type == UnitType.KNIGHT || type == UnitType.DRAGON_ARTILLERY -> 3
                type.culture == Culture.GOLD_ELF -> 2
                else -> 1
            }
}
