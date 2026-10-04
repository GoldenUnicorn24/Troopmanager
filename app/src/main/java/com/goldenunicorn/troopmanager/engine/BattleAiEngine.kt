package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Intent changes movement/targeting, never authorizes an otherwise illegal attack. */
object BattleAiEngine {
    fun intent(battle: BattleSession, front: BattleFront): BattleAiIntent {
        if (front.morale < 22 || front.cohesion < 15 || front.enemySoldiers < front.enemyStart / 5) return BattleAiIntent.WITHDRAW
        val devices = battle.siegeDevices.filter { it.section == front.section && !it.disabled && it.crew > 0 }
        if (devices.any { it.type == SiegeDevice.RAM && it.distance == 0 }) return BattleAiIntent.GATE
        if (battle.segment(front.section)?.contactState?.allowsMelee == true) return BattleAiIntent.ASSAULT
        if (battle.enemy == EnemyType.URUK && devices.any { it.type == SiegeDevice.CATAPULT && it.ammunition > 0 }) return BattleAiIntent.ARTILLERY
        val shooters = battle.enemyRoster.filter { it.section == front.section && it.type.ranged >= 8 }.sumOf { it.soldiers }
        return if ((battle.enemyArrowsRemaining > 0 || battle.enemyArtilleryRemaining > 0) && shooters * 2 > front.enemySoldiers && front.enemyDistance <= 200) BattleAiIntent.MISSILES else BattleAiIntent.APPROACH
    }
}
