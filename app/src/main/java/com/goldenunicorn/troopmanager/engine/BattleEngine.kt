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
        terrain: Map<BattleSection, BattleTerrain> = emptyMap(),
        participation: BattleParticipation = BattleParticipation.COMMAND,
        rangedWeather: Double = 1.0,
        cavalryWeather: Double = 1.0,
        seasonPenalty: Double = 1.0,
        enemyFactionName: String? = null,
        enemyUnits: List<UnitAllocation> = emptyList(),
        location: String? = null,
        enemyFactionId: String? = null,
        enemyArmyName: String? = null,
        enemyMorale: Int = 80,
        ownMorale: Int? = null,
        enemyFortification: Int = 0,
        enemyExperience: Int = 0,
    ): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true)
            return GameEngine.ActionResult(
                state,
                "Die laufende Schlacht muss zuerst beendet werden.",
            )
        if (enemyStrength != null && enemyStrength <= 0)
            return GameEngine.ActionResult(state, "Die Gegnerstärke muss positiv sein.")
        if (!rangedWeather.isFinite() || !cavalryWeather.isFinite() || !seasonPenalty.isFinite()) return GameEngine.ActionResult(state, "Ungültige Schlachtbedingungen.")
        if (participation == BattleParticipation.PERSONAL && state.war.playerCondition != CombatantStatus.ACTIVE)
            return GameEngine.ActionResult(state, "Du bist nicht für persönliche Schlachtteilnahme einsatzbereit.")
        val chosen = deployments.ifEmpty { defaultDeployments(state) }
        val fieldArmy = state.world.encounter?.playerArmyId?.let { id -> state.world.armies.firstOrNull { it.id == id } }
        if (fieldArmy != null) {
            if (chosen.any { it.commanderId != fieldArmy.commanderId }) return GameEngine.ActionResult(state, "Im Feld kann nur das anwesende Expeditionskommando aufgestellt werden.")
            UnitType.entries.forEach { type ->
                val available = fieldArmy.units.filter { it.type == type }.sumOf { it.amount.toLong() }
                val deployed = chosen.sumOf { d -> d.units.filter { it.type == type }.sumOf { it.amount.toLong() } }
                if (deployed != available) return GameEngine.ActionResult(state, "${type.label}: Alle $available Soldaten der Expedition müssen einer Front oder der Reserve zugeordnet sein.")
            }
        }
        if (chosen.sumOf { d -> d.units.sumOf { it.amount.toLong() } } > Int.MAX_VALUE.toLong()) return GameEngine.ActionResult(state, "Die Gesamtstärke überschreitet das unterstützte Maximum.")
        val commanders = chosen.mapNotNull { it.commanderId }
        if (commanders.distinct().size != commanders.size)
            return GameEngine.ActionResult(state, "Ein Kommandant kann nur einen Abschnitt führen.")
        chosen.forEach { deployment ->
            val id = deployment.commanderId
            if (id != null && state.commanders.none { it.id == id }) {
                return GameEngine.ActionResult(state, "Dieser Kommandant existiert nicht.")
            }
            if (id != null && fieldArmy == null && state.commanderAway(id)) {
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
            if (fieldArmy == null && id != null && deployment.units.any { it.amount > state.assignedTo(id, it.type) }) {
                return GameEngine.ActionResult(
                    state,
                    "Ein Kommandant kann nur seine zugewiesenen Truppen einsetzen.",
                )
            }
        }
        if (fieldArmy == null) {
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
        }
        val contingents =
            chosen.flatMap { d ->
                d.units.map { u ->
                    val pool = state.armyPools.first { it.type == u.type }
                    val named = state.frontier.designs.firstOrNull { it.unitType == u.type && it.soldiers > 0 }
                    val namedCount = minOf(u.amount, named?.soldiers ?: 0)
                    BattleContingent(
                        u.type,
                        d.commanderId,
                        d.section,
                        u.amount,
                        u.amount,
                        pool.experience,
                        ((ownMorale ?: pool.morale) + CharacterEngine.bonuses(state).morale + (d.commanderId?.let { CharacterEngine.commanderMorale(state, it) } ?: 0) + if (participation == BattleParticipation.PERSONAL) 8 else 0).coerceAtMost(100),
                        pool.equipment,
                        displayName = if (namedCount > 0) named?.name else null,
                        designId = if (namedCount > 0) named?.id else null,
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
        val wallVolley = if (tactic == Tactic.FORTIFY) state.frontier.weapons.filter { it.count > 0 && it.ammunition > 0 }.sumOf { it.type.defense * it.count } else 0
        val strength =
            ((enemyStrength
                ?: (when (enemy) {
                    EnemyType.ORC -> 220
                    EnemyType.URUK -> 380
                    EnemyType.TAO_TEI -> 480
                } + state.realm.territory * 35 + state.victories * 55)) - wallVolley).coerceAtLeast(40)
        if (enemyUnits.isNotEmpty() && (enemyUnits.any { it.amount <= 0 } || enemyUnits.map { it.type }.distinct().size != enemyUnits.size || enemyUnits.sumOf { it.amount.toLong() } != strength.toLong()))
            return GameEngine.ActionResult(state, "Die gegnerische Truppenliste muss der Startstärke entsprechen.")
        val devices =
            if (tactic == Tactic.FORTIFY)
                state.invasion?.takeIf { it.enemy == enemy }?.devices
                    ?: when (enemy) {
                        EnemyType.ORC -> listOf(SiegeDevice.LADDERS, SiegeDevice.RAM)
                        EnemyType.URUK ->
                            listOf(SiegeDevice.RAM, SiegeDevice.TOWER, SiegeDevice.CATAPULT, SiegeDevice.TUNNEL)
                        EnemyType.TAO_TEI -> listOf(SiegeDevice.CLIMBERS)
                    }
            else emptyList()
        val battlefield = BattleSection.entries.associateWith { section ->
            terrain[section] ?: if (tactic == Tactic.FORTIFY) {
                if (section == BattleSection.RESERVE) BattleTerrain.STREET else BattleTerrain.WALL
            } else BattleTerrain.entries[((kotlin.math.abs(seed.toLong()) + section.ordinal * 5) % 7L).toInt()]
        }
        val heroPerk = CharacterEngine.hasPerk(state, PlayerPerk.WARFARE_HERO)
        val reservePerk = CharacterEngine.hasPerk(state, PlayerPerk.LEADERSHIP_RESERVE)
        val feignedPerk = CharacterEngine.hasPerk(state, PlayerPerk.WARFARE_FEIGNED_RETREAT)
        val rallyPerk = CharacterEngine.hasPerk(state, PlayerPerk.WARFARE_RALLY)
        val cp = (20 + state.player.leadership / 10).coerceAtMost(35)
        val combatResources =
            if (tactic == Tactic.FORTIFY)
                state.resources.copy(
                    food =
                        (state.resources.food.toLong() + state.war.siegeFoodStored)
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                )
            else state.resources
        val rangedSoldiers =
            contingents.filter { it.type.ranged >= 8 }.sumOf { it.soldiers.toLong() }
        val desiredArrows =
            (rangedSoldiers * 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val arrowsLoaded = minOf(state.militaryStock.arrows, desiredArrows)
        val rangedSupplyFactor =
            if (desiredArrows == 0) 1.0
            else (arrowsLoaded.toDouble() / desiredArrows).coerceIn(0.35, 1.0)
        var session =
            BattleSession(
                enemy,
                tactic,
                seed,
                contingents.sumOf { it.soldiers },
                strength,
                contingents,
                combatSections.mapIndexed { index, section ->
                    val totalAtFront = contingents.filter { it.section != BattleSection.RESERVE }.sumOf { it.soldiers.toLong() }.coerceAtLeast(1)
                    val lastOccupied = combatSections.lastOrNull { chosenSection -> contingents.any { it.section == chosenSection && it.soldiers > 0 } }
                    val allocatedBefore = combatSections.take(index).sumOf { prior -> strength.toLong() * contingents.filter { it.section == prior }.sumOf { it.soldiers.toLong() } / totalAtFront }
                    val number = if (fieldArmy == null) strength / 3 + if (index == 1) strength % 3 else 0
                        else if (section == lastOccupied) (strength - allocatedBefore).toInt()
                        else (strength.toLong() * contingents.filter { it.section == section }.sumOf { it.soldiers.toLong() } / totalAtFront).toInt()
                    BattleFront(section, number, number, morale = enemyMorale.coerceIn(0, 100))
                },
                devices = devices,
                wallIntegrity = state.realm.wallIntegrity,
                terrain = battlefield, participation = participation, enemyFactionName = enemyFactionName, enemyUnits = enemyUnits, location = location, enemyFactionId = enemyFactionId, enemyArmyName = enemyArmyName, enemyFortification = enemyFortification.coerceIn(0, 100), deployedMorale = ownMorale?.coerceIn(0, 100), enemyExperience = enemyExperience.coerceIn(0, 10000),
                commandPoints = cp, maxCommandPoints = cp,
                heroPerk = heroPerk, reservePerk = reservePerk, feignedRetreatPerk = feignedPerk, rallyPerk = rallyPerk,
                moraleBonus = CharacterEngine.bonuses(state).morale, healingBonus = CharacterEngine.bonuses(state).healing,
                rangedWeather = rangedWeather.coerceIn(0.25, 1.5),
                cavalryWeather = cavalryWeather.coerceIn(0.25, 1.5),
                seasonPenalty = seasonPenalty.coerceIn(0.5, 1.5),
                rangedSupplyFactor = rangedSupplyFactor,
            )
        session =
            session.copy(
                replayStart =
                    BattleReplayStart(
                        state.player,
                        state.commanders,
                        state.realm,
                        combatResources,
                        state.armyPools,
                        state.population,
                        state.commanderAssignments,
                        session.contingents,
                        session.fronts,
                        session.devices,
                        state.day,
                        state.war.equipment,
                        heroPerk,
                        reservePerk,
                        feignedPerk,
                        rallyPerk,
                        session.moraleBonus,
                        session.seasonPenalty,
                        session.rangedWeather,
                        session.cavalryWeather,
                        session.healingBonus,
                        state.war.buildingDamage,
                        state.war.gateReinforcement,
                        state.war.eliteUnits,
                        state.settings.permadeath,
                        session.enemyFortification,
                        session.location,
                        session.deployedMorale,
                        session.enemyExperience,
                        state.doctrine,
                        session.rangedSupplyFactor,
                        frontier = state.frontier,
                        companion = state.companion,
                    )
            )
        val next =
            FrontierEngine.prepareBattle(state.copy(
                battleSession = session,
                resources = combatResources,
                militaryStock =
                    state.militaryStock.copy(
                        arrows = (state.militaryStock.arrows - arrowsLoaded).coerceAtLeast(0)
                    ),
                war =
                    if (tactic == Tactic.FORTIFY)
                        state.war.copy(siegeFoodStored = 0)
                    else state.war,
            ))
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
                session.terrain, session.participation, session.rangedWeather, session.cavalryWeather, session.seasonPenalty, session.enemyFactionName, session.enemyUnits, session.location, session.enemyFactionId, session.enemyArmyName, session.fronts.firstOrNull()?.morale ?: 80, session.deployedMorale, session.enemyFortification, session.enemyExperience,
            )
        return if (prepared.state.battleSession == null)
            GameEngine.ActionResult(state, prepared.message)
        else prepared
    }

    fun configure(state: GameState, terrain: Map<BattleSection, BattleTerrain>? = null, participation: BattleParticipation? = null): GameEngine.ActionResult {
        val session = state.battleSession ?: return GameEngine.ActionResult(state, "Keine Schlacht vorbereitet.")
        if (!session.isActive || session.minute != 0 || session.step != 0) return GameEngine.ActionResult(state, "Konfiguration ist nur vor Kampfbeginn möglich.")
        val deployments = session.contingents.groupBy { it.commanderId to it.section }.map { (key, troops) -> BattleDeployment(key.first, key.second, ArmyEngine.normalize(troops.map { UnitAllocation(it.type, it.soldiers) })) }
        val result = start(state.copy(battleSession = null), session.enemy, session.tactic, deployments, session.seed, session.enemyStart, terrain ?: session.terrain, participation ?: session.participation, session.rangedWeather, session.cavalryWeather, session.seasonPenalty, session.enemyFactionName, session.enemyUnits, session.location, session.enemyFactionId, session.enemyArmyName, session.fronts.firstOrNull()?.morale ?: 80, session.deployedMorale, session.enemyFortification, session.enemyExperience)
        return if (result.state.battleSession == null) GameEngine.ActionResult(state, result.message) else result
    }

    fun advance(initialState: GameState, decision: BattleDecision? = null): GameEngine.ActionResult {
        var state = initialState
        var original =
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
        if (decision != null && !canOrder(original, decision, event?.section))
            return GameEngine.ActionResult(state, "Für diesen Befehl fehlen passende Truppen oder Voraussetzungen.")
        val siegeCost = decision?.let { assaultCost(it) } ?: Resources(0, 0, 0, 0, 0)
        if (state.resources.wood < siegeCost.wood || state.resources.iron < siegeCost.iron) return GameEngine.ActionResult(state, "Dieser Angriff benötigt ${siegeCost.wood} Holz und ${siegeCost.iron} Eisen.")
        val cost = decision?.let { orderCost(original, it) } ?: 0
        if (cost > original.commandPoints) return GameEngine.ActionResult(state, "Dieser Befehl benötigt $cost Befehlspunkte; ${original.commandPoints} verfügbar.")
        original = original.copy(commandPoints = (original.commandPoints - cost + commandRegeneration(state, original)).coerceAtMost(original.maxCommandPoints), inputs = (original.inputs + BattleInput(decision, event)).takeLast(40))
        if (original.status == BattleStatus.PURSUIT)
            return resolvePursuit(state, original, decision!!)
        if (decision == BattleDecision.ORDERED_RETREAT)
            return resolveRetreat(state, original)
        val volley = FrontierEngine.fireWallWeapons(state, original)
        state = volley.state
        original = volley.battle
        val rng = Random(original.seed xor ((original.step + 1) * 104729))
        var troops = original.contingents
        val target = event?.section
        val commandLog = mutableListOf<String>()
        val commanderEvents = original.commanderEvents.toMutableList()
        if (decision in listOf(BattleDecision.SEND_RESERVE, BattleDecision.STRENGTHEN_SECTION, BattleDecision.RELOCATE_RESERVE, BattleDecision.RESCUE_COMMANDER)) {
            val limit = when (decision) {
                BattleDecision.RESCUE_COMMANDER -> 20
                BattleDecision.RELOCATE_RESERVE -> 100
                else -> if (original.reservePerk) 600 else 300
            }
            var available = limit
            val moved = mutableListOf<BattleContingent>()
            troops =
                troops
                    .map { c ->
                        if (c.section != BattleSection.RESERVE || c.routed || available == 0 || c.soldiers == 0)
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
                                    morale = if (decision == BattleDecision.RELOCATE_RESERVE) (c.morale + 5).coerceAtMost(100) else c.morale,
                                )
                            c.copy(
                                soldiers = c.soldiers - amount,
                                startSoldiers = c.startSoldiers - initial,
                            )
                        }
                    } + moved
            commandLog +=
                "${limit - available} Reservisten verstärken ${(target ?: BattleSection.CENTER).label}."
        } else if (decision != null)
            commandLog += decision.label + ": " + (target?.label ?: "Zentrum") + "."
        if (decision == BattleDecision.RESCUE_COMMANDER) {
            val rescuedIds = troops.filter { it.section == target && it.commanderWounded && !it.commanderRescued }.mapNotNull { it.commanderId }.toSet()
            // The injury belongs to the commander, including all of his split contingents.
            troops = troops.map { if (it.commanderId in rescuedIds) it.copy(commanderWounded = true, commanderRescued = true, morale = (it.morale + 6).coerceAtMost(100)) else it }
            rescuedIds.forEach { id ->
                val message = "${state.commanders.firstOrNull { it.id == id }?.name ?: "Kommandant"} wird geborgen; ein Teil seiner Führungswirkung kehrt zurück."
                commandLog += message
                commanderEvents += message
            }
        }
        if (decision == BattleDecision.CAVALRY_CHARGE || decision == BattleDecision.OPEN_GATE) {
            troops =
                troops.map {
                    if (it.type == UnitType.KNIGHT && it.soldiers > 0 && !it.routed)
                        it.copy(section = target ?: BattleSection.CENTER)
                    else it
                }
        }
        if (decision == BattleDecision.RALLY) {
            troops = troops.map { c -> if (c.section == target && c.soldiers > 0) c.copy(morale = (c.morale + 18).coerceAtMost(100), routed = false) else c }
            commandLog += "Eine Schlachtrede bringt wankende Formationen zurück in die Linie."
        }
        val minute = original.minute + 5
        val phase = phase(minute)
        var enemyFort = original.enemyFortification
        var devices = original.devices
        var integrity = original.wallIntegrity
        if (decision in listOf(BattleDecision.SCALE_WALL, BattleDecision.BREACH_GATE, BattleDecision.TOWER_ASSAULT, BattleDecision.UNDERMINE) || decision == BattleDecision.ARTILLERY_TARGET && enemyFort > 0) {
            val siegeSkill = troops.mapNotNull { c -> c.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.siege } }.maxOrNull() ?: state.player.tactics
            val breach = when (decision) { BattleDecision.UNDERMINE -> 18; BattleDecision.ARTILLERY_TARGET -> 15; BattleDecision.TOWER_ASSAULT -> 12; BattleDecision.BREACH_GATE -> 10; else -> 7 } + siegeSkill / 15
            enemyFort = (enemyFort - breach).coerceAtLeast(0)
            commandLog += "${decision?.label}: gegnerische Befestigung verliert $breach Punkte und steht bei $enemyFort %."
        }
        if (decision == BattleDecision.ARTILLERY_TARGET && devices.isNotEmpty()) {
            val destroyed = devices.maxBy { siegeDamage(it) }
            devices = devices - destroyed
            commandLog += "Gezieltes Artilleriefeuer zerstört ${destroyed.label}."
        }
        if (decision == BattleDecision.HOLD_GATE) {
            troops = troops.map { if (it.section == target && it.soldiers > 0) it.copy(morale = (it.morale - 3).coerceAtLeast(0)) else it }
            commandLog += "Die Torwache bindet den Angriff; das Halten kostet drei Moralpunkte."
        }
        if (decision == BattleDecision.OPEN_GATE) {
            commandLog += "Ein Kavallerieausfall lenkt die Belagerer ab; die Reiter riskieren zusätzliche Verluste."
        }
        val sounds = mutableListOf<BattleSoundCue>()
        if (phase == BattlePhase.RANGED) sounds += BattleSoundCue.ARROWS
        else sounds += BattleSoundCue.SWORDS
        if (original.enemyUnits.isEmpty() && original.enemy == EnemyType.TAO_TEI) sounds += BattleSoundCue.MONSTERS
        if (decision in listOf(BattleDecision.CAVALRY_CHARGE, BattleDecision.OPEN_GATE)) sounds += BattleSoundCue.HORSES
        if (decision == BattleDecision.ARTILLERY_TARGET || troops.any { it.type == UnitType.DRAGON_ARTILLERY && it.soldiers > 0 }) sounds += BattleSoundCue.ARTILLERY
        if (decision in listOf(BattleDecision.OPEN_GATE, BattleDecision.HOLD_GATE)) sounds += BattleSoundCue.GATE
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
            if (SiegeDevice.TUNNEL in devices) commandLog += "Tunneltrupps untergraben das Fundament; Verstärkung des Tores schützt die Mauer nicht vollständig."
            val damage =
                devices
                    .map {
                        siegeDamage(it)
                    }
                    .sum()
            integrity =
                (integrity - max(0, damage - WarEngine.effectiveLevel(state, BuildingType.TOWER) - (if (SiegeDevice.TUNNEL in devices) 0 else state.war.gateReinforcement / 5) -
                    if (decision == BattleDecision.HOLD_GATE) 4 else if (decision == BattleDecision.OPEN_GATE) 2 else 0)).coerceAtLeast(
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
                        troops[it].section == front.section && troops[it].soldiers > 0 && !troops[it].routed
                    }
                val defenders = indices.map { troops[it] }
                val count = defenders.sumOf { it.soldiers }
                val focus = target == front.section
                val wall =
                    if (original.tactic == Tactic.FORTIFY)
                        integrity / 100.0 *
                            (WarEngine.effectiveLevel(state, BuildingType.WALL) * 0.08 +
                                WarEngine.effectiveLevel(state, BuildingType.TOWER) * 0.05)
                    else 0.0
                val ownPower =
                    defenders.sumOf { combatPower(state, it, phase) } *
                        tacticBonus(original.tactic, phase, front.section) *
                        (1.0 + wall) *
                        (if (state.resources.food == 0) 0.70 else 1.0) *
                        when {
                            focus && decision in listOf(BattleDecision.FOCUS_ARCHERS, BattleDecision.ARROW_VOLLEY, BattleDecision.FOCUS_FIRE) ->
                                if (defenders.any { it.type.ranged >= 8 })
                                    1.20 + state.player.tactics / 600.0
                                else 1.0
                            focus && decision == BattleDecision.CAVALRY_CHARGE ->
                                if (troops.any { it.type == UnitType.KNIGHT && it.soldiers > 0 })
                                    1.28
                                else 1.0
                            focus && decision == BattleDecision.HOLD ->
                                1.0 + state.player.tactics / 500.0
                            focus && decision == BattleDecision.STRENGTHEN_SECTION -> 1.12
                            focus && decision == BattleDecision.HOLD_GATE -> 1.08
                            focus && decision == BattleDecision.OPEN_GATE -> 1.25
                            focus && decision == BattleDecision.ADVANCE -> 1.15
                            focus && decision == BattleDecision.FEIGNED_RETREAT -> 1.30
                            else -> 1.0
                        }
                val enemyQuality = if (original.enemyUnits.isNotEmpty()) original.enemyUnits.sumOf { u -> u.amount.toDouble() * (if (phase == BattlePhase.RANGED) u.type.ranged * 2.0 + u.type.defense * 0.3 else u.type.attack + u.type.defense + u.type.ranged * 0.35) } / original.enemyStart.coerceAtLeast(1) else
                    when (original.enemy) {
                        EnemyType.ORC -> 13.0
                        EnemyType.URUK -> 21.0
                        EnemyType.TAO_TEI -> 19.0
                    }
                val enemyPower = front.enemySoldiers * enemyQuality * (0.5 + front.morale / 160.0) * (1.0 + enemyFort / 200.0) * (1.0 + original.enemyExperience.coerceAtMost(100) / 200.0)
                val pressure = enemyPower / ownPower.coerceAtLeast(1.0)
                val ranged = phase == BattlePhase.RANGED
                val lossRate =
                    (if (front.enemySoldiers == 0) 0.0 else if (ranged) 0.013 else 0.022) *
                        pressure.coerceIn(0.25, 4.5) *
                        rng.nextDouble(0.8, 1.2) *
                        (if (focus && decision in listOf(BattleDecision.RETREAT_LINE, BattleDecision.FEIGNED_RETREAT)) 0.55 else 1.0) *
                        (if (focus && decision == BattleDecision.ADVANCE) 1.15 else 1.0) *
                        (if (focus && decision == BattleDecision.CAVALRY_CHARGE) 1.3 else 1.0) *
                        (if (focus && decision == BattleDecision.STRENGTHEN_SECTION) 0.85 else 1.0)
                indices.forEach { i ->
                    val c = troops[i]
                    val defense = (0.65 + c.type.defense / 20.0) * (0.55 + c.equipment / 200.0)
                    val sortieRisk = if (focus && decision == BattleDecision.OPEN_GATE && c.type == UnitType.KNIGHT) 1.6 else 1.0
                    val loss = ceil(c.soldiers * lossRate * sortieRisk / defense).toInt().coerceIn(0, c.soldiers)
                    losses += UnitAllocation(c.type, loss)
                    c.commanderId?.let { id ->
                        val key = id to c.type
                        commandedLosses[key] = (commandedLosses[key] ?: 0) + loss
                    }
                    val leader =
                        if (c.commanderId == null) state.player.leadership
                        else
                            state.commanders.firstOrNull { it.id == c.commanderId }?.leadership ?: 0
                    val brokenNeighbours = original.fronts.count { it.section != front.section && it.position < 25 }
                    val moraleDelta = (if (pressure > 1.4) -4 else if (pressure < 0.7) 2 else -1) - brokenNeighbours * 2 -
                        (if (c.commanderWounded && !c.commanderRescued) 3 else 0) -
                        (if (loss > c.soldiers / 12) 4 else 0) + if (c.experience >= 60) 1 else 0
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
                        rng.nextDouble(0.8, 1.2) /
                        (if (original.enemyUnits.isEmpty()) 1.0 else (0.65 + original.enemyUnits.sumOf { it.amount.toDouble() * it.type.defense } / original.enemyStart.coerceAtLeast(1) / 20.0))
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
            val danger = 0.20 + original.fronts.count { it.position < 40 } * 0.08
            if (exposed.isNotEmpty() && rng.nextDouble() < danger.coerceAtMost(0.55)) {
                val woundedId = changedTroops[exposed[rng.nextInt(exposed.size)]].commanderId
                changedTroops.indices.forEach { i ->
                    if (changedTroops[i].commanderId == woundedId)
                        changedTroops[i] = changedTroops[i].copy(commanderWounded = true)
                }
                val message = "${state.commanders.firstOrNull { it.id == woundedId }?.name ?: "Ein Kommandant"} ist verwundet; sein Kommando verliert an Wirkung."
                commandLog += message
                commanderEvents += message
            }
        }
        val routedTroops = changedTroops.map { c ->
            if (c.soldiers > 0 && !c.routed && c.morale < 15) {
                commandLog += "${c.type.label} fliehen aus ${c.section.label}; ${c.soldiers} Soldaten bleiben am Leben, kämpfen aber nicht mehr."
                c.copy(routed = true)
            } else c
        }
        var session =
            original.copy(
                minute = minute,
                step = original.step + 1,
                phase = phase,
                contingents = routedTroops,
                fronts = fronts,
                devices = devices,
                enemyFortification = enemyFort,
                wallIntegrity = integrity,
                pendingEvent = null,
                lastSounds = sounds.distinct(),
                commanderEvents = commanderEvents,
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
                    resources = state.resources.copy(wood = state.resources.wood - siegeCost.wood, iron = state.resources.iron - siegeCost.iron),
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
        val ownRatio = session.fightingRemaining.toDouble() / session.ownStart.coerceAtLeast(1)
        val enemyRatio = session.enemyRemaining.toDouble() / session.enemyStart
        val victory =
            session.enemyRemaining == 0 || enemyRatio < 0.28 || (minute >= 35 && enemyMorale < 25)
        val defeat =
            session.fightingRemaining == 0 ||
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

    fun assaultCost(decision: BattleDecision): Resources = when (decision) {
        BattleDecision.SCALE_WALL -> Resources(0, 0, 100, 0, 0)
        BattleDecision.BREACH_GATE -> Resources(0, 0, 120, 0, 20)
        BattleDecision.TOWER_ASSAULT -> Resources(0, 0, 200, 0, 30)
        BattleDecision.UNDERMINE -> Resources(0, 0, 100, 0, 40)
        else -> Resources(0, 0, 0, 0, 0)
    }

    fun orderCost(session: BattleSession, decision: BattleDecision): Int = when (decision) {
        BattleDecision.HOLD, BattleDecision.HOLD_FORMATION -> 0
        BattleDecision.RETREAT_LINE, BattleDecision.HOLD_GATE -> 2
        BattleDecision.SEND_RESERVE, BattleDecision.RELOCATE_RESERVE, BattleDecision.STRENGTHEN_SECTION -> if (session.reservePerk) 2 else 4
        BattleDecision.FOCUS_ARCHERS, BattleDecision.ARROW_VOLLEY, BattleDecision.ADVANCE -> 4
        BattleDecision.FOCUS_FIRE, BattleDecision.ARTILLERY_TARGET, BattleDecision.RESCUE_COMMANDER -> 5
        BattleDecision.CAVALRY_CHARGE, BattleDecision.OPEN_GATE, BattleDecision.ORDERED_RETREAT, BattleDecision.PURSUE -> 6
        BattleDecision.RALLY, BattleDecision.FEIGNED_RETREAT, BattleDecision.UNDERMINE, BattleDecision.TOWER_ASSAULT -> 7
        BattleDecision.SCALE_WALL, BattleDecision.BREACH_GATE -> 5
    }

    fun commandRegeneration(state: GameState, session: BattleSession): Int {
        val commanderLeadership = session.contingents.filter { it.soldiers > 0 && !it.commanderWounded }.mapNotNull { c -> c.commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.leadership } }.average().takeUnless { it.isNaN() } ?: 0.0
        val chaos = if (session.phase == BattlePhase.CRITICAL || session.morale < 35) 2 else 0
        return (2 + state.player.leadership / 30 + state.player.tactics / 40 + commanderLeadership.toInt() / 50 + (if (session.morale >= 80) 1 else 0) + (if (session.reservePerk) 1 else 0) - chaos).coerceIn(1, 8)
    }

    /** Proactive orders are legal between exchanges and use the same event/decision path. */
    fun order(state: GameState, decision: BattleDecision, section: BattleSection): GameEngine.ActionResult {
        val session = state.battleSession ?: return GameEngine.ActionResult(state, "Keine Schlacht aktiv.")
        if (!session.isActive || session.status == BattleStatus.PURSUIT || session.pendingEvent != null || section == BattleSection.RESERVE)
            return GameEngine.ActionResult(state, "Beantworte zuerst das Schlachtereignis oder wähle einen Frontabschnitt.")
        if (decision in listOf(BattleDecision.PURSUE, BattleDecision.HOLD_FORMATION) || !canOrder(session, decision, section))
            return GameEngine.ActionResult(state, "Befehl an diesem Abschnitt nicht verfügbar.")
        val commanded = state.copy(battleSession = session.copy(pendingEvent = BattleEvent("Feldbefehl", decision.label, section, listOf(decision))))
        val result = advance(commanded, decision)
        return if (result.state == commanded) GameEngine.ActionResult(state, result.message) else result
    }

    fun terrainMultiplier(terrain: BattleTerrain, type: UnitType, phase: BattlePhase): Double {
        val ranged = type.ranged >= 8 && phase == BattlePhase.RANGED
        val cavalry = type == UnitType.KNIGHT
        return when (terrain) {
            BattleTerrain.PLAIN -> if (cavalry) 1.15 else 1.0
            BattleTerrain.FOREST -> when { type.culture == Culture.WOOD_ELF -> 1.20; cavalry -> 0.55; type == UnitType.DRAGON_ARTILLERY -> 0.65; else -> 1.0 }
            BattleTerrain.HILL -> if (ranged) 1.25 else if (cavalry) 0.85 else 1.05
            BattleTerrain.RIVER -> if (cavalry) 0.65 else if (type.defense >= 10) 1.18 else 1.05
            BattleTerrain.BRIDGE, BattleTerrain.PASS -> if (cavalry) 0.60 else if (type.defense >= 10) 1.22 else 1.08
            BattleTerrain.MUD -> if (cavalry || type == UnitType.DRAGON_ARTILLERY) 0.60 else 0.90
            BattleTerrain.WALL -> if (ranged) 1.22 else if (cavalry) 0.60 else 1.12
            BattleTerrain.STREET -> if (cavalry) 0.70 else if (ranged) 0.90 else 1.05
        }
    }

    fun visualGroups(session: BattleSession): List<VisualBattleGroup> {
        val rows = session.contingents.filter { it.soldiers > 0 }.groupBy { Triple(it.section, it.type, it.routed) }.map { (key, troops) -> VisualBattleGroup(key.first, key.second, troops.sumOf { it.soldiers }, false, key.third, troops.firstOrNull { it.commanderId != null }?.commanderId) } + session.fronts.filter { it.enemySoldiers > 0 }.map { VisualBattleGroup(it.section, null, it.enemySoldiers, true, it.morale < 15) }
        val total = rows.sumOf { it.soldiers.toLong() }.coerceAtLeast(1)
        val target = minOf(300, maxOf(rows.size, (total / 1000).toInt().coerceIn(50, 300)))
        val extraBudget = (target - rows.size).coerceAtLeast(0)
        return rows.flatMap { row ->
            val groups = minOf(row.soldiers, 1 + (extraBudget.toLong() * row.soldiers / total).toInt())
            List(groups) { index -> row.copy(soldiers = row.soldiers / groups + if (index < row.soldiers % groups) 1 else 0) }
        }
    }

    /** Returns replay state only; campaign rewards are never written into the caller's state. */
    fun replay(record: BattleRecord, exchanges: Int = record.inputs.size): BattleSession? {
        val context = record.replay ?: return null
        val cp = (20 + context.player.leadership / 10).coerceAtMost(35)
        var replayState =
            GameState(
                player = context.player,
                day = context.day,
                commanders = context.commanders,
                realm = context.realm,
                resources = context.resources,
                armyPools = context.armyPools,
                population = context.population,
                commanderAssignments = context.assignments,
                settings = GameSettings(permadeath = context.permadeath),
                doctrine = context.doctrine,
                frontier = context.frontier,
                companion = context.companion,
                war =
                    WarState(
                        equipment = context.equipment,
                        buildingDamage = context.buildingDamage,
                        gateReinforcement = context.gateReinforcement,
                        eliteUnits = context.eliteUnits,
                    ),
                battleSession =
                    BattleSession(
                        record.enemy,
                        record.tactic,
                        record.seed,
                        record.ownStart,
                        record.enemyStart,
                        context.initialContingents,
                        context.initialFronts,
                        devices = context.devices,
                        wallIntegrity = context.realm.wallIntegrity,
                        terrain = record.terrain,
                        participation = record.participation,
                        replayStart = context,
                        commandPoints = cp,
                        maxCommandPoints = cp,
                        heroPerk = context.heroPerk,
                        reservePerk = context.reservePerk,
                        feignedRetreatPerk = context.feignedRetreatPerk,
                        rallyPerk = context.rallyPerk,
                        moraleBonus = context.moraleBonus,
                        rangedWeather = context.rangedWeather,
                        cavalryWeather = context.cavalryWeather,
                        seasonPenalty = context.seasonPenalty,
                        healingBonus = context.healingBonus,
                        enemyFactionName = record.enemyFactionName,
                        enemyUnits = record.enemyUnits,
                        location = context.location,
                        enemyFactionId = record.enemyFactionId,
                        enemyArmyName = record.enemyArmyName,
                        enemyFortification = context.enemyFortification,
                        deployedMorale = context.deployedMorale,
                        enemyExperience = context.enemyExperience,
                        rangedSupplyFactor = context.rangedSupplyFactor,
                    ),
            )
        replayState = FrontierEngine.prepareBattle(replayState)
        if (record.ownStart == 0) return finish(replayState, replayState.battleSession!!, false).battleSession
        record.inputs.take(exchanges.coerceIn(0, record.inputs.size)).forEach { input ->
            val current = replayState.battleSession ?: return null
            if (!current.isActive) return null
            replayState = replayState.copy(battleSession = current.copy(pendingEvent = input.event))
            val result = advance(replayState, input.decision)
            if (result.state == replayState) return null
            replayState = result.state
        }
        return replayState.battleSession
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
        if (c.routed) return 0.0
        val session = state.battleSession
        val terrain = session?.terrain?.get(c.section) ?: BattleTerrain.PLAIN
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
                if (c.commanderRescued) 0.92 else if (c.commanderWounded) 0.80 else 1.0
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
            fortress *
            FrontierEngine.customUnitPowerFactor(state, c.type) *
            (if (c.commanderId == COMPANION_COMMANDER_ID) FrontierEngine.bondCombatFactor(state) else 1.0) *
            terrainMultiplier(terrain, c.type, phase) *
            (state.war.equipment.firstOrNull { it.type == c.type }?.quality?.power ?: 1.0) *
            (if (c.type.ranged >= 8 && phase == BattlePhase.RANGED) session?.rangedWeather ?: 1.0 else 1.0) *
            (if (c.type == UnitType.KNIGHT) session?.cavalryWeather ?: 1.0 else 1.0) *
            (session?.seasonPenalty ?: 1.0) *
            (if (session?.participation == BattleParticipation.PERSONAL && c.commanderId == null) if (session.heroPerk) 1.22 else 1.10 else 1.0) *
            (if (state.war.eliteUnits.any { it.type == c.type && "Unbeugsam" in it.traits }) 1.05 else 1.0) *
            DoctrineEngine.equipmentPowerFactor(state) *
            (if (phase == BattlePhase.RANGED && c.type.ranged >= 8)
                DoctrineEngine.rangedPowerFactor(state) * (session?.rangedSupplyFactor ?: 1.0)
             else 1.0) *
            (if (state.doctrine == MilitaryDoctrine.FOREST_WARFARE && terrain == BattleTerrain.FOREST)
                1.12 else 1.0) *
            (if (
                state.doctrine == MilitaryDoctrine.DISCIPLINED_LINE &&
                    c.type != UnitType.KNIGHT &&
                    phase != BattlePhase.RANGED
            ) 1.05 else 1.0)
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
        val woundedSection = session.contingents.firstOrNull { it.soldiers > 0 && it.commanderWounded && !it.commanderRescued && it.section != BattleSection.RESERVE }?.section
        val front = session.fronts.firstOrNull { it.section == woundedSection }
            ?: session.fronts.filter { it.enemySoldiers > 0 || session.fighting(it.section) > 0 }.ifEmpty { session.fronts }.minBy { it.position + session.soldiers(it.section) / 20 }
        val commanders = session.contingents.filter { it.commanderId != null && it.soldiers > 0 }
        val (title, text) =
            when {
                woundedSection != null -> "Verwundeter Kommandant" to
                    "Ein verwundeter Anführer braucht zwanzig Reservisten zur Bergung. Die Rettung stellt einen Teil seiner Führungswirkung wieder her."
                session.tactic == Tactic.FORTIFY && session.wallIntegrity < 45 ->
                    "Das Tor droht zu brechen" to
                        "Die Belagerungsgeräte setzen dem Tor zu. Entlaste die Verteidiger."
                session.enemyUnits.isEmpty() && session.enemy == EnemyType.TAO_TEI && session.minute == 20 ->
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
        if (session.fighting(BattleSection.RESERVE) > 0) {
            options += BattleDecision.SEND_RESERVE
            options += BattleDecision.RELOCATE_RESERVE
            options += BattleDecision.STRENGTHEN_SECTION
        }
        options += BattleDecision.ORDERED_RETREAT
        listOf(BattleDecision.ARTILLERY_TARGET, BattleDecision.HOLD_GATE, BattleDecision.OPEN_GATE, BattleDecision.RESCUE_COMMANDER).forEach {
            if (canOrder(session, it, front.section)) options += it
        }
        if (
            session.contingents.any {
                it.section == front.section && it.soldiers > 0 && it.type.ranged >= 8
            }
        )
            options += BattleDecision.FOCUS_ARCHERS
        if (session.contingents.any { it.soldiers > 0 && it.type == UnitType.KNIGHT })
            options += BattleDecision.CAVALRY_CHARGE
        listOf(BattleDecision.SCALE_WALL, BattleDecision.BREACH_GATE, BattleDecision.TOWER_ASSAULT, BattleDecision.UNDERMINE).filter { canOrder(session, it, front.section) }.forEach { options += it }
        if (session.rallyPerk) options += BattleDecision.RALLY
        if (session.feignedRetreatPerk) options += BattleDecision.FEIGNED_RETREAT
        return BattleEvent(title, text, front.section, options)
    }

    fun canOrder(session: BattleSession, decision: BattleDecision, section: BattleSection?): Boolean = when (decision) {
        BattleDecision.SCALE_WALL, BattleDecision.BREACH_GATE, BattleDecision.TOWER_ASSAULT, BattleDecision.UNDERMINE -> session.enemyFortification > 0 && session.contingents.any { it.soldiers >= 50 && !it.routed && it.type.ranged < 8 }
        BattleDecision.RALLY -> session.rallyPerk && section != null && session.soldiers(section) > 0
        BattleDecision.FEIGNED_RETREAT -> session.feignedRetreatPerk && section != null && session.soldiers(section) > 0
        BattleDecision.ARROW_VOLLEY, BattleDecision.FOCUS_FIRE, BattleDecision.FOCUS_ARCHERS -> session.contingents.any { it.section == section && it.soldiers > 0 && it.type.ranged >= 8 && !it.routed }
        BattleDecision.CAVALRY_CHARGE -> session.contingents.any { it.soldiers > 0 && it.type == UnitType.KNIGHT && !it.routed }
        BattleDecision.ADVANCE -> section != null && session.contingents.any { it.section == section && it.soldiers > 0 && !it.routed }
        BattleDecision.SEND_RESERVE, BattleDecision.RELOCATE_RESERVE, BattleDecision.STRENGTHEN_SECTION -> session.fighting(BattleSection.RESERVE) > 0 && section != null && section != BattleSection.RESERVE
        BattleDecision.ARTILLERY_TARGET -> (session.enemyFortification > 0 || session.tactic == Tactic.FORTIFY && session.devices.isNotEmpty()) && session.contingents.any { it.type == UnitType.DRAGON_ARTILLERY && it.soldiers > 0 }
        BattleDecision.HOLD_GATE -> session.tactic == Tactic.FORTIFY && session.wallIntegrity > 0 && session.devices.isNotEmpty() && section != null && session.soldiers(section) > 0
        BattleDecision.OPEN_GATE -> session.tactic == Tactic.FORTIFY && session.wallIntegrity > 0 && session.devices.isNotEmpty() && session.contingents.any { it.type == UnitType.KNIGHT && it.soldiers > 0 && !it.routed }
        BattleDecision.RESCUE_COMMANDER -> session.fighting(BattleSection.RESERVE) >= 20 && session.contingents.any { it.commanderId != null && it.section == section && it.soldiers > 0 && it.commanderWounded && !it.commanderRescued }
        else -> true
    }

    private fun siegeDamage(device: SiegeDevice): Int = when (device) {
        SiegeDevice.RAM -> 4
        SiegeDevice.TOWER -> 3
        SiegeDevice.LADDERS -> 1
        SiegeDevice.CATAPULT -> 5
        SiegeDevice.CLIMBERS -> 2
        SiegeDevice.TUNNEL -> 4
    }

    /** An early withdrawal applies a small final exchange and ends the battle immediately. */
    private fun resolveRetreat(state: GameState, original: BattleSession): GameEngine.ActionResult {
        val rng = Random(original.seed xor ((original.step + 1) * 104729))
        val losses = original.contingents.map { c ->
            UnitAllocation(c.type, if (c.section == BattleSection.RESERVE) 0 else ceil(c.soldiers * rng.nextDouble(0.003, 0.010)).toInt().coerceAtMost(c.soldiers))
        }
        val commandedLosses = mutableMapOf<Pair<Long, UnitType>, Int>()
        val troops = original.contingents.mapIndexed { index, c ->
            val loss = losses[index].amount
            c.commanderId?.let { id -> val key = id to c.type; commandedLosses[key] = (commandedLosses[key] ?: 0) + loss }
            c.copy(soldiers = c.soldiers - loss)
        }
        val session = original.copy(
            minute = original.minute + 5, step = original.step + 1, contingents = troops,
            orderedRetreat = true, pendingEvent = null,
            log = original.log + BattleLogEntry(original.minute + 5, "Geordneter Rückzug: Das Heer verlässt die Schlacht, die Reserve deckt den Rückzug.", losses.sumOf { it.amount }),
        )
        val next = finish(ArmyEngine.applyLosses(reduceAssignments(state, commandedLosses), losses).copy(battleSession = session), session, false)
        return GameEngine.ActionResult(next, "Geordneter Rückzug. ${losses.sumOf { it.amount }} zusätzliche Verluste; das übrige Heer kehrt zurück.")
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
        val wear = if (session.minute == 0) 0 else (3 + session.minute / 10 - WarEngine.effectiveLevel(state, BuildingType.ARSENAL).coerceAtMost(5)).coerceIn(1, 15)
        val xp = if (victory) 90 + session.enemyStart / 8 else 30
        val renown = if (victory) max(15, session.enemyStart / 12) else 4
        val worn = session.contingents.map { it.copy(equipment = (it.equipment - wear).coerceAtLeast(0)) }
        val equipmentDamage = if (session.ownRemaining == 0) 0 else
            session.contingents.zip(worn).sumOf { (before, after) -> before.soldiers.toLong() * (before.equipment - after.equipment) }.div(session.ownRemaining).toInt()
        val final =
            session.copy(
                status = if (victory) BattleStatus.VICTORY else BattleStatus.DEFEAT,
                pendingEvent = null,
                phase = BattlePhase.PURSUIT,
                lastSounds = listOf(BattleSoundCue.HORN),
                contingents = worn,
                xpReward = xp,
                renownReward = renown,
                equipmentDamage = equipmentDamage,
                lootFood = if (victory) session.enemyStart / 8 else 0,
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
                        equipment =
                            ((pool.equipment.toLong() * (pool.soldiers - participants) +
                                worn.filter { it.type == pool.type }.sumOf { it.soldiers.toLong() * it.equipment }) / pool.soldiers)
                                .toInt().coerceIn(0, 100),
                    )
            }
        val result =
            state.copy(
                battleSession = final,
                armyPools = pools,
                frontier = state.frontier.copy(designs = state.frontier.designs.map { design ->
                    val losses = session.contingents.filter { it.designId == design.id }.sumOf { (it.startSoldiers - it.soldiers).coerceAtLeast(0) }
                    if (losses == 0) design else design.copy(soldiers = (design.soldiers - losses).coerceAtLeast(0))
                }, weapons = if (session.tactic != Tactic.FORTIFY) state.frontier.weapons else state.frontier.weapons.map { stock ->
                    if (stock.count == 0 || stock.ammunition <= 0) stock else stock.copy(ammunition = (stock.ammunition - 1).coerceAtLeast(0), integrity = (stock.integrity - 4).coerceAtLeast(20))
                }),
                commanders = state.commanders.map { commander ->
                    val deployed = session.contingents.filter { it.commanderId == commander.id && it.startSoldiers > 0 }
                    if (deployed.isEmpty()) commander else commander.copy(
                        battlesFought = commander.battlesFought + 1,
                        victories = commander.victories + if (victory) 1 else 0,
                        casualties = commander.casualties + deployed.sumOf { it.startSoldiers - it.soldiers },
                    )
                },
                resources =
                    EconomyEngine.add(
                        state.resources,
                        Resources(
                            if (victory) final.lootGold else 0,
                            final.lootFood,
                            0,
                            0,
                            0,
                        ),
                    ),
                victories = state.victories + if (victory) 1 else 0,
                defeats = state.defeats + if (victory) 0 else 1,
                renown = state.renown + renown,
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
                                "${session.enemyFactionName ?: session.enemyArmyName ?: session.enemy.label}: ${session.ownStart} eigene zu Beginn, ${session.ownRemaining} Überlebende, ${session.ownStart - session.ownRemaining} eigene und ${session.enemyStart - session.enemyRemaining} feindliche Verluste. +$xp XP, +$renown Ruhm; ${final.lootGold} Gold, ${final.lootFood} Nahrung. Moral ${session.morale} %, Verschleiß $equipmentDamage Punkte. ${session.commanderEvents.joinToString(" ")}",
                            ))
                        .takeLast(2000),
            )
        val recorded = FrontierEngine.afterBattle(WarEngine.recordOutcome(result, final, victory), final)
        val career = WorldEngine.reconcileBattle(CharacterEngine.recordBattle(recorded, session.contingents.mapNotNull { it.commanderId }.distinct(), victory, session.ownStart - session.ownRemaining, session.ownStart < session.enemyStart))
        return DynastyEngine.resolveBattleSuccession(ProgressionEngine.update(
            ProgressionEngine.awardXp(
                RelationshipEngine.onEvent(
                    ArmyEngine.clampAssignments(career),
                    if (victory) "feast" else "defeat",
                ),
                xp,
            )
        ))
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
