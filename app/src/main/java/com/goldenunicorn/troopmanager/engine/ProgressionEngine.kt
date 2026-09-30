package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object ProgressionEngine {
    fun awardXp(state: GameState, amount: Int): GameState {
        var p = state.player.copy(experience = state.player.experience + amount.coerceAtLeast(0))
        while (p.experience >= p.level * 100) p =
            p.copy(
                experience = p.experience - p.level * 100,
                level = p.level + 1,
                skillPoints = p.skillPoints + 3,
            )
        return state.copy(player = p)
    }

    fun spendPoint(state: GameState, skill: String): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Fertigkeiten nach der Schlacht verteilen.")
        val p = state.player
        if (p.skillPoints <= 0)
            return GameEngine.ActionResult(state, "Keine Fertigkeitspunkte verfügbar.")
        val value =
            when (skill) {
                "sword" -> p.sword
                "bow" -> p.bow
                "riding" -> p.riding
                "leadership" -> p.leadership
                "tactics" -> p.tactics
                "diplomacy" -> p.diplomacy
                else -> return GameEngine.ActionResult(state, "Unbekannte Fertigkeit.")
            }
        if (value >= 100) return GameEngine.ActionResult(state, "Fertigkeit bereits am Maximum.")
        val next =
            when (skill) {
                "sword" -> p.copy(sword = value + 1)
                "bow" -> p.copy(bow = value + 1)
                "riding" -> p.copy(riding = value + 1)
                "leadership" -> p.copy(leadership = value + 1)
                "tactics" -> p.copy(tactics = value + 1)
                else -> p.copy(diplomacy = value + 1)
            }.copy(skillPoints = p.skillPoints - 1)
        return GameEngine.ActionResult(state.copy(player = next), "Fertigkeit verbessert.")
    }

    fun update(state: GameState): GameState {
        val r = state.realm
        val tier =
            when {
                r.territory >= 7 || r.level(BuildingType.PALACE) >= 5 -> SettlementTier.CAPITAL
                r.territory >= 4 || r.level(BuildingType.PALACE) >= 3 -> SettlementTier.FORTRESS
                r.territory >= 3 -> SettlementTier.WALL_CITY
                r.territory >= 2 -> SettlementTier.CASTLE
                else -> SettlementTier.BORDER_KEEP
            }
        val title =
            when {
                r.territory >= 8 && state.armySize >= 10000 && r.level(BuildingType.WALL) >= 5 ->
                    "Hochkönig"
                r.territory >= 5 -> "König"
                r.territory >= 4 -> "Fürst"
                r.territory >= 3 -> "Regionaler Herrscher"
                r.territory >= 2 -> "Landherr"
                else -> "Grenzherr"
            }
        val rank =
            when {
                state.renown >= 2200 -> "Marschall"
                state.renown >= 1300 -> "General"
                state.renown >= 700 -> "Kommandant"
                state.renown >= 300 -> "Veteran"
                else -> "Grenzhauptmann"
            }
        var next =
            state.copy(
                title = title,
                rank = rank,
                realm = r.copy(settlementTier = tier),
                completedRealm = state.completedRealm || title == "Hochkönig",
            )
        if (!state.completedRealm && next.completedRealm)
            next =
                next.copy(
                    chronicle =
                        (next.chronicle +
                                ChronicleEntry(
                                    next.day,
                                    "Hochkönigreich",
                                    "Das Reich steht. Größere Invasionen und politische Herausforderungen warten weiterhin.",
                                ))
                            .takeLast(80)
                )
        return next
    }
}
