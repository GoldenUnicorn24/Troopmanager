package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

/** Advances only one exchange at a time. All resume state is serializable in BattleSession. */
object BattleEngine {
    private val combatSections =
        listOf(BattleSection.LEFT, BattleSection.CENTER, BattleSection.RIGHT)

    fun defaultDeployments(state: GameState): List<BattleDeployment> {
        val result = mutableListOf<BattleDeployment>()
        state.commanderAssignments
            .filter { !state.commanderAway(it.commanderId) }
            .forEachIndexed { index, assignment ->
                if (assignment.total > 0)
                    result +=
                        BattleDeployment(
                            assignment.commanderId,
                            combatSections[index % 3],
                            assignment.units,
                        )
            }
        val direct = BattleSection.entries.associateWith { mutableListOf<UnitAllocation>() }
        UnitType.entries.forEach { type ->
            val amount = state.directCommand(type)
            if (amount > 0) {
                if (type == UnitType.KNIGHT)
                    direct.getValue(BattleSection.RESERVE) += UnitAllocation(type, amount)
                else {
                    val left = amount / 3
                    val right = amount / 3
                    if (left > 0) direct.getValue(BattleSection.LEFT) += UnitAllocation(type, left)
                    if (right > 0)
                        direct.getValue(BattleSection.RIGHT) += UnitAllocation(type, right)
                    direct.getValue(BattleSection.CENTER) +=
                        UnitAllocation(type, amount - left - right)
                }
            }
        }
        direct.forEach { (section, units) ->
            if (units.isNotEmpty()) result += BattleDeployment(null, section, units)
        }
        if (result.none { it.section != BattleSection.RESERVE && it.units.isNotEmpty() })
            return result.map { it.copy(section = BattleSection.CENTER) }
        return result
    }

