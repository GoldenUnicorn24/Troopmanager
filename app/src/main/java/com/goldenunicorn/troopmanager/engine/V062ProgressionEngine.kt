package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

object DifficultyEngine {
    fun scoutInterval(state: GameState): Int = when (state.settings.difficulty) {
        Difficulty.STORY -> 7
        Difficulty.STANDARD -> 5
        Difficulty.VETERAN -> 3
    }

    fun recruitmentInterval(state: GameState): Int = when (state.settings.difficulty) {
        Difficulty.STORY -> 8
        Difficulty.STANDARD -> 6
        Difficulty.VETERAN -> 5
    }

    fun attackInterval(state: GameState, aggressive: Boolean): Int {
        val base = if (aggressive) 4 else 9
        return when (state.settings.difficulty) {
            Difficulty.STORY -> base + 3
            Difficulty.STANDARD -> base
            Difficulty.VETERAN -> (base - 2).coerceAtLeast(3)
        }
    }

    fun intelMemoryDays(state: GameState): Int = when (state.settings.difficulty) {
        Difficulty.STORY -> 2
        Difficulty.STANDARD -> 3
        Difficulty.VETERAN -> 5
    }

    fun invasionStrengthFactor(state: GameState): Double = when (state.settings.difficulty) {
        Difficulty.STORY -> 0.85
        Difficulty.STANDARD -> 1.0
        Difficulty.VETERAN -> 1.15
    }

    fun missionDifficultyFactor(state: GameState): Double = when (state.settings.difficulty) {
        Difficulty.STORY -> 0.88
        Difficulty.STANDARD -> 1.0
        Difficulty.VETERAN -> 1.10
    }
}

object DoctrineEngine {
    fun trainingFactor(state: GameState): Double = when (state.doctrine) {
        MilitaryDoctrine.MASS_ARMY -> 0.82
        MilitaryDoctrine.ELITE_CORE -> 1.18
        else -> 1.0
    }

    fun marchFactor(state: GameState): Double =
        if (state.doctrine == MilitaryDoctrine.MOBILE_WARFARE) 1.12
        else if (state.doctrine == MilitaryDoctrine.DISCIPLINED_LINE) 0.96
        else 1.0

    fun equipmentPowerFactor(state: GameState): Double =
        if (state.doctrine == MilitaryDoctrine.ELITE_CORE) 1.08
        else if (state.doctrine == MilitaryDoctrine.MASS_ARMY) 0.96
        else 1.0

    fun rangedPowerFactor(state: GameState): Double =
        if (state.doctrine == MilitaryDoctrine.RANGED_SUPREMACY) 1.10 else 1.0

    fun infantryMoraleBonus(state: GameState): Int =
        if (state.doctrine == MilitaryDoctrine.DISCIPLINED_LINE) 5 else 0

    fun set(state: GameState, doctrine: MilitaryDoctrine): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Doktrin kann während einer Schlacht nicht geändert werden.")
        if (state.realm.level(BuildingType.ACADEMY) <= 0)
            return GameEngine.ActionResult(state, "Eine Offiziersschule wird für Armeedoktrinen benötigt.")
        if (state.doctrine == doctrine)
            return GameEngine.ActionResult(state, "Diese Doktrin ist bereits aktiv.")
        val cost = 150
        if (state.resources.gold < cost)
            return GameEngine.ActionResult(state, "Doktrinwechsel benötigt $cost Gold für Übungen und neue Befehle.")
        return GameEngine.ActionResult(
            state.copy(
                doctrine = doctrine,
                resources = state.resources.copy(gold = state.resources.gold - cost),
                chronicle =
                    (state.chronicle + ChronicleEntry(state.day, "Neue Armeedoktrin", doctrine.label))
                        .takeLast(2000),
            ),
            "${doctrine.label} ist nun die aktive Armeedoktrin.",
        )
    }
}

object ResearchEngine {
    fun requirementsMet(state: GameState, tech: ResearchTech): Boolean = when (tech) {
        ResearchTech.CROP_ROTATION -> state.realm.level(BuildingType.FARM) >= 2
        ResearchTech.FIELD_MEDICINE -> state.realm.level(BuildingType.HOSPITAL) >= 1
        ResearchTech.SUPPLY_TRAINS -> state.realm.level(BuildingType.STABLES) >= 1
        ResearchTech.FORGE_STANDARDIZATION -> state.realm.level(BuildingType.ARSENAL) >= 1
        ResearchTech.COMPOSITE_BOWS -> state.realm.level(BuildingType.ARSENAL) >= 1
        ResearchTech.SIEGE_ENGINEERING -> state.realm.level(BuildingType.ARSENAL) >= 2
        ResearchTech.OFFICER_CORPS -> state.realm.level(BuildingType.ACADEMY) >= 2
        ResearchTech.CIVIC_ADMINISTRATION -> state.realm.level(BuildingType.EMBASSY) >= 1
    }

    fun start(state: GameState, tech: ResearchTech): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Forschung kann nach der Schlacht begonnen werden.")
        if (state.realm.level(BuildingType.ACADEMY) <= 0)
            return GameEngine.ActionResult(state, "Eine Offiziersschule wird als Forschungszentrum benötigt.")
        if (tech in state.research.completed)
            return GameEngine.ActionResult(state, "${tech.label} ist bereits erforscht.")
        if (state.research.active != null)
            return GameEngine.ActionResult(state, "Es läuft bereits ein Forschungsprojekt.")
        if (!requirementsMet(state, tech))
            return GameEngine.ActionResult(state, "Die benötigten Gebäude für ${tech.label} fehlen.")
        if (state.resources.gold < tech.goldCost || state.resources.iron < tech.ironCost)
            return GameEngine.ActionResult(
                state,
                "Benötigt ${tech.goldCost} Gold und ${tech.ironCost} Eisen.",
            )
        val academy = WarEngine.effectiveLevel(state, BuildingType.ACADEMY).coerceAtLeast(1)
        val days = (tech.days - (academy - 1).coerceAtMost(4)).coerceAtLeast(4)
        return GameEngine.ActionResult(
            state.copy(
                resources =
                    state.resources.copy(
                        gold = state.resources.gold - tech.goldCost,
                        iron = state.resources.iron - tech.ironCost,
                    ),
                research = state.research.copy(active = ResearchProject(tech, days, days)),
            ),
            "${tech.label}: Forschung begonnen · $days Tage.",
        )
    }

    fun tick(state: GameState): GameState {
        val project = state.research.active ?: return state
        if (project.remainingDays > 1)
            return state.copy(
                research =
                    state.research.copy(
                        active = project.copy(remainingDays = project.remainingDays - 1)
                    )
            )
        val completed = state.research.completed + project.tech
        return state.copy(
            research = ResearchState(completed = completed, active = null),
            chronicle =
                (state.chronicle +
                        ChronicleEntry(
                            state.day,
                            "Forschung abgeschlossen",
                            "${project.tech.label}: ${project.tech.description}",
                        ))
                    .takeLast(2000),
        )
    }
}
