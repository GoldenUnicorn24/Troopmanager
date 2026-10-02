package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

object MissionEngine {
    data class MissionEstimate(val outcomeHint: String, val goldRange: IntRange, val xpRange: IntRange)
    fun estimate(state: GameState, type: MissionType, units: List<UnitAllocation>, commanderId: Long?): MissionEstimate {
        val selected = ArmyEngine.normalize(units)
        val commander = state.commanders.find { it.id == commanderId }
        val power = selected.sumOf { u ->
            val pool = state.armyPools.find { it.type == u.type }
            if (pool == null || pool.soldiers == 0) 0.0 else pool.power.toDouble() * u.amount / pool.soldiers
        }
        val score = power * (1 + (commander?.sword ?: state.player.sword)/180.0 +
            (commander?.leadership ?: state.player.leadership)/350.0 +
            (commander?.tactics ?: state.player.tactics)/350.0) *
            (0.7 + (commander?.loyalty ?: 100)/333.0) / (type.spec().difficulty * 20.0)
        val hint = when { score >= 1.6 -> "Sehr gute Aussichten"; score >= 1.0 -> "Gute Aussichten"; score >= 0.7 -> "Unsicherer Ausgang"; else -> "Hohes Verlustrisiko" }
        return MissionEstimate("$hint · Schätzung, Zufall und Einheitenspezialisierung beeinflussen den Ausgang", 0..type.spec().difficulty*6, 10..maxOf(10,type.spec().difficulty*3/4))
    }

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
        val worldState = WorldEngine.initialize(state)
        val target = regionId ?: "keep"
        val path = WorldEngine.route(worldState.world, "keep", target)
        if (!WorldEngine.routeAllowed(worldState, PLAYER_FACTION, path))
            return GameEngine.ActionResult(state, "Kein zugänglicher Weg: Militärzugang oder Kriegserklärung nötig.")
        val previewArmy = WorldArmy("preview", PLAYER_FACTION, type.label, selected, "keep")
        val travel = WorldEngine.travelDays(worldState, previewArmy, path)
        val operationDays = duration(state, type, commanderId)
        val days = operationDays + travel * 2
        val supply = maxOf(total.toLong() * days * 2, previewArmy.dailyFood.toLong() * days)

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
                phase = if (travel > 0) MissionPhase.OUTBOUND else MissionPhase.OPERATING,
                operationDaysRemaining = operationDays,
                originalTotal = total,
                lastTickDay = state.day,
                quality =
                    selected.map { u ->
                        state.armyPools.first { it.type == u.type }.copy(soldiers = u.amount)
                    },
            )
        val launched = worldState.copy(
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
                        .takeLast(2000),
            )
        return GameEngine.ActionResult(
            WorldEngine.attachMission(launched, mission),
            "Mission gestartet. Rückkehr voraussichtlich an Tag ${state.day + days}; Wetter und Routenwahl beeinflussen die Reise.",
        )
    }

    private fun nextId(state: GameState): Long =
        (state.activeMissions.maxOfOrNull { it.id } ?: 0) + 1

    fun recall(state: GameState, id: Long): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Rückruf nach der Schlacht möglich.")
        val mission =
            state.activeMissions.find { it.id == id && it.status == MissionStatus.ACTIVE }
                ?: return GameEngine.ActionResult(state, "Mission kann nicht zurückgerufen werden.")
        // A recall still takes a travel day and never grants completion rewards.
        val army = state.world.armies.firstOrNull { it.missionId == id }
        val path = army?.let { WorldEngine.route(state.world, it.regionId, "keep") } ?: emptyList()
        val days = army?.let { WorldEngine.travelDays(state, it, path) }?.coerceAtLeast(1) ?: 1
        val returning = mission.copy(status = MissionStatus.RETURNING, phase = MissionPhase.RETURNING, remainingDays = days, pendingDecision = null)
        val next = WorldEngine.returnMission(state.copy(activeMissions = state.activeMissions.map { if (it.id == id) returning else it }), id)
        return GameEngine.ActionResult(next, "Rückmarsch etwa $days Tage. Unvollendete Missionen gewähren keine Beute.")
    }

    fun chooseRoute(state: GameState, id: Long, choice: Int): GameEngine.ActionResult {
        val mission = state.activeMissions.firstOrNull { it.id == id && it.status == MissionStatus.ACTIVE }
            ?: return GameEngine.ActionResult(state, "Mission ist nicht verfügbar.")
        val decision = mission.pendingDecision ?: return GameEngine.ActionResult(state, "Keine Routenentscheidung offen.")
        if (choice !in decision.options.indices || state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Diese Entscheidung ist gerade nicht möglich.")
        val army = state.world.armies.firstOrNull { it.missionId == id }
        if (choice == 2 && state.resources.gold < 100) return GameEngine.ActionResult(state, "Lokale Vorräte kosten 100 Gold.")
        val localOwner = army?.let { state.world.place(it.regionId)?.ownerId } ?: NEUTRAL_FACTION
        val localFaction = state.world.faction(localOwner)
        val purchased = army?.dailyFood?.toLong()?.times(2)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        if (choice == 2 && (localFaction?.food ?: 0) < purchased)
            return GameEngine.ActionResult(state, "Die örtlichen Vorräte reichen nicht für das Heer.")
        val policy = when (choice) { 0 -> MarchPolicy.SAFE; 1 -> MarchPolicy.FORCED; else -> MarchPolicy.NORMAL }
        val updated = mission.copy(pendingDecision = null, routeDecisionMade = true,
            riskFactor = when (choice) { 0 -> .8; 1 -> 1.25; else -> .9 })
        var next = state.copy(activeMissions = state.activeMissions.map { if (it.id == id) updated else it },
            resources = if (choice == 2) state.resources.copy(gold = state.resources.gold - 100) else state.resources)
        if (army != null) next = next.copy(world = next.world.copy(armies = next.world.armies.map { if (it.id == army.id) it.copy(marchPolicy = policy,
            supplyFood = if (choice == 2) (it.supplyFood.toLong() + purchased).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else it.supplyFood) else it },
            factions = if (choice == 2) next.world.factions.map { if (it.id == localOwner) it.copy(food = it.food - purchased, gold = (it.gold.toLong() + 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else it } else next.world.factions))
        return GameEngine.ActionResult(next, "${decision.options[choice]}: Marsch und Missionsrisiko wurden angepasst.")
    }

    fun tick(state: GameState, randomFactor: Double? = null): GameState {
        var next = state
        // Direct callers from earlier integrations still move the same world armies once per day.
        if (next.world.initialized && next.world.lastTickDay < next.day) next = WorldEngine.tick(next)
        next.activeMissions.filter { it.status.isAway }.toList().forEach { original ->
            val previous = next.activeMissions.first { it.id == original.id }
            if (previous.lastTickDay >= next.day) return@forEach
            val mission = previous.copy(lastTickDay = next.day)
            next = update(next, mission)
            val army = next.world.armies.firstOrNull { it.missionId == mission.id }
            if (mission.pendingDecision != null) return@forEach
            if (mission.phase == MissionPhase.OUTBOUND && army != null) {
                if (!mission.routeDecisionMade && next.day > mission.startDay && army.route.size > 1) {
                    next = update(next, mission.copy(pendingDecision = MissionDecision(
                        "Ein schwieriger Reiseabschnitt", "Die Vorhut meldet einen gefährlichen schnellen Weg. Eine vorsichtige Route schont das Heer; lokale Händler können Vorräte verkaufen.",
                        listOf("Sicheren Weg wählen", "Gewaltmarsch wagen", "Lokale Vorräte kaufen (100 Gold)"))))
                    return@forEach
                }
                if (army.status != WorldArmyStatus.MISSION) {
                    next = update(next, mission.copy(remainingDays = ((army.arrivalDay ?: next.day + 1) - next.day).coerceAtLeast(1) + mission.operationDaysRemaining))
                    return@forEach
                }
                next = update(next, mission.copy(phase = MissionPhase.OPERATING, remainingDays = mission.operationDaysRemaining.coerceAtLeast(1)))
                return@forEach
            }
            if (mission.phase == MissionPhase.RETURNING && army != null && army.status != WorldArmyStatus.HOME && army.status != WorldArmyStatus.DESTROYED) {
                next = update(next, mission.copy(remainingDays = ((army.arrivalDay ?: next.day + 1) - next.day).coerceAtLeast(1)))
                return@forEach
            }
            if (mission.phase == MissionPhase.RETURNING && mission.outcome != null) {
                val done = mission.copy(status = if (mission.outcome <= MissionOutcome.PARTIAL) MissionStatus.COMPLETE else MissionStatus.FAILED, remainingDays = 0, phase = MissionPhase.DONE)
                next = update(next, done)
                next = restoreCommand(next, done, emptyList())
                if (army != null) next = next.copy(resources = EconomyEngine.add(next.resources, Resources(0, army.supplyFood, 0, 0, 0)),
                    world = next.world.copy(armies = next.world.armies.map { if (it.id == army.id) it.copy(supplyFood = 0, status = WorldArmyStatus.HOME) else it }))
                return@forEach
            }
            if (mission.remainingDays > 1 && mission.phase != MissionPhase.RETURNING) next = update(next, mission.copy(remainingDays = mission.remainingDays - 1, operationDaysRemaining = (mission.operationDaysRemaining - 1).coerceAtLeast(0)))
            else next = finish(next, mission, randomFactor ?: Random((mission.id xor state.day.toLong()).toInt()).nextDouble(.75, 1.26))
        }
        return next
    }

    private fun update(state: GameState, mission: ActiveMission): GameState = state.copy(activeMissions = state.activeMissions.map { if (it.id == mission.id) mission else it })

    private fun finish(state: GameState, mission: ActiveMission, roll: Double): GameState {
        if (mission.status == MissionStatus.RETURNING) {
            var next =
                state.copy(
                    activeMissions =
                        state.activeMissions.map {
                            if (it.id == mission.id)
                                it.copy(status = MissionStatus.FAILED, remainingDays = 0, phase = MissionPhase.DONE)
                            else it
                        }
                )
            next = WorldEngine.completeMission(next, mission, emptyList())
            next = restoreCommand(next, mission, emptyList())
            return next.copy(
                chronicle =
                    (next.chronicle +
                            ChronicleEntry(
                                next.day,
                                "Truppen zurückgerufen",
                                "${mission.total} Soldaten von ${mission.missionType.label} sind wieder verfügbar.",
                            ))
                        .takeLast(2000)
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
                roll.coerceIn(0.1, 2.0) / (mission.missionType.spec().difficulty * 20.0 * mission.riskFactor)
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
        val worldArmy = state.world.armies.firstOrNull { it.missionId == mission.id }
        val needsReturn = mission.regionId != null && worldArmy != null && worldArmy.regionId != "keep"
        val survivors = mission.units.mapNotNull { u -> val n = u.amount - (losses.firstOrNull { it.type == u.type }?.amount ?: 0); if (n > 0) u.copy(amount = n) else null }
        val completed =
            mission.copy(
                status = if (needsReturn) MissionStatus.RETURNING else if (success) MissionStatus.COMPLETE else MissionStatus.FAILED,
                phase = if (needsReturn) MissionPhase.RETURNING else MissionPhase.DONE,
                units = if (needsReturn) survivors else mission.units,
                quality = if (needsReturn) mission.quality.mapNotNull { p -> survivors.firstOrNull { it.type == p.type }?.let { p.copy(soldiers = it.amount) } } else mission.quality,
                remainingDays = if (needsReturn) WorldEngine.travelDays(state, worldArmy!!, WorldEngine.route(state.world, worldArmy.regionId, "keep")).coerceAtLeast(1) else 0,
                outcome = outcome,
                losses = mission.losses + lost,
                reward = reward,
                renownReward = fame,
                xpReward = maxOf(10, base / 4),
            )
        var next =
            state.copy(
                activeMissions =
                    state.activeMissions.map { if (it.id == mission.id) completed else it }
            )
        next = next.copy(commanders = next.commanders.map { c ->
            if (c.id == mission.commanderId) c.copy(missionsCompleted = c.missionsCompleted + 1,
                victories = c.victories + if (success) 1 else 0, casualties = c.casualties + lost) else c
        })
        if (worldArmy != null) next = next.copy(world = next.world.copy(armies = next.world.armies.map { if (it.id == worldArmy.id) it.copy(units = survivors) else it }))
        next = ArmyEngine.applyLosses(next, losses)
        if (needsReturn) next = WorldEngine.returnMission(next, mission.id)
        else {
            next = WorldEngine.completeMission(next, mission, emptyList())
            next = restoreCommand(next, mission, losses)
        }
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
                                "${mission.total - lost} überleben, $lost Verluste. ${if (needsReturn) "Der Rückmarsch beginnt." else "Das Heer ist wieder verfügbar."} +${reward.gold} Gold, +$fame Ruhm.",
                            ))
                        .takeLast(2000),
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
        if (mission.commanderId != null) next = CharacterEngine.recordMission(next, mission.commanderId, success, mission.missionType.label)
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