    fun start(
        state: GameState,
        enemy: EnemyType,
        tactic: Tactic,
        deployments: List<BattleDeployment> = emptyList(),
        seed: Int = Random.nextInt(),
        enemyStrength: Int? = null,
    ): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(
                state,
                "Die laufende Schlacht muss zuerst beendet werden.",
            )
        if (enemyStrength != null && enemyStrength <= 0)
            return GameEngine.ActionResult(state, "Die Gegnerstärke muss positiv sein.")
        val chosen = deployments.ifEmpty { defaultDeployments(state) }
        val commanders = chosen.mapNotNull { it.commanderId }
        if (commanders.distinct().size != commanders.size)
            return GameEngine.ActionResult(state, "Ein Kommandant kann nur einen Abschnitt führen.")
        chosen.forEach { deployment ->
            val id = deployment.commanderId
            if (id != null && (state.commanders.none { it.id == id } || state.commanderAway(id))) {
                return GameEngine.ActionResult(
                    state,
                    "Dieser Kommandant ist nicht in der Festung verfügbar.",
                )
            }
            if (
                deployment.units.any { it.amount <= 0 } ||
                    deployment.units.map { it.type }.distinct().size != deployment.units.size
            ) {
                return GameEngine.ActionResult(
                    state,
                    "Truppenmengen müssen positiv und je Kontingent eindeutig sein.",
                )
            }
            if (id != null && deployment.units.any { it.amount > state.assignedTo(id, it.type) }) {
                return GameEngine.ActionResult(
                    state,
                    "Ein Kommandant kann nur seine zugewiesenen Truppen einsetzen.",
                )
            }
        }
        UnitType.entries.forEach { type ->
            val total =
                chosen.sumOf { d ->
                    d.units.filter { it.type == type }.sumOf { it.amount.toLong() }
                }
            val direct =
                chosen
                    .filter { it.commanderId == null }
                    .sumOf { d -> d.units.filter { it.type == type }.sumOf { it.amount.toLong() } }
            if (total > state.homeSoldiers(type) || direct > state.directCommand(type)) {
                return GameEngine.ActionResult(
                    state,
                    "${type.label}: Truppen sind unterwegs, bereits zugewiesen oder mehrfach aufgestellt.",
                )
            }
        }
        val contingents =
            chosen.flatMap { d ->
                d.units.map { u ->
                    val pool = state.armyPools.first { it.type == u.type }
                    BattleContingent(
                        u.type,
                        d.commanderId,
                        d.section,
                        u.amount,
                        u.amount,
                        pool.experience,
                        pool.morale,
                        pool.equipment,
                    )
                }
            }
        if (
            contingents.isNotEmpty() &&
                contingents.none { it.section != BattleSection.RESERVE && it.soldiers > 0 }
        )
            return GameEngine.ActionResult(
                state,
                "Mindestens ein Kontingent muss eine Front halten.",
            )
        val strength =
            enemyStrength
                ?: (when (enemy) {
                    EnemyType.ORC -> 220
                    EnemyType.URUK -> 380
                    EnemyType.TAO_TEI -> 480
                } + state.realm.territory * 35 + state.victories * 55)
        val devices =
            if (tactic == Tactic.FORTIFY)
                state.invasion?.takeIf { it.enemy == enemy }?.devices
                    ?: when (enemy) {
                        EnemyType.ORC -> listOf(SiegeDevice.LADDERS, SiegeDevice.RAM)
                        EnemyType.URUK ->
                            listOf(SiegeDevice.RAM, SiegeDevice.TOWER, SiegeDevice.CATAPULT)
                        EnemyType.TAO_TEI -> listOf(SiegeDevice.CLIMBERS)
                    }
            else emptyList()
        val session =
            BattleSession(
                enemy,
                tactic,
                seed,
                contingents.sumOf { it.soldiers },
                strength,
                contingents,
                combatSections.mapIndexed { index, section ->
                    val number = strength / 3 + if (index == 1) strength % 3 else 0
                    BattleFront(section, number, number)
                },
                devices = devices,
                wallIntegrity = state.realm.wallIntegrity,
            )
        val next = state.copy(battleSession = session)
        if (session.ownStart == 0)
            return GameEngine.ActionResult(
                finish(next, session, false),
                "Ohne Truppen in der Festung wird der Angriff nicht aufgehalten.",
            )
        return GameEngine.ActionResult(
            next,
            "Aufstellung bereit. Jeder nächste Schritt verändert die Schlacht und wird gespeichert.",
        )
    }

    fun redeploy(state: GameState, deployments: List<BattleDeployment>): GameEngine.ActionResult {
        val session =
            state.battleSession
                ?: return GameEngine.ActionResult(state, "Keine Schlacht vorbereitet.")
        if (!session.isActive || session.minute != 0 || session.step != 0)
            return GameEngine.ActionResult(
                state,
                "Die Schlacht hat bereits begonnen; nutze die Ereignisbefehle.",
            )
        val prepared =
            start(
                state.copy(battleSession = null),
                session.enemy,
                session.tactic,
                deployments,
                session.seed,
                session.enemyStart,
            )
        return if (prepared.state.battleSession == null)
            GameEngine.ActionResult(state, prepared.message)
        else prepared
    }

    fun advance(state: GameState, decision: BattleDecision? = null): GameEngine.ActionResult {
        val original =
            state.battleSession ?: return GameEngine.ActionResult(state, "Keine Schlacht aktiv.")
        if (!original.isActive)
            return GameEngine.ActionResult(state, "Die Schlacht ist bereits beendet.")
        val event = original.pendingEvent
        if (event != null && decision !in event.options)
            return GameEngine.ActionResult(
                state,
                "Wähle zuerst einen Befehl für das aktuelle Ereignis.",
            )
        if (event == null && decision != null)
            return GameEngine.ActionResult(
                state,
                "Für diesen Schritt ist kein Sonderbefehl vorgesehen.",
            )
        if (original.status == BattleStatus.PURSUIT)
            return resolvePursuit(state, original, decision!!)
        val rng = Random(original.seed xor ((original.step + 1) * 104729))
        var troops = original.contingents
        val target = event?.section
        val commandLog = mutableListOf<String>()
        if (decision == BattleDecision.SEND_RESERVE) {
            var available = 300
            val moved = mutableListOf<BattleContingent>()
            troops =
                troops
                    .map { c ->
                        if (c.section != BattleSection.RESERVE || available == 0 || c.soldiers == 0)
                            c
                        else {
                            val amount = c.soldiers.coerceAtMost(available)
                            available -= amount
                            // startSoldiers follows the split so final training/morale remains
                            // weighted correctly.
                            val initial = (c.startSoldiers.toDouble() * amount / c.soldiers).toInt()
                            moved +=
                                c.copy(
                                    section = target ?: BattleSection.CENTER,
                                    soldiers = amount,
                                    startSoldiers = initial,
                                )
                            c.copy(
                                soldiers = c.soldiers - amount,
                                startSoldiers = c.startSoldiers - initial,
                            )
                        }
                    }
                    .filter { it.startSoldiers > 0 } + moved
            commandLog +=
                "${300 - available} Reservisten verstärken ${(target ?: BattleSection.CENTER).label}."
        } else if (decision != null)
            commandLog += decision.label + ": " + (target?.label ?: "Zentrum") + "."
        if (decision == BattleDecision.CAVALRY_CHARGE) {
            troops =
                troops.map {
                    if (it.type == UnitType.KNIGHT && it.soldiers > 0)
                        it.copy(section = target ?: BattleSection.CENTER)
                    else it
                }
        }
        val minute = original.minute + 5
        val phase = phase(minute)
        var devices = original.devices
        var integrity = original.wallIntegrity
        val sounds = mutableListOf<BattleSoundCue>()
        if (phase == BattlePhase.RANGED) sounds += BattleSoundCue.ARROWS
        else sounds += BattleSoundCue.SWORDS
        if (original.enemy == EnemyType.TAO_TEI) sounds += BattleSoundCue.MONSTERS
        if (minute >= 15 && devices.isNotEmpty()) {
            val artillery =
                troops.filter { it.type == UnitType.DRAGON_ARTILLERY }.sumOf { it.soldiers }
            val siegeSkill =
                troops.sumOf { c ->
                    c.soldiers.toDouble() *
                        (c.commanderId?.let { id ->
                            state.commanders.firstOrNull { it.id == id }?.siege
                        } ?: state.player.tactics)
                } / troops.sumOf { it.soldiers }.coerceAtLeast(1)
            if (rng.nextDouble() < (artillery / 350.0 + siegeSkill / 600.0).coerceAtMost(0.65)) {
                val destroyed = devices[rng.nextInt(devices.size)]
                devices = devices - destroyed
                commandLog += "${destroyed.label} wird durch die Verteidigung ausgeschaltet."
            }
            val damage =
                devices
                    .map {
                        when (it) {
                            SiegeDevice.RAM -> 4
                            SiegeDevice.TOWER -> 3
                            SiegeDevice.LADDERS -> 1
                            SiegeDevice.CATAPULT -> 5
                            SiegeDevice.CLIMBERS -> 2
                        }
                    }
                    .sum()
            integrity =
                (integrity - max(0, damage - state.realm.level(BuildingType.TOWER))).coerceAtLeast(
                    0
                )
            if (original.wallIntegrity > 0 && integrity == 0) {
                sounds += BattleSoundCue.WALL_BREAK
                commandLog += "Das Tor bricht; der Schutz der Mauer ist verloren."
            }
        }
        val losses = mutableListOf<UnitAllocation>()
        val commandedLosses = mutableMapOf<Pair<Long, UnitType>, Int>()
        var enemyLossTotal = 0
        val changedTroops = troops.toMutableList()
        val fronts =
            original.fronts.map { front ->
                val indices =
                    troops.indices.filter {
                        troops[it].section == front.section && troops[it].soldiers > 0
                    }
                val defenders = indices.map { troops[it] }
                val count = defenders.sumOf { it.soldiers }
                val focus = target == front.section
                val wall =
                    if (original.tactic == Tactic.FORTIFY)
                        integrity / 100.0 *
                            (state.realm.level(BuildingType.WALL) * 0.08 +
                                state.realm.level(BuildingType.TOWER) * 0.05)
                    else 0.0
                val ownPower =
                    defenders.sumOf { combatPower(state, it, phase) } *
                        tacticBonus(original.tactic, phase, front.section) *
                        (1.0 + wall) *
                        (if (state.resources.food == 0) 0.70 else 1.0) *
                        when {
                            focus && decision == BattleDecision.FOCUS_ARCHERS ->
                                if (defenders.any { it.type.ranged >= 8 })
                                    1.20 + state.player.tactics / 600.0
                                else 1.0
                            focus && decision == BattleDecision.CAVALRY_CHARGE ->
                                if (troops.any { it.type == UnitType.KNIGHT && it.soldiers > 0 })
                                    1.28
                                else 1.0
                            focus && decision == BattleDecision.HOLD ->
                                1.0 + state.player.tactics / 500.0
                            else -> 1.0
                        }
                val enemyQuality =
                    when (original.enemy) {
                        EnemyType.ORC -> 13.0
                        EnemyType.URUK -> 21.0
                        EnemyType.TAO_TEI -> 19.0
                    }
                val enemyPower = front.enemySoldiers * enemyQuality * (0.5 + front.morale / 160.0)
                val pressure = enemyPower / ownPower.coerceAtLeast(1.0)
                val ranged = phase == BattlePhase.RANGED
                val lossRate =
                    (if (front.enemySoldiers == 0) 0.0 else if (ranged) 0.013 else 0.022) *
                        pressure.coerceIn(0.25, 4.5) *
                        rng.nextDouble(0.8, 1.2) *
                        (if (focus && decision == BattleDecision.RETREAT_LINE) 0.55 else 1.0) *
                        (if (focus && decision == BattleDecision.CAVALRY_CHARGE) 1.3 else 1.0)
                indices.forEach { i ->
                    val c = troops[i]
                    val defense = (0.65 + c.type.defense / 20.0) * (0.55 + c.equipment / 200.0)
                    val loss = ceil(c.soldiers * lossRate / defense).toInt().coerceIn(0, c.soldiers)
                    losses += UnitAllocation(c.type, loss)
                    c.commanderId?.let { id ->
                        val key = id to c.type
                        commandedLosses[key] = (commandedLosses[key] ?: 0) + loss
                    }
                    val leader =
                        if (c.commanderId == null) state.player.leadership
                        else
                            state.commanders.firstOrNull { it.id == c.commanderId }?.leadership ?: 0
                    val moraleDelta = if (pressure > 1.4) -4 else if (pressure < 0.7) 2 else -1
                    changedTroops[i] =
                        c.copy(
                            soldiers = c.soldiers - loss,
                            morale =
                                (c.morale + moraleDelta -
                                        (if (state.resources.food == 0) 3 else 0) +
                                        (if (focus && decision == BattleDecision.HOLD) leader / 30
                                        else 0))
                                    .coerceIn(0, 100),
                        )
                }
                val opposingRate =
                    (if (ranged) 0.020 else 0.030) *
                        (ownPower / enemyPower.coerceAtLeast(1.0)).coerceIn(0.0, 4.5) *
                        rng.nextDouble(0.8, 1.2)
                val enemyLoss =
                    if (count == 0) 0
                    else
                        ceil(front.enemySoldiers * opposingRate)
                            .toInt()
                            .coerceIn(0, front.enemySoldiers)
                enemyLossTotal += enemyLoss
                val morale =
                    (front.morale -
                            if (enemyLoss > front.enemySoldiers * 0.06) 6
                            else if (pressure < 0.8) 3 else 1)
                        .coerceAtLeast(0)
                val movement =
                    when {
                        count == 0 -> -8
                        pressure < 0.7 -> 5
                        pressure > 1.4 -> -5
                        else -> 0
                    }
                front.copy(
                    enemySoldiers = front.enemySoldiers - enemyLoss,
                    morale = morale,
                    position =
                        (front.position + movement -
                                if (focus && decision == BattleDecision.RETREAT_LINE) 5 else 0)
                            .coerceIn(0, 100),
                )
            }
        if (minute == 30) {
            val exposed =
                changedTroops.indices.filter {
                    changedTroops[it].commanderId != null && changedTroops[it].soldiers > 0
                }
            if (exposed.isNotEmpty() && rng.nextDouble() < 0.35) {
                val woundedId = changedTroops[exposed[rng.nextInt(exposed.size)]].commanderId
                changedTroops.indices.forEach { i ->
                    if (changedTroops[i].commanderId == woundedId)
                        changedTroops[i] = changedTroops[i].copy(commanderWounded = true)
                }
                commandLog +=
                    "${state.commanders.firstOrNull { it.id == woundedId }?.name ?: "Ein Kommandant"} ist verwundet; sein Kommando verliert an Wirkung."
            }
        }
        var session =
            original.copy(
                minute = minute,
                step = original.step + 1,
                phase = phase,
                contingents = changedTroops,
                fronts = fronts,
                devices = devices,
                wallIntegrity = integrity,
                pendingEvent = null,
                lastSounds = sounds.distinct(),
                log =
                    (original.log +
                            BattleLogEntry(
                                minute,
                                (commandLog + phase.label + ".").joinToString(" "),
                                losses.sumOf { it.amount },
                                enemyLossTotal,
                            ))
                        .takeLast(60),
            )
        var next =
            ArmyEngine.applyLosses(reduceAssignments(state, commandedLosses), losses)
                .copy(
                    battleSession = session,
                    realm =
                        state.realm.copy(
                            wallIntegrity =
                                if (original.tactic == Tactic.FORTIFY) integrity
                                else state.realm.wallIntegrity
                        ),
                )
        val enemyMorale =
            fronts.sumOf { it.morale * it.enemySoldiers.toLong() }.toDouble() /
                session.enemyRemaining.coerceAtLeast(1)
        val ownRatio = session.ownRemaining.toDouble() / session.ownStart.coerceAtLeast(1)
        val enemyRatio = session.enemyRemaining.toDouble() / session.enemyStart
        val victory =
            session.enemyRemaining == 0 || enemyRatio < 0.28 || (minute >= 35 && enemyMorale < 25)
        val defeat =
            session.ownRemaining == 0 ||
                ownRatio < 0.24 ||
                (minute >= 35 && session.morale < 20) ||
                fronts.count { it.position <= 10 && it.enemySoldiers > 0 } >= 2
        if (victory || (minute >= 90 && !defeat && ownRatio > enemyRatio)) {
            session =
                session.copy(
                    status = BattleStatus.PURSUIT,
                    phase = BattlePhase.PURSUIT,
                    pendingEvent =
                        BattleEvent(
                            "Der Gegner flieht",
                            "Verfolgung bringt mehr Beute, kann aber weitere Soldaten kosten.",
                            BattleSection.CENTER,
                            listOf(BattleDecision.PURSUE, BattleDecision.HOLD_FORMATION),
                        ),
                    lastSounds = listOf(BattleSoundCue.HORN),
                )
            next = next.copy(battleSession = session)
        } else if (defeat || minute >= 90) next = finish(next, session, false)
        else if (minute % 10 == 0 || minute == 25 || minute == 45 || minute == 65) {
            session = session.copy(pendingEvent = nextEvent(session, rng))
            next = next.copy(battleSession = session)
        }
        return GameEngine.ActionResult(
            next,
            if (next.battleSession?.pendingEvent != null) "Ein Ereignis verlangt deinen Befehl."
            else
                "Minute $minute: ${losses.sumOf { it.amount }} eigene und $enemyLossTotal feindliche Verluste.",
        )
    }

    private fun phase(minute: Int): BattlePhase =
        when {
            minute <= 10 -> BattlePhase.RANGED
            minute <= 20 -> BattlePhase.CONTACT
            minute <= 35 -> BattlePhase.MAIN
            minute <= 45 -> BattlePhase.RESERVES
            minute <= 65 -> BattlePhase.CRITICAL
            else -> BattlePhase.DECISION
        }

    private fun combatPower(state: GameState, c: BattleContingent, phase: BattlePhase): Double {
        val commander = c.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id } }
        val leadership = commander?.leadership ?: state.player.leadership
        val tactics = commander?.tactics ?: state.player.tactics
        val skill =
            if (phase == BattlePhase.RANGED) commander?.bow ?: state.player.bow
            else commander?.sword ?: state.player.sword
        val stats =
            if (phase == BattlePhase.RANGED) c.type.ranged * 2.0 + c.type.defense * 0.3
            else c.type.attack + c.type.defense + c.type.ranged * 0.35
        val cavalry =
            if (c.type == UnitType.KNIGHT && commander == null) 1.0 + state.player.riding / 700.0
            else 1.0
        val command =
            (1.0 + leadership / 600.0 + tactics / 450.0 + skill / 700.0) *
                if (c.commanderWounded) 0.80 else 1.0
        val fortress =
            if (state.battleSession?.tactic == Tactic.FORTIFY && c.type.culture == Culture.WALL)
                1.12
            else 1.0
        return c.soldiers *
            stats *
            (0.5 + c.morale / 200.0) *
            (0.5 + c.equipment / 200.0) *
            (1.0 + c.experience / 200.0) *
            command *
            cavalry *
            fortress
    }

    private fun tacticBonus(tactic: Tactic, phase: BattlePhase, section: BattleSection): Double =
        when (tactic) {
            Tactic.HOLD -> 1.06
            Tactic.AGGRESSIVE -> if (phase == BattlePhase.RANGED) 0.94 else 1.13
            Tactic.RANGED -> if (phase == BattlePhase.RANGED) 1.30 else 1.03
            Tactic.FLANK -> if (section == BattleSection.CENTER) 0.96 else 1.18
            Tactic.FORTIFY -> if (phase == BattlePhase.RANGED) 1.20 else 1.04
        }

    private fun nextEvent(session: BattleSession, rng: Random): BattleEvent {
        val front = session.fronts.minBy { it.position + session.soldiers(it.section) / 20 }
        val commanders = session.contingents.filter { it.commanderId != null && it.soldiers > 0 }
        val (title, text) =
            when {
                session.tactic == Tactic.FORTIFY && session.wallIntegrity < 45 ->
                    "Das Tor droht zu brechen" to
                        "Die Belagerungsgeräte setzen dem Tor zu. Entlaste die Verteidiger."
                session.enemy == EnemyType.TAO_TEI && session.minute == 20 ->
                    "Monster erklimmen die Mauer" to
                        "Kletterer greifen den schwächsten Abschnitt an."
                session.minute == 30 && commanders.isNotEmpty() ->
                    "Ein Kommandant gerät in Gefahr" to
                        "Sein Kontingent wird bedrängt. Verstärkung kann den Druck verringern."
                session.minute >= 55 && front.position > 55 ->
                    "Der feindliche General wird entdeckt" to
                        "Ein entschlossener Angriff kann die gegnerische Moral brechen."
                rng.nextBoolean() ->
                    "${front.section.label} gerät unter Druck" to
                        "Die Linie braucht einen klaren Befehl."
                else ->
                    "Feindliche Kavallerie am Flügel" to
                        "Der Gegner versucht, deine Stellung zu umgehen."
            }
        val options = mutableListOf(BattleDecision.HOLD, BattleDecision.RETREAT_LINE)
        if (session.soldiers(BattleSection.RESERVE) > 0) options += BattleDecision.SEND_RESERVE
        if (
            session.contingents.any {
                it.section == front.section && it.soldiers > 0 && it.type.ranged >= 8
            }
        )
            options += BattleDecision.FOCUS_ARCHERS
        if (session.contingents.any { it.soldiers > 0 && it.type == UnitType.KNIGHT })
            options += BattleDecision.CAVALRY_CHARGE
        return BattleEvent(title, text, front.section, options)
    }

    private fun resolvePursuit(
        state: GameState,
        original: BattleSession,
        decision: BattleDecision,
    ): GameEngine.ActionResult {
        val rng = Random(original.seed xor (original.step + 1) * 104729)
        val losses =
            if (decision == BattleDecision.PURSUE)
                original.contingents.map { c ->
                    UnitAllocation(
                        c.type,
                        ceil(c.soldiers * rng.nextDouble(0.006, 0.025))
                            .toInt()
                            .coerceAtMost(c.soldiers),
                    )
                }
            else emptyList()
        // Losses are applied to each contingent in the same order before aggregating pool removals.
        val troops =
            original.contingents.mapIndexed { i, c ->
                c.copy(soldiers = c.soldiers - (losses.getOrNull(i)?.amount ?: 0))
            }
        val enemyFactor = if (decision == BattleDecision.PURSUE) 0.45 else 1.0
        val fronts =
            original.fronts.map {
                it.copy(enemySoldiers = (it.enemySoldiers * enemyFactor).toInt())
            }
        val enemyLoss = original.enemyRemaining - fronts.sumOf { it.enemySoldiers }
        val commandedLosses = mutableMapOf<Pair<Long, UnitType>, Int>()
        original.contingents.forEachIndexed { index, c ->
            c.commanderId?.let { id ->
                val key = id to c.type
                commandedLosses[key] =
                    (commandedLosses[key] ?: 0) + (losses.getOrNull(index)?.amount ?: 0)
            }
        }
        val session =
            original.copy(
                minute = original.minute + 5,
                step = original.step + 1,
                contingents = troops,
                fronts = fronts,
                lootGold = original.enemyStart / (if (decision == BattleDecision.PURSUE) 3 else 5),
                pendingEvent = null,
                log =
                    original.log +
                        BattleLogEntry(
                            original.minute + 5,
                            decision.label + ".",
                            losses.sumOf { it.amount },
                            enemyLoss,
                        ),
            )
        val next =
            finish(
                ArmyEngine.applyLosses(reduceAssignments(state, commandedLosses), losses)
                    .copy(battleSession = session),
                session,
                true,
            )
        return GameEngine.ActionResult(
            next,
            "Sieg! ${session.lootGold} Gold erbeutet. Das Ergebnis ist gespeichert.",
        )
    }

    private fun finish(state: GameState, session: BattleSession, victory: Boolean): GameState {
        val final =
            session.copy(
                status = if (victory) BattleStatus.VICTORY else BattleStatus.DEFEAT,
                pendingEvent = null,
                phase = BattlePhase.PURSUIT,
                lastSounds = listOf(BattleSoundCue.HORN),
            )
        val pools =
            state.armyPools.map { pool ->
                val participants =
                    session.contingents.filter { it.type == pool.type }.sumOf { it.soldiers }
                val participantMorale =
                    session.contingents
                        .filter { it.type == pool.type }
                        .sumOf { it.soldiers.toLong() * it.morale }
                if (participants == 0 || pool.soldiers == 0) pool
                else
                    pool.copy(
                        experience =
                            (pool.experience +
                                    ((if (victory) 9L else 4L) * participants / pool.soldiers)
                                        .toInt())
                                .coerceAtMost(100),
                        morale =
                            ((pool.morale.toLong() * (pool.soldiers - participants) +
                                    participantMorale +
                                    (if (victory) 7L else -12L) * participants) / pool.soldiers)
                                .toInt()
                                .coerceIn(0, 100),
                    )
            }
        val result =
            state.copy(
                battleSession = final,
                armyPools = pools,
                resources =
                    EconomyEngine.add(
                        state.resources,
                        Resources(
                            if (victory) final.lootGold else 0,
                            if (victory) session.enemyStart / 8 else 0,
                            0,
                            0,
                            0,
                        ),
                    ),
                victories = state.victories + if (victory) 1 else 0,
                defeats = state.defeats + if (victory) 0 else 1,
                renown = state.renown + if (victory) max(15, session.enemyStart / 12) else 4,
                invasion =
                    state.invasion?.takeUnless {
                        it.arrivalDay <= state.day && it.enemy == session.enemy
                    },
                realm =
                    state.realm.copy(
                        threat = (state.realm.threat - if (victory) 38 else 12).coerceAtLeast(0)
                    ),
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                if (victory) "Schlacht gewonnen" else "Schlacht verloren",
                                "${session.enemy.label}: ${session.ownStart - session.ownRemaining} eigene und ${session.enemyStart - session.enemyRemaining} feindliche Verluste.",
                            ))
                        .takeLast(80),
            )
        return ProgressionEngine.update(
            ProgressionEngine.awardXp(
                RelationshipEngine.onEvent(
                    ArmyEngine.clampAssignments(result),
                    if (victory) "feast" else "defeat",
                ),
                if (victory) 90 + session.enemyStart / 8 else 30,
            )
        )
    }

    private fun reduceAssignments(
        state: GameState,
        losses: Map<Pair<Long, UnitType>, Int>,
    ): GameState =
        state.copy(
            commanderAssignments =
                state.commanderAssignments.map { assignment ->
                    assignment.copy(
                        units =
                            assignment.units.mapNotNull { u ->
                                val remaining =
                                    (u.amount - (losses[assignment.commanderId to u.type] ?: 0))
                                        .coerceAtLeast(0)
                                if (remaining > 0) u.copy(amount = remaining) else null
                            }
                    )
                }
        )
}
