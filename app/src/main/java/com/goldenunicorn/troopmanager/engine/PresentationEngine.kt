package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Campaign evidence, cosmetic identity and tutorials share the saved campaign state. */
object PresentationEngine {
    val bannerPalette = listOf(
        0xFF203B57.toInt(), 0xFF762F35.toInt(), 0xFF22513C.toInt(), 0xFF493560.toInt(),
        0xFF24272C.toInt(), 0xFFFFE2A1.toInt(), 0xFFDAE4E8.toInt(), 0xFFC3843C.toInt(),
    )

    fun setHeraldry(state: GameState, heraldry: Heraldry): GameEngine.ActionResult {
        if (heraldry.primaryArgb !in bannerPalette || heraldry.secondaryArgb !in bannerPalette)
            return GameEngine.ActionResult(state, "Bitte eine Farbe aus der Wappenpalette wählen.")
        if (heraldry.primaryArgb == heraldry.secondaryArgb)
            return GameEngine.ActionResult(state, "Grundfarbe und Symbolfarbe müssen unterscheidbar sein.")
        return GameEngine.ActionResult(
            state.copy(presentation = state.presentation.copy(heraldry = heraldry)),
            "Dein Banner wurde für das gesamte Reich übernommen.",
        )
    }

    fun markTutorialSeen(state: GameState, id: String): GameState = state.copy(
        presentation = state.presentation.copy(tutorialsSeen = state.presentation.tutorialsSeen + id)
    )

    fun tutorial(state: GameState, context: String): ContextTutorial? {
        val tip = when (context.lowercase()) {
            "city", "stadt" -> ContextTutorial("city", "Deine Stadt lebt", "Gebäude antippen: Kaserne führt zum Heer, Residenz zum Hof, Botschaft zur Diplomatie und Mauer zur Welt. Nahrung, Wohlstand und Krieg verändern die Straßen. Die Gebäudeliste bietet dieselben Ziele ohne Gesten.")
            "army", "armee" -> ContextTutorial("army", "Ein Heer, mehrere Aufgaben", "Missionen und Weltmärsche verwenden dieselben Soldaten. Abwesende Truppen können nicht erneut entsandt werden. Nahrung und Moral bestimmen die Einsatzbereitschaft.")
            "world", "welt", "regions" -> ContextTutorial("world", "Ein Feldzug braucht Nachschub", "Weltarmeen marschieren mit Reisezeit. Kundschafter und Verbündete verbessern die Sicht; unbekannte Stärke ist eine Schätzung. Beobachte Ankunft und Versorgung vor dem Aufbruch.")
            "battle", "schlacht" -> ContextTutorial("battle", "Führung unter Druck", "Aufstellung, Gelände und Moral entscheiden die Schlacht. Befehle brauchen Command Points; Reserve und geordneter Rückzug können Soldaten retten.")
            "court", "hof", "relationship" -> ContextTutorial("court", "Menschen mit eigenen Grenzen", "Ämter wirken durch Fähigkeiten und Loyalität. Freundschaft ist ein eigener Weg. Romanze benötigt beiderseitiges Interesse; jede Ablehnung und Grenze bleibt wirksam.")
            "diplomacy", "diplomatie" -> ContextTutorial("diplomacy", "Verträge sind Verhandlungen", "Angebote werden nach Nutzen, Risiko und Vertrauen bewertet. Handel benötigt sichere Wege; gebrochene Verträge kosten Glaubwürdigkeit.")
            else -> null
        }
        return tip?.takeUnless { it.id in state.presentation.tutorialsSeen }
    }

    fun cityActivity(state: GameState, night: Boolean = CityTime.at(state.day) == CityTime.NIGHT): CityActivity {
        val p = EconomyEngine.production(state)
        val hungry = state.society.hunger >= 25 || state.resources.food.toLong() + p.gross.food < p.upkeep
        val atWar = state.invasion != null || state.battleSession?.isActive == true ||
            state.realm.threat >= 40 || state.world.factions.any { PLAYER_FACTION in it.wars }
        val citizens = ((state.city.satisfaction / 7 + 3) / (if (hungry) 3 else 1) / (if (night) 2 else 1)).coerceIn(1, 18)
        val merchants = if (hungry || atWar) 1 else (state.city.prosperity / 15).coerceIn(1, 6)
        val wagons = if (hungry || atWar || night) 0 else (state.city.prosperity / 25).coerceIn(0, 4)
        val soldiers = (if (atWar) 10 else 2) + (state.city.security / 30)
        val returning = state.world.armies.count { it.factionId == PLAYER_FACTION && it.status == WorldArmyStatus.RETURNING }.coerceAtMost(3)
        return CityActivity(citizens, merchants, wagons, soldiers, hungry, atWar, returning)
    }

