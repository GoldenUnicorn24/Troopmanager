package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

object MissionEngine {
    data class MissionEstimate(val outcomeHint: String, val goldRange: IntRange, val xpRange: IntRange)

    private data class LeaderStats(
        val sword: Int,
        val bow: Int,
        val riding: Int,
        val leadership: Int,
        val tactics: Int,
        val loyalty: Int,
    )

    private fun participantStats(
        state: GameState,
        commanderIds: List<Long>,
        playerParticipates: Boolean,
    ): List<LeaderStats> = buildList {
        if (playerParticipates) {
            add(
                LeaderStats(
                    state.player.sword,
                    state.player.bow,
                    state.player.riding,
                    state.player.leadership,
                    state.player.tactics,
                    100,
                )
            )
        }
        commanderIds.distinct().forEach { id ->
            state.commanders.firstOrNull { it.id == id }?.let { commander ->
                add(
                    LeaderStats(
                        commander.sword,
                        commander.bow,
                        if (id == COMPANION_COMMANDER_ID) state.companion.riding
                        else (commander.leadership + commander.tactics) / 2,
                        commander.leadership,
                        commander.tactics,
                        commander.loyalty,
                    )
                )
            }
        }
    }

    private fun average(values: List<Int>, fallback: Int): Int =
        if (values.isEmpty()) fallback else values.sum() / values.size

    fun estimate(
        state: GameState,
        type: MissionType,
        units: List<UnitAllocation>,
        commanderId: Long?,
    ): MissionEstimate =
        estimate(
            state,
            type,
            units,
            listOfNotNull(commanderId),
            playerParticipates = commanderId == null,
        )

    fun estimate(
        state: GameState,
        type: MissionType,
        units: List<UnitAllocation>,
        commanderIds: List<Long>,
        playerParticipates: Boolean,
    ): MissionEstimate {
        val selected = ArmyEngine.normalize(units)
        val leaders =
            participantStats(state, commanderIds, playerParticipates).ifEmpty {
                listOf(
                    LeaderStats(
                        state.player.sword,
                        state.player.bow,
                        state.player.riding,
                        state.player.leadership,
                        state.player.tactics,
                        100,
                    )
                )
            }
        val power =
            selected.sumOf { u ->
                val pool = state.armyPools.find { it.type == u.type }
                if (pool == null || pool.soldiers == 0) 0.0
                else pool.power.toDouble() * u.amount / pool.soldiers
            }
        val sword = average(leaders.map { it.sword }, state.player.sword)
        val leadership = average(leaders.map { it.leadership }, state.player.leadership)
        val tactics = average(leaders.map { it.tactics }, state.player.tactics)
        val loyalty = average(leaders.map { it.loyalty }, 100)
        val coordination = 1.0 + (leaders.size - 1).coerceAtLeast(0) * 0.06
        val score =
            power *
                (1 + sword / 180.0 + leadership / 350.0 + tactics / 350.0) *
                (0.7 + loyalty / 333.0) *
                coordination / (type.spec().difficulty * 20.0)
        val hint =
            when {
                score >= 1.6 -> "Sehr gute Aussichten"
                score >= 1.0 -> "Gute Aussichten"
                score >= 0.7 -> "Unsicherer Ausgang"
                else -> "Hohes Verlustrisiko"
            }
        return MissionEstimate(
            "${hint} · ${leaders.size} Führungsperson${if (leaders.size == 1) "" else "en"} · Schätzung, Zufall und Einheitenspezialisierung beeinflussen den Ausgang",
            0..type.spec().difficulty * 6,
            10..maxOf(10, type.spec().difficulty * 3 / 4),
        )
    }

    fun duration(state: GameState, type: MissionType, commanderId: Long?): Int =
        duration(
            state,
            type,
            listOfNotNull(commanderId),
            playerParticipates = commanderId == null,
        )

    fun duration(
        state: GameState,
        type: MissionType,
        commanderIds: List<Long>,
        playerParticipates: Boolean,
    ): Int {
        val riding =
            participantStats(state, commanderIds, playerParticipates)
                .maxOfOrNull { it.riding } ?: state.player.riding
        return (type.spec().days -
                if (riding >= 75 && type in listOf(MissionType.ESCORT, MissionType.SCOUT)) 1 else 0)
            .coerceAtLeast(1)
    }

    fun available(state: GameState, commanderId: Long?, type: UnitType): Int =
        available(state, listOfNotNull(commanderId), type)

