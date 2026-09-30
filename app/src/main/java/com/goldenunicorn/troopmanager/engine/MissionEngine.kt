package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

object MissionEngine {
    fun duration(state: GameState, type: MissionType, commanderId: Long?): Int {
        val riding =
            if (commanderId == COMPANION_COMMANDER_ID) state.companion.riding
            else state.player.riding
        return (type.spec().days -
                if (riding >= 75 && type in listOf(MissionType.ESCORT, MissionType.SCOUT)) 1 else 0)
            .coerceAtLeast(1)
    }

    fun available(state: GameState, commanderId: Long?, type: UnitType): Int {
        if (
            state.battleSession?.isActive == true ||
                (commanderId != null && state.commanderAway(commanderId))
        )
            return 0
        return state.directCommand(type) + (commanderId?.let { state.assignedTo(it, type) } ?: 0)
    }

    fun start(
        state: GameState,
        type: MissionType,
        commanderId: Long?,
        units: List<UnitAllocation>,
        regionId: String? = null,
    ): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Die Schlacht muss zuerst entschieden werden.")
        if (
            commanderId != null &&
                (state.commanders.none { it.id == commanderId } || state.commanderAway(commanderId))
        )
            return GameEngine.ActionResult(state, "Kommandant ist nicht verfügbar.")
        if (units.any { it.amount < 0 } || units.sumOf { it.amount.toLong() } > Int.MAX_VALUE)
            return GameEngine.ActionResult(state, "Ungültige Truppenauswahl.")
        val selected = ArmyEngine.normalize(units)
        val total = selected.sumOf { it.amount }
        if (total < type.spec().minimum)
            return GameEngine.ActionResult(
                state,
                "Mindestens ${type.spec().minimum} Soldaten benötigt.",
            )
        selected.forEach {
            if (it.amount > available(state, commanderId, it.type))
                return GameEngine.ActionResult(
                    state,
                    "${it.type.label}: nur ${available(state, commanderId, it.type)} verfügbar.",
                )
        }
        if (regionId != null && state.regions.none { it.id == regionId && it.mission == type })
            return GameEngine.ActionResult(state, "Diese Region bietet die Mission nicht an.")
        val days = duration(state, type, commanderId)
        val supply = total.toLong() * days * 2
        if (supply > state.resources.food)
            return GameEngine.ActionResult(state, "Versorgung benötigt $supply Nahrung.")
        val mission =
            ActiveMission(
                id = nextId(state),
                missionType = type,
                commanderId = commanderId,
                units = selected,
                startDay = state.day,
                remainingDays = days,
                supplyCost = supply.toInt(),
                duration = days,
                regionId = regionId,
                quality =
                    selected.map { u ->
                        state.armyPools.first { it.type == u.type }.copy(soldiers = u.amount)
                    },
            )
        return GameEngine.ActionResult(
            state.copy(
                resources = state.resources.copy(food = state.resources.food - supply.toInt()),
                commanderAssignments =
                    state.commanderAssignments.filterNot { it.commanderId == commanderId },
                activeMissions =
                    (state.activeMissions + mission)
                        .filter { it.status.isAway }
                        .plus((state.activeMissions.filterNot { it.status.isAway }).takeLast(30)),
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Mission gestartet",
                                "${type.label}: $total Soldaten sind $days Tage unterwegs; $supply Nahrung eingelagert.",
                            ))
                        .takeLast(80),
            ),
            "Mission gestartet. Rückkehr frühestens an Tag ${state.day + days}.",
        )
    }

    private fun nextId(state: GameState): Long =
        maxOf(System.nanoTime(), (state.activeMissions.maxOfOrNull { it.id } ?: 0) + 1)

    fun recall(state: GameState, id: Long): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Rückruf nach der Schlacht möglich.")
        val mission =
            state.activeMissions.find { it.id == id && it.status == MissionStatus.ACTIVE }
                ?: return GameEngine.ActionResult(state, "Mission kann nicht zurückgerufen werden.")
        // A recall still takes a travel day and never grants completion rewards.
        val returning = mission.copy(status = MissionStatus.RETURNING, remainingDays = 1)
        return GameEngine.ActionResult(
            state.copy(
                activeMissions = state.activeMissions.map { if (it.id == id) returning else it }
            ),
            "Truppen kehren am nächsten Tag zurück. Keine Missionsbeute.",
        )
    }

    fun tick(state: GameState, randomFactor: Double? = null): GameState {
        var next = state
        state.activeMissions
            .filter { it.status.isAway }
            .forEach { mission ->
                if (mission.remainingDays > 1) {
                    next =
                        next.copy(
                            activeMissions =
                                next.activeMissions.map {
                                    if (it.id == mission.id)
                                        it.copy(remainingDays = it.remainingDays - 1)
                                    else it
                                }
                        )
                } else
                    next =
                        finish(
                            next,
                            mission,
                            randomFactor
                                ?: Random((mission.id xor state.day.toLong()).toInt())
                                    .nextDouble(0.75, 1.26),
                        )
            }
        return next
    }

    private fun finish(state: GameState, mission: ActiveMission, roll: Double): GameState {
        if (mission.status == MissionStatus.RETURNING) {
            var next =
                state.copy(
                    activeMissions =
                        state.activeMissions.map {
                            if (it.id == mission.id)
                                it.copy(status = MissionStatus.FAILED, remainingDays = 0)
                            else it
                        }
                )
            next = restoreCommand(next, mission, emptyList())
            return next.copy(
                chronicle =
                    (next.chronicle +
                            ChronicleEntry(
                                next.day,
                                "Truppen zurückgerufen",
                                "${mission.total} Soldaten von ${mission.missionType.label} sind wieder verfügbar.",
                            ))
                        .takeLast(80)
            )
        }
        val commander = state.commanders.find { it.id == mission.commanderId }
        var power = 0.0
        mission.units.forEach { u ->
            val p =
                mission.quality.firstOrNull { it.type == u.type }
                    ?: state.armyPools.first { it.type == u.type }
            val specialization =
                when (mission.missionType) {
                    MissionType.SCOUT -> if (u.type.culture == Culture.WOOD_ELF) 1.5 else 1.0
                    MissionType.HUNT ->
                        if (
                            u.type.culture == Culture.GOLD_ELF ||
                                u.type.culture == Culture.WALL ||
                                u.type.ranged >= 8
                        )
                            1.3
                        else 1.0
                    MissionType.ESCORT -> if (u.type == UnitType.KNIGHT) 1.5 else 1.0
                    MissionType.RELIEF -> if (u.type.culture == Culture.WALL) 1.4 else 1.0
                    MissionType.BANDITS -> if (u.type.culture == Culture.GOLD_ELF) 1.3 else 1.0
                    MissionType.PATROL -> 1.0
                }
            power += p.power.toDouble() * u.amount / p.soldiers * specialization
        }
        val skill =
            when (mission.missionType) {
                MissionType.HUNT,
                MissionType.SCOUT -> commander?.bow ?: state.player.bow
                MissionType.ESCORT ->
                    if (mission.commanderId == COMPANION_COMMANDER_ID) state.companion.riding
                    else state.player.riding
                MissionType.RELIEF -> commander?.leadership ?: state.player.leadership
                else -> commander?.sword ?: state.player.sword
            }
        val leadership = commander?.leadership ?: state.player.leadership
        val tactics = commander?.tactics ?: state.player.tactics
        val loyalty = commander?.loyalty ?: 100
        val score =
            power *
                (1 + skill / 180.0 + leadership / 350.0 + tactics / 350.0) *
                (0.7 + loyalty / 333.0) *
                roll.coerceIn(0.1, 2.0) / (mission.missionType.spec().difficulty * 20.0)
        val outcome =
            when {
                score >= 1.6 -> MissionOutcome.GREAT_SUCCESS
                score >= 1.0 -> MissionOutcome.SUCCESS
                score >= 0.7 -> MissionOutcome.PARTIAL
                score >= 0.4 -> MissionOutcome.FAILED
                else -> MissionOutcome.CATASTROPHIC
            }
        val lossRate =
            when (outcome) {
                MissionOutcome.GREAT_SUCCESS -> 0.01
                MissionOutcome.SUCCESS -> 0.04
                MissionOutcome.PARTIAL -> 0.10
                MissionOutcome.FAILED -> 0.20
                MissionOutcome.CATASTROPHIC -> 0.40
            } * if (mission.missionType.spec().risk == "Niedrig") 0.5 else 1.0
        val losses =
            mission.units.map {
                UnitAllocation(it.type, (it.amount * lossRate).toInt().coerceIn(0, it.amount))
            }
        val lost = losses.sumOf { it.amount }
        val success = outcome <= MissionOutcome.PARTIAL
        val rewardScale =
            when (outcome) {
                MissionOutcome.GREAT_SUCCESS -> 3
                MissionOutcome.SUCCESS -> 2
                MissionOutcome.PARTIAL -> 1
                else -> 0
            }
        val base = mission.missionType.spec().difficulty * rewardScale
        val reward =
            Resources(
                gold = base * 2,
                food = if (mission.missionType == MissionType.RELIEF) 0 else base / 2,
                wood = if (mission.missionType == MissionType.ESCORT) base / 3 else 0,
                stone = if (mission.missionType == MissionType.BANDITS) base / 4 else 0,
                iron =
                    if (mission.missionType in listOf(MissionType.HUNT, MissionType.BANDITS))
                        base / 3
                    else 0,
            )
        val fame = base / (if (mission.missionType == MissionType.HUNT) 5 else 10)
        val completed =
            mission.copy(
                remainingDays = 0,
                status = if (success) MissionStatus.COMPLETE else MissionStatus.FAILED,
                outcome = outcome,
                losses = lost,
                reward = reward,
                renownReward = fame,
            )
        var next =
            state.copy(
                activeMissions =
                    state.activeMissions.map { if (it.id == mission.id) completed else it }
            )
        next = ArmyEngine.applyLosses(next, losses)
        next = restoreCommand(next, mission, losses)
        next =
            next.copy(
                resources = EconomyEngine.add(next.resources, reward),
                renown = next.renown + fame,
                armyPools =
                    next.armyPools.map { p ->
                        val returned =
                            ((mission.units.find { it.type == p.type }?.amount ?: 0) -
                                    (losses.find { it.type == p.type }?.amount ?: 0))
                                .coerceAtLeast(0)
                        fun weighted(delta: Int) =
                            (delta.toLong() * returned / p.soldiers.coerceAtLeast(1)).toInt()
                        if (returned == 0) p
                        else
                            p.copy(
                                experience =
                                    (p.experience + weighted(4 * rewardScale + 1)).coerceIn(0, 100),
                                morale =
                                    (p.morale + weighted(if (success) 3 else -8)).coerceIn(0, 100),
                                equipment =
                                    (p.equipment + weighted(if (success) -1 else -4)).coerceIn(
                                        0,
                                        100,
                                    ),
                            )
                    },
                realm =
                    next.realm.copy(
                        tradeBonusDays =
                            if (success && mission.missionType == MissionType.ESCORT) 7
                            else next.realm.tradeBonusDays,
                        scoutingDays =
                            if (success && mission.missionType == MissionType.SCOUT) 7
                            else next.realm.scoutingDays,
                        threat =
                            (next.realm.threat -
                                    if (success && mission.missionType == MissionType.PATROL) 3
                                    else 0)
                                .coerceAtLeast(0),
                    ),
                chronicle =
                    (next.chronicle +
                            ChronicleEntry(
                                next.day,
                                "${mission.missionType.label}: ${outcome.label}",
                                "${mission.total - lost} kehren zurück, $lost Verluste. +${reward.gold} Gold, +$fame Ruhm.",
                            ))
                        .takeLast(80),
            )
        if (success && mission.missionType == MissionType.RELIEF) {
            val culture = mission.units.maxBy { it.amount }.type.culture
            val oldPopulation = ArmyEngine.population(next.population, culture)
            val grown = ArmyEngine.adjustPopulation(next.population, culture, 60 * rewardScale)
            next =
                next.copy(
                    population =
                        ArmyEngine.adjustRecruits(
                            grown,
                            culture,
                            minOf(
                                10 * rewardScale,
                                ArmyEngine.population(grown, culture) - oldPopulation,
                            ),
                        )
                )
        }
        if (mission.commanderId == COMPANION_COMMANDER_ID)
            next =
                next.copy(
                    companion =
                        next.companion.copy(
                            trust = (next.companion.trust + if (success) 3 else -2).coerceIn(0, 100)
                        )
                )
        next = RelationshipEngine.onEvent(next, if (success) "mission" else "wounded")
        return ProgressionEngine.update(ProgressionEngine.awardXp(next, maxOf(10, base / 4)))
    }

    private fun restoreCommand(
        state: GameState,
        mission: ActiveMission,
        losses: List<UnitAllocation>,
    ): GameState {
        val id = mission.commanderId ?: return state
        val units =
            mission.units
                .map { u ->
                    UnitAllocation(
                        u.type,
                        u.amount - (losses.find { it.type == u.type }?.amount ?: 0),
                    )
                }
                .filter { it.amount > 0 }
        return ArmyEngine.clampAssignments(
            state.copy(
                commanderAssignments =
                    state.commanderAssignments.filterNot { it.commanderId == id } +
                        CommanderAssignment(id, units)
            )
        )
    }
}