    fun tick(state: GameState): GameState {
        val history = state.war.history
        val victories = history.filter { it.victory }
        fun achieved(a: Achievement): Boolean = when (a) {
            Achievement.FIRST_VICTORY -> state.victories > 0 || victories.isNotEmpty()
            Achievement.ARMY_10K -> state.armySize >= 10_000
            Achievement.ARMY_100K -> state.armySize >= 100_000
            Achievement.KING -> state.title == "König" || state.title == "Hochkönig"
            Achievement.HIGH_KING -> state.title == "Hochkönig"
            Achievement.LEGENDARY_GENERAL -> state.commanders.any { it.level >= 6 && it.victories >= 10 }
            Achievement.DAYS_100 -> state.day >= 100
            Achievement.DAYS_365 -> state.day >= 365
            Achievement.LOSSLESS_VICTORY -> victories.any { it.casualties.dead + it.casualties.wounded + it.casualties.missing + it.casualties.captured == 0 }
            Achievement.OUTNUMBERED_VICTORY -> victories.any { it.ownStart < it.enemyStart }
        }
        val existing = state.presentation.achievements.map { it.achievement }.toSet()
        val earned = Achievement.entries.filter { it !in existing && achieved(it) }
        val legends = state.presentation.legends.toMutableList()
        fun addLegend(entry: HallLegendEntry) { if (legends.none { it.id == entry.id }) legends += entry }
        state.commanders.filter { it.level >= 6 && it.victories >= 10 }.forEach {
            addLegend(HallLegendEntry("general_${it.id}", "${it.name} – Schild des Reiches", LegendKind.GENERAL,
                state.day, "${it.victories} Siege, ${it.battlesFought} Schlachten. Ihre Statue stärkt die Moral der Heimattruppen um 1 pro Tag (höchstens +3).", it.id))
        }
        state.armyPools.filter { it.soldiers >= 500 && it.experience >= 85 }.forEach {
            addLegend(HallLegendEntry("unit_${it.type.name}", "Die ehrwürdigen ${it.type.label}", LegendKind.UNIT,
                state.day, "Ein Veteranenverband von ${it.soldiers} Soldaten erreichte Erfahrung ${it.experience}."))
        }
        history.filter { it.victory && it.ownStart < it.enemyStart && it.enemyStart >= 1000 }.forEach {
            addLegend(HallLegendEntry("battle_${it.id}", "Sieg bei ${it.place}", LegendKind.BATTLE, it.day,
                "${it.ownStart} gegen ${it.enemyStart}: ${it.ownRemaining} kehrten zurück. Dauer ${it.minute} Minuten."))
        }
        if (state.title == "Hochkönig") addLegend(HallLegendEntry("ruler_${state.player.name}", state.player.name,
            LegendKind.RULER, state.day, "Vereinte das Reich unter der letzten Mauer. Die Kampagne geht weiter."))
        val old = state.presentation.records
        val gross = EconomyEngine.production(state).gross
        val records = CampaignRecords(
            largestArmy = maxOf(old.largestArmy, state.armySize),
            largestVictory = maxOf(old.largestVictory, victories.maxOfOrNull { it.enemyStart } ?: 0),
            largestDefeat = maxOf(old.largestDefeat, history.filter { !it.victory }.maxOfOrNull { it.ownStart } ?: 0),
            longestBattleMinutes = maxOf(old.longestBattleMinutes, history.maxOfOrNull { it.minute } ?: 0),
            highestPopulation = maxOf(old.highestPopulation, state.population.total),
            mostTerritories = maxOf(old.mostTerritories, state.realm.territory),
            highestDailyProduction = maxOf(old.highestDailyProduction, ResourceKind.entries.sumOf { it.value(gross).toLong() }),
        )
        val generalStatues = legends.count { it.kind == LegendKind.GENERAL }.coerceAtMost(3)
        val moraleGain = if (state.presentation.lastLegendMoraleDay < state.day) generalStatues else 0
        val chronicle = earned.map { ChronicleEntry(state.day, "Erfolg: ${it.label}", "${it.description} +${it.renown} Ruhm.") } +
            legends.filter { entry -> state.presentation.legends.none { it.id == entry.id } }.map { ChronicleEntry(it.day, "Halle der Legenden", "${it.name}: ${it.account}") }
        return state.copy(
            presentation = state.presentation.copy(achievements = state.presentation.achievements + earned.map { AchievementUnlock(it, state.day) },
                legends = legends, records = records, lastLegendMoraleDay = state.day),
            renown = (state.renown.toLong() + earned.sumOf { it.renown }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            armyPools = if (moraleGain > 0) state.armyPools.map { pool ->
                val gain = if (pool.soldiers > 0) moraleGain.toLong() * state.homeSoldiers(pool.type) / pool.soldiers else 0L
                pool.copy(morale = (pool.morale + gain.toInt()).coerceAtMost(100))
            } else state.armyPools,
            chronicle = if (chronicle.isEmpty()) state.chronicle else (state.chronicle + chronicle).takeLast(2000),
        )
    }
}