    fun available(state: GameState, commanderIds: List<Long>, type: UnitType): Int {
        val leaders = commanderIds.distinct()
        if (
            state.battleSession?.isActive == true ||
                leaders.any { state.commanderAway(it) }
        ) return 0
        return (
            state.directCommand(type).toLong() +
                leaders.sumOf { state.assignedTo(it, type).toLong() }
            ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun start(
        state: GameState,
        type: MissionType,
        commanderId: Long?,
        units: List<UnitAllocation>,
        regionId: String? = null,
    ): GameEngine.ActionResult =
        start(
            state,
            type,
            listOfNotNull(commanderId),
            playerParticipates = commanderId == null,
            units = units,
            regionId = regionId,
        )

    fun start(
        state: GameState,
        type: MissionType,
        commanderIds: List<Long>,
        playerParticipates: Boolean,
        units: List<UnitAllocation>,
        regionId: String? = null,
    ): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Die Schlacht muss zuerst entschieden werden.")

        val leaders = commanderIds.distinct()
        val leaderCount = leaders.size + if (playerParticipates) 1 else 0
        if (leaderCount !in 1..3)
            return GameEngine.ActionResult(state, "Wähle eine bis drei Führungspersonen für die Mission.")
        if (playerParticipates && state.playerAwayOnMission)
            return GameEngine.ActionResult(state, "Du bist bereits persönlich auf einer Mission unterwegs.")
        val unavailable =
            leaders.firstOrNull { id ->
                state.commanders.none { it.id == id } ||
                    state.commanderAway(id) ||
                    state.war.unavailableCommander(id)
            }
        if (unavailable != null)
            return GameEngine.ActionResult(state, "Mindestens eine ausgewählte Führungsperson ist nicht verfügbar.")

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
            val available = available(state, leaders, it.type)
            if (it.amount > available)
                return GameEngine.ActionResult(
                    state,
                    "${it.type.label}: nur $available verfügbar.",
                )
        }

        if (regionId != null && state.regions.none { it.id == regionId && it.mission == type })
            return GameEngine.ActionResult(state, "Diese Region bietet die Mission nicht an.")
        val worldState = WorldEngine.initialize(state)
        val target = regionId ?: "keep"
        val path = WorldEngine.route(worldState.world, "keep", target)
        if (!WorldEngine.routeAllowed(worldState, PLAYER_FACTION, path))
            return GameEngine.ActionResult(
                state,
                "Kein zugänglicher Weg: Militärzugang oder Kriegserklärung nötig.",
            )
        val previewArmy = WorldArmy("preview", PLAYER_FACTION, type.label, selected, "keep")
        val travel = WorldEngine.travelDays(worldState, previewArmy, path)
        val operationDays = duration(state, type, leaders, playerParticipates)
        val days = operationDays + travel * 2
        val supply = maxOf(total.toLong() * days * 2, previewArmy.dailyFood.toLong() * days)

        if (supply > state.resources.food)
            return GameEngine.ActionResult(state, "Versorgung benötigt $supply Nahrung.")

        val mission =
            ActiveMission(
                id = nextId(state),
                missionType = type,
                commanderId = leaders.firstOrNull(),
                commanderIds = leaders,
                playerParticipates = playerParticipates,
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

        val leaderNames = buildList {
            if (playerParticipates) add(state.player.name)
            leaders.mapNotNullTo(this) { id -> state.commanders.firstOrNull { it.id == id }?.name }
        }.joinToString(", ")

        val launched =
            worldState.copy(
                resources = state.resources.copy(food = state.resources.food - supply.toInt()),
                // Permanent allocations from the Army page remain untouched. Mission allocations
                // exist only inside ActiveMission and vanish automatically on return.
                activeMissions =
                    (state.activeMissions + mission)
                        .filter { it.status.isAway }
                        .plus((state.activeMissions.filterNot { it.status.isAway }).takeLast(30)),
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Mission gestartet",
                                "${type.label}: $total Soldaten und $leaderNames sind $days Tage unterwegs; $supply Nahrung eingelagert.",
                            ))
                        .takeLast(2000),
            )
        return GameEngine.ActionResult(
            WorldEngine.attachMission(launched, mission),
            "Mission gestartet mit $leaderCount Führungsperson${if (leaderCount == 1) "" else "en"}. Rückkehr voraussichtlich an Tag ${state.day + days}.",
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
        val leaders =
            participantStats(state, mission.allCommanderIds, mission.playerParticipates).ifEmpty {
                // Legacy saves with no explicit commander represented the player's personal command.
                listOf(
                    LeaderStats(
                        state.player.sword,
                        state.player.bow,
                        state.player.riding,
                        state.player.leadership,
                        state.player.tactics,
                        100,
                    )
                )
            }
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
            average(
                leaders.map { leader ->
                    when (mission.missionType) {
                        MissionType.HUNT, MissionType.SCOUT -> leader.bow
                        MissionType.ESCORT -> leader.riding
                        MissionType.RELIEF -> leader.leadership
                        else -> leader.sword
                    }
                },
                state.player.sword,
            )
        val leadership = average(leaders.map { it.leadership }, state.player.leadership)
        val tactics = average(leaders.map { it.tactics }, state.player.tactics)
        val loyalty = average(leaders.map { it.loyalty }, 100)
        val coordination = 1.0 + (leaders.size - 1).coerceAtLeast(0) * 0.06
        val score =
            power *
                (1 + skill / 180.0 + leadership / 350.0 + tactics / 350.0) *
                (0.7 + loyalty / 333.0) *
                coordination *
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
        val participantCommanderIds = mission.allCommanderIds.toSet()
        next = next.copy(commanders = next.commanders.map { commander ->
            if (commander.id in participantCommanderIds)
                commander.copy(
                    missionsCompleted = commander.missionsCompleted + 1,
                    victories = commander.victories + if (success) 1 else 0,
                    casualties = commander.casualties + lost,
                )
            else commander
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
        if (COMPANION_COMMANDER_ID in participantCommanderIds)
            next =
                next.copy(
                    companion =
                        next.companion.copy(
                            trust = (next.companion.trust + if (success) 3 else -2).coerceIn(0, 100)
                        )
                )
        mission.allCommanderIds.forEach { id ->
            next = CharacterEngine.recordMission(next, id, success, mission.missionType.label)
        }
        next = RelationshipEngine.onEvent(next, if (success) "mission" else "wounded")
        return ProgressionEngine.update(ProgressionEngine.awardXp(next, maxOf(10, base / 4)))
    }

    /**
     * Mission staffing is temporary in v0.61. Permanent assignments are owned exclusively by the
     * Army page, so returning from a mission must never create or replace a CommanderAssignment.
     */
    private fun restoreCommand(
        state: GameState,
        mission: ActiveMission,
        losses: List<UnitAllocation>,
    ): GameState = ArmyEngine.clampAssignments(state)
}
