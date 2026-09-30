package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object GameEngine {

    data class ActionResult(val state: GameState, val message: String)
    data class BattleResult(val state: GameState, val headline: String, val details: String, val victory: Boolean)

    data class BattleFrame(
        val minute: Int,
        val ownRemaining: Int,
        val enemyRemaining: Int,
        val ownLossesThisFrame: Int,
        val enemyLossesThisFrame: Int,
        val front: Int,
        val event: String
    )

    data class LiveBattleResult(
        val finalState: GameState,
        val victory: Boolean,
        val enemyStart: Int,
        val ownStart: Int,
        val frames: List<BattleFrame>,
        val headline: String
    )

    fun newGame(name: String, age: Int, species: Species, portraitUri: String?): GameState {
        val population = when (species) {
            Species.HUMAN -> Population(human = 250, woodElf = 0, goldElf = 0, wall = 30, humanRecruits = 55, woodElfRecruits = 0, goldElfRecruits = 0, wallRecruits = 8)
            Species.ELF -> Population(human = 0, woodElf = 180, goldElf = 60, wall = 0, humanRecruits = 0, woodElfRecruits = 35, goldElfRecruits = 12, wallRecruits = 0)
            Species.HALF_ELF -> Population()
        }
        val profile = CharacterProfile(name = name.ifBlank { "Leon" }, age = age.coerceIn(18, 80), species = species, portraitUri = portraitUri)
        return GameState(player = profile, population = population)
    }

    fun isUnitUnlocked(state: GameState, type: UnitType): Boolean {
        return when (state.player.species) {
            Species.HALF_ELF -> true
            Species.HUMAN -> type.culture in setOf(Culture.HUMAN, Culture.WALL) ||
                    (state.renown >= 900 && type.culture in setOf(Culture.WOOD_ELF, Culture.GOLD_ELF))
            Species.ELF -> type.culture in setOf(Culture.WOOD_ELF, Culture.GOLD_ELF) ||
                    (state.renown >= 700 && type.culture in setOf(Culture.HUMAN, Culture.WALL))
        }
    }

    fun advanceDay(state: GameState): ActionResult {
        val farm = state.realm.level(BuildingType.FARM)
        val saw = state.realm.level(BuildingType.SAWMILL)
        val quarry = state.realm.level(BuildingType.QUARRY)
        val ironworks = state.realm.level(BuildingType.IRONWORKS)
        val market = state.realm.level(BuildingType.MARKET)
        val territory = state.realm.territory

        var resources = state.resources.copy(
            gold = state.resources.gold + 18 + market * 16 + territory * 10,
            food = state.resources.food + 28 + farm * 35 + territory * 12 - max(0, state.armySize / 40),
            wood = state.resources.wood + 10 + saw * 28,
            stone = state.resources.stone + 4 + quarry * 30 + territory * 3,
            iron = state.resources.iron + 4 + ironworks * 18
        )
        resources = resources.copy(food = max(0, resources.food))

        var population = state.population
        if ((state.day + 1) % 7 == 0) {
            val growth = 1 + state.realm.territory
            population = population.copy(
                human = population.human + if (population.human > 0) 3 * growth else 0,
                woodElf = population.woodElf + if (population.woodElf > 0) growth else 0,
                goldElf = population.goldElf + if (population.goldElf > 0 && (state.day + 1) % 21 == 0) 1 else 0,
                wall = population.wall + if (population.wall > 0) 2 * growth else 0,
                humanRecruits = population.humanRecruits + if (population.human > 0) 2 * growth else 0,
                woodElfRecruits = population.woodElfRecruits + if (population.woodElf > 0) growth else 0,
                goldElfRecruits = population.goldElfRecruits + if (population.goldElf > 0 && (state.day + 1) % 14 == 0) 1 else 0,
                wallRecruits = population.wallRecruits + if (population.wall > 0) growth else 0
            )
        }

        val finished = state.trainingQueue.filter { it.daysRemaining <= 1 }
        val remaining = state.trainingQueue.filter { it.daysRemaining > 1 }.map { it.copy(daysRemaining = it.daysRemaining - 1) }
        val newRegiments = finished.map { order ->
            Regiment(
                id = System.nanoTime() + order.id,
                name = defaultRegimentName(order.type, state.regiments.count { it.type == order.type } + 1),
                type = order.type,
                soldiers = order.amount,
                maxSoldiers = order.amount,
                morale = 72 + min(18, state.realm.level(BuildingType.BARRACKS) * 3)
            )
        }

        var companion = state.companion
        var chronicle = state.chronicle
        if (!companion.met && state.day + 1 >= 5) {
            companion = companion.copy(met = true)
            chronicle = chronicle + ChronicleEntry(
                state.day + 1,
                "Eine Soldatin schließt sich an",
                "${companion.name} kämpft bei einem Überfall an deiner Seite und entscheidet sich danach, mit dir weiterzuziehen."
            )
        }

        if (finished.isNotEmpty()) {
            chronicle = chronicle + ChronicleEntry(
                state.day + 1,
                "Ausbildung abgeschlossen",
                finished.joinToString { "${it.amount} ${it.type.label}" } + " stehen nun unter Waffen."
            )
        }

        // Deterministic realm events keep long campaigns alive without requiring online content.
        when ((state.day + 1) % 52) {
            9 -> {
                resources = resources.copy(gold = resources.gold + 180)
                chronicle = chronicle + ChronicleEntry(state.day + 1, "Händlerkarawane", "Eine große Karawane erreicht ${state.realm.settlementName}. Der Handel bringt 180 Gold.")
            }
            13 -> {
                resources = resources.copy(stone = resources.stone + 140)
                chronicle = chronicle + ChronicleEntry(state.day + 1, "Neue Steinader", "Arbeiter entdecken hochwertiges Gestein. +140 Stein.")
            }
            17 -> {
                population = population.copy(
                    human = population.human + 24,
                    humanRecruits = population.humanRecruits + 5
                )
                chronicle = chronicle + ChronicleEntry(state.day + 1, "Flüchtlinge an den Toren", "24 Menschen erhalten Schutz. 5 davon melden sich später als Rekruten.")
            }
            21 -> {
                chronicle = chronicle + ChronicleEntry(state.day + 1, "Spähermeldung", "Feindliche Bewegung an der Grenze. Die Bedrohung steigt schneller als gewöhnlich.")
            }
        }

        val threatGain = 2 + state.realm.territory * 2 + state.victories / 3 + if ((state.day + 1) % 52 == 21) 8 else 0
        val realm = state.realm.copy(threat = min(100, state.realm.threat + threatGain))
        val next = updateProgress(
            state.copy(
                day = state.day + 1,
                resources = resources,
                population = population,
                realm = realm,
                trainingQueue = remaining,
                regiments = state.regiments + newRegiments,
                companion = companion,
                chronicle = chronicle.takeLast(80)
            )
        )
        return ActionResult(next, if (finished.isEmpty()) "Ein Tag vergeht. Das Reich produziert neue Ressourcen." else "Ausbildung abgeschlossen.")
    }

    fun recruit(state: GameState, type: UnitType, percent: Int): ActionResult {
        if (!isUnitUnlocked(state, type)) return ActionResult(state, "Diese Kultur ist für deinen Herrscher noch nicht freigeschaltet.")
        val available = state.population.recruits(type.culture)
        if (available <= 0) return ActionResult(state, "Keine passenden Rekruten verfügbar.")
        val pct = percent.coerceIn(1, 100)
        val amount = max(1, ceil(available * pct / 100.0).toInt())
        val goldCost = amount * type.goldCost
        val ironCost = amount * type.ironCost
        if (state.resources.gold < goldCost || state.resources.iron < ironCost) {
            return ActionResult(state, "Nicht genug Gold oder Eisen. Benötigt: $goldCost Gold / $ironCost Eisen.")
        }

        val population = removeRecruits(state.population, type.culture, amount)
        val order = TrainingOrder(
            id = System.nanoTime(),
            type = type,
            amount = amount,
            daysRemaining = max(2, type.trainingDays - state.realm.level(BuildingType.BARRACKS))
        )
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - goldCost, iron = state.resources.iron - ironCost),
            population = population,
            trainingQueue = state.trainingQueue + order,
            chronicle = (state.chronicle + ChronicleEntry(state.day, "Rekrutierung", "$amount ${type.label} beginnen ihre Ausbildung.")).takeLast(80)
        )
        return ActionResult(next, "$amount ${type.label} wurden der Ausbildung zugewiesen.")
    }

    fun build(state: GameState, type: BuildingType): ActionResult {
        val level = state.realm.level(type)
        val multiplier = level + 1
        val costs = when (type) {
            BuildingType.FARM -> Triple(140, 70, 20)
            BuildingType.SAWMILL -> Triple(120, 60, 15)
            BuildingType.QUARRY -> Triple(170, 80, 10)
            BuildingType.IRONWORKS -> Triple(220, 120, 80)
            BuildingType.MARKET -> Triple(280, 130, 40)
            BuildingType.BARRACKS -> Triple(300, 180, 100)
            BuildingType.WALL -> Triple(420, 360, 160)
            BuildingType.TOWER -> Triple(360, 300, 140)
            BuildingType.PALACE -> Triple(800, 600, 260)
        }
        val gold = costs.first * multiplier
        val wood = costs.second * multiplier
        val stone = costs.third * multiplier
        if (state.resources.gold < gold || state.resources.wood < wood || state.resources.stone < stone) {
            return ActionResult(state, "Nicht genug Ressourcen: $gold Gold, $wood Holz, $stone Stein.")
        }
        val buildings = state.realm.buildings.toMutableMap().apply { put(type, level + 1) }
        val nextRealm = state.realm.copy(
            buildings = buildings,
            settlementName = settlementNameFor(buildings[BuildingType.PALACE] ?: 0, state.realm.territory),
            wallIntegrity = if (type == BuildingType.WALL) 100 else state.realm.wallIntegrity
        )
        return ActionResult(
            updateProgress(
                state.copy(
                    resources = state.resources.copy(
                        gold = state.resources.gold - gold,
                        wood = state.resources.wood - wood,
                        stone = state.resources.stone - stone
                    ),
                    realm = nextRealm,
                    chronicle = (state.chronicle + ChronicleEntry(state.day, "Bauprojekt", "${type.label} erreicht Stufe ${level + 1}.")).takeLast(80)
                )
            ),
            "${type.label} wurde auf Stufe ${level + 1} ausgebaut."
        )
    }

    fun buyLand(state: GameState): ActionResult {
        val cost = 600 + state.realm.territory * 550
        if (state.resources.gold < cost) return ActionResult(state, "Du benötigst $cost Gold für das nächste Landrecht.")
        val t = state.realm.territory + 1
        var population = state.population
        if (state.player.species == Species.HALF_ELF) {
            population = population.copy(
                human = population.human + 60,
                woodElf = population.woodElf + 18,
                goldElf = population.goldElf + 4,
                wall = population.wall + 24,
                humanRecruits = population.humanRecruits + 12,
                woodElfRecruits = population.woodElfRecruits + 4,
                goldElfRecruits = population.goldElfRecruits + 1,
                wallRecruits = population.wallRecruits + 5
            )
        }
        val next = updateProgress(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold - cost),
                population = population,
                realm = state.realm.copy(territory = t, settlementName = settlementNameFor(state.realm.level(BuildingType.PALACE), t)),
                chronicle = (state.chronicle + ChronicleEntry(state.day, "Neues Land", "Du sicherst dir ein weiteres Gebiet. Dein Besitz umfasst nun $t Territorien.")).takeLast(80)
            )
        )
        return ActionResult(next, "Neues Land erworben.")
    }

    fun runMission(state: GameState, type: MissionType, randomFactor: Double = Random.nextDouble(0.85, 1.16)): ActionResult {
        val difficulty = when (type) {
            MissionType.PATROL -> 80
            MissionType.ESCORT -> 120
            MissionType.HUNT -> 180
            MissionType.RELIEF -> 250
        }
        val personalPower = state.player.sword + state.player.bow + state.player.leadership + state.armyPower / 150
        val companionPower = if (state.companion.met) (state.companion.sword + state.companion.bow + state.companion.tactics) / 2 else 0
        val success = (personalPower + companionPower) * randomFactor >= difficulty
        val reward = if (success) difficulty * 2 else difficulty / 3
        val renown = if (success) difficulty / 4 else 5
        val trust = if (state.companion.met) if (success) 2 else 1 else 0
        val next = updateProgress(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold + reward, food = state.resources.food + if (success) difficulty / 2 else 0),
                renown = state.renown + renown,
                companion = if (state.companion.met) state.companion.copy(trust = min(100, state.companion.trust + trust), respect = min(100, state.companion.respect + if (success) 2 else 0)) else state.companion,
                chronicle = (state.chronicle + ChronicleEntry(state.day, type.label, if (success) "Die Mission gelingt. +$reward Gold, +$renown Ruhm." else "Die Mission scheitert, doch du kehrst lebend zurück.")).takeLast(80)
            )
        )
        return ActionResult(next, if (success) "Mission erfolgreich." else "Mission gescheitert.")
    }

    fun companionAction(state: GameState, action: String): ActionResult {
        if (!state.companion.met) return ActionResult(state, "Ihr habt euch noch nicht getroffen.")
        var c = state.companion
        val message = when (action) {
            "talk" -> {
                c = c.copy(trust = min(100, c.trust + 3), affection = min(100, c.affection + 2))
                "Ihr verbringt den Abend im Gespräch."
            }
            "train" -> {
                c = c.copy(
                    level = c.level + 1,
                    sword = min(100, c.sword + 2),
                    bow = min(100, c.bow + 2),
                    respect = min(100, c.respect + 3)
                )
                "Ihr trainiert gemeinsam."
            }
            "command" -> {
                c = c.copy(
                    leadership = min(100, c.leadership + 2),
                    tactics = min(100, c.tactics + 2),
                    trust = min(100, c.trust + 2),
                    role = if (c.leadership >= 65) "Mitregentin & Generalin" else "Kommandantin"
                )
                "Du überträgst ihr Verantwortung über eigene Truppen."
            }
            "court" -> {
                c = c.copy(diplomacy = min(100, c.diplomacy + 2), affection = min(100, c.affection + 2), role = if (c.affection >= 70 && c.trust >= 70) "Mitregentin" else c.role)
                "Ihr trefft Entscheidungen für das gemeinsame Reich."
            }
            else -> return ActionResult(state, "Unbekannte Aktion.")
        }
        return ActionResult(
            state.copy(companion = c, chronicle = (state.chronicle + ChronicleEntry(state.day, "Gemeinsame Zeit", message)).takeLast(80)),
            message
        )
    }

    fun updateCompanionIdentity(state: GameState, name: String, portraitUri: String?): GameState =
        state.copy(companion = state.companion.copy(name = name.ifBlank { state.companion.name }, portraitUri = portraitUri ?: state.companion.portraitUri))

    fun updatePlayerPortrait(state: GameState, portraitUri: String?): GameState =
        state.copy(player = state.player.copy(portraitUri = portraitUri))

    fun markTutorialSeen(state: GameState): GameState =
        state.copy(tutorialSeen = true)

    fun assignCommander(state: GameState, commanderId: Long, regimentId: Long): ActionResult {
        val commander = state.commanders.firstOrNull { it.id == commanderId }
            ?: return ActionResult(state, "Kommandant nicht gefunden.")
        val regiment = state.regiments.firstOrNull { it.id == regimentId }
            ?: return ActionResult(state, "Regiment nicht gefunden.")
        if (commander.culture != regiment.type.culture) {
            return ActionResult(state, "${commander.name} kann aktuell nur Regimenter der eigenen Kultur führen.")
        }
        val cleared = state.regiments.map {
            if (it.commanderId == commanderId) it.copy(commanderId = null) else it
        }
        val updated = cleared.map {
            if (it.id == regimentId) it.copy(commanderId = commanderId) else it
        }
        return ActionResult(
            state.copy(regiments = updated),
            "${commander.name} übernimmt ${regiment.name}."
        )
    }


    fun setCommanderAllocation(
        state: GameState,
        commanderId: Long,
        requested: List<UnitAllocation>
    ): ActionResult {
        val commander = state.commanders.firstOrNull { it.id == commanderId }
            ?: return ActionResult(state, "Kommandant nicht gefunden.")

        val normalized = requested
            .filter { it.amount > 0 && it.type.culture == commander.culture }
            .groupBy { it.type }
            .map { (type, rows) -> UnitAllocation(type, rows.sumOf { it.amount }) }

        val otherAssignments = state.commanderAssignments.filterNot { it.commanderId == commanderId }
        for (entry in normalized) {
            val total = state.soldiers(entry.type)
            val usedByOthers = otherAssignments
                .flatMap { it.units }
                .filter { it.type == entry.type }
                .sumOf { it.amount }
            val maxAvailable = (total - usedByOthers).coerceAtLeast(0)
            if (entry.amount > maxAvailable) {
                return ActionResult(
                    state,
                    "Von ${entry.type.label} sind nur $maxAvailable Soldaten frei verfügbar."
                )
            }
        }

        val updated = otherAssignments + CommanderAssignment(commanderId, normalized)
        return ActionResult(
            state.copy(commanderAssignments = updated),
            "${commander.name} führt jetzt ${normalized.sumOf { it.amount }} Soldaten."
        )
    }

    fun freeSoldiersForCommander(state: GameState, commanderId: Long, type: UnitType): Int {
        val current = state.assignedTo(commanderId, type)
        val usedByOthers = state.commanderAssignments
            .filterNot { it.commanderId == commanderId }
            .flatMap { it.units }
            .filter { it.type == type }
            .sumOf { it.amount }
        return (state.soldiers(type) - usedByOthers).coerceAtLeast(current)
    }

    fun promoteCommander(state: GameState): ActionResult {
        if (state.armySize < 100) return ActionResult(state, "Du brauchst mindestens 100 aktive Soldaten.")
        if (state.resources.gold < 250) return ActionResult(state, "Eine Beförderung und Ausrüstung kostet 250 Gold.")
        val index = state.commanders.size
        val cultures = state.regiments.map { it.type.culture }.ifEmpty { listOf(Culture.HUMAN) }
        val culture = cultures[index % cultures.size]
        val names = when (culture) {
            Culture.GOLD_ELF -> listOf("Aelor", "Thalion", "Eryndor", "Caladren")
            Culture.WOOD_ELF -> listOf("Caelen", "Lethriel", "Faelar", "Sylwen")
            Culture.WALL -> listOf("Wei Jian", "Lin Zhao", "Shen Rui", "Mei Ren")
            Culture.HUMAN -> listOf("Marcus", "Edric", "Rowan", "Gareth")
        }
        val name = names[index % names.size]
        val commander = Commander(
            id = System.nanoTime(),
            name = name,
            culture = culture,
            portraitKey = when (culture) {
                Culture.GOLD_ELF -> "gold_elf"
                Culture.WOOD_ELF -> "wood_elf"
                Culture.WALL -> "wall_guard"
                Culture.HUMAN -> "knight"
            },
            sword = 58 + index % 14,
            bow = 50 + (index * 3) % 18,
            leadership = 48 + (index * 5) % 20,
            tactics = 45 + (index * 7) % 22,
            loyalty = 72 + index % 20,
            trait = listOf("Loyal", "Mutig", "Bedacht", "Diszipliniert")[index % 4]
        )
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - 250),
            commanders = state.commanders + commander,
            chronicle = (state.chronicle + ChronicleEntry(state.day, "Neue Führungskraft", "$name wird zum Hauptmann befördert.")).takeLast(80)
        )
        return ActionResult(next, "$name wurde als individueller Kommandant übernommen.")
    }

    fun trainCommander(state: GameState, id: Long): ActionResult {
        val commander = state.commanders.firstOrNull { it.id == id } ?: return ActionResult(state, "Kommandant nicht gefunden.")
        if (state.resources.gold < 120) return ActionResult(state, "Training kostet 120 Gold.")
        val updated = commander.copy(
            level = commander.level + 1,
            leadership = min(100, commander.leadership + 2),
            tactics = min(100, commander.tactics + 2),
            sword = min(100, commander.sword + 1),
            bow = min(100, commander.bow + 1),
            rank = when {
                commander.level >= 8 -> "Marschall"
                commander.level >= 4 -> "General"
                else -> commander.rank
            }
        )
        return ActionResult(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold - 120),
                commanders = state.commanders.map { if (it.id == id) updated else it }
            ),
            "${updated.name} beendet das Training."
        )
    }

    fun simulateBattle(state: GameState, enemy: EnemyType, tactic: Tactic, randomFactor: Double = Random.nextDouble(0.90, 1.11)): BattleResult {
        if (state.armySize <= 0) return BattleResult(state, "Keine Armee", "Du hast keine einsatzbereiten Regimenter.", false)

        val baseEnemy = when (enemy) {
            EnemyType.ORC -> 4_000 + state.victories * 850 + state.realm.territory * 1_200
            EnemyType.URUK -> 7_500 + state.victories * 1_200 + state.realm.territory * 1_800
            EnemyType.TAO_TEI -> 10_000 + state.victories * 1_550 + state.realm.territory * 2_300
        }
        val tacticMultiplier = when (tactic) {
            Tactic.HOLD -> 1.04
            Tactic.AGGRESSIVE -> if (enemy == EnemyType.ORC) 1.10 else 0.98
            Tactic.RANGED -> if (enemy == EnemyType.TAO_TEI) 1.12 else 1.04
            Tactic.FLANK -> if (enemy == EnemyType.URUK) 1.10 else 1.02
            Tactic.FORTIFY -> 1.0 + (state.realm.level(BuildingType.WALL) * 0.07) + (state.realm.level(BuildingType.TOWER) * 0.04)
        }
        val commanderBonus = commanderBattleBonus(state)
        val companionBonus = if (state.companion.met) (state.companion.leadership + state.companion.tactics) / 300.0 else 0.0
        val playerPower = state.armyPower * tacticMultiplier * (1.0 + commanderBonus + companionBonus) * randomFactor
        val victory = playerPower >= baseEnemy

        val lossRate = if (victory) {
            when (enemy) {
                EnemyType.ORC -> 0.04
                EnemyType.URUK -> 0.08
                EnemyType.TAO_TEI -> 0.12
            }
        } else {
            when (enemy) {
                EnemyType.ORC -> 0.14
                EnemyType.URUK -> 0.22
                EnemyType.TAO_TEI -> 0.30
            }
        }
        val adjustedLossRate = if (tactic == Tactic.FORTIFY) lossRate * 0.72 else lossRate
        var lost = 0
        val surviving = state.regiments.mapNotNull { reg ->
            val casualties = min(reg.soldiers, max(1, (reg.soldiers * adjustedLossRate * Random.nextDouble(0.75, 1.25)).toInt()))
            lost += casualties
            val left = reg.soldiers - casualties
            if (left <= 0) null else reg.copy(
                soldiers = left,
                experience = min(100, reg.experience + if (victory) 8 else 3),
                morale = (reg.morale + if (victory) 6 else -12).coerceIn(10, 100)
            )
        }

        val loot = if (victory) baseEnemy / 8 else 0
        val fame = if (victory) baseEnemy / 55 else 10
        val threatDrop = if (victory) 35 else 8
        val next = updateProgress(
            state.copy(
                regiments = surviving,
                resources = state.resources.copy(gold = state.resources.gold + loot, food = state.resources.food + if (victory) baseEnemy / 20 else 0),
                renown = state.renown + fame,
                victories = state.victories + if (victory) 1 else 0,
                defeats = state.defeats + if (victory) 0 else 1,
                realm = state.realm.copy(
                    threat = max(0, state.realm.threat - threatDrop),
                    wallIntegrity = if (tactic == Tactic.FORTIFY) max(20, state.realm.wallIntegrity - if (victory) 6 else 18) else state.realm.wallIntegrity
                ),
                companion = if (state.companion.met) state.companion.copy(
                    trust = min(100, state.companion.trust + if (victory) 3 else 1),
                    respect = min(100, state.companion.respect + if (victory) 4 else 1)
                ) else state.companion,
                chronicle = (state.chronicle + ChronicleEntry(
                    state.day,
                    if (victory) "Sieg gegen ${enemy.label}" else "Niederlage gegen ${enemy.label}",
                    "Taktik: ${tactic.label}. Verluste: $lost. ${if (victory) "Beute: $loot Gold." else "Die Armee zieht sich zurück."}"
                )).takeLast(80)
            )
        )
        return BattleResult(
            next,
            if (victory) "Sieg!" else "Niederlage",
            "Gegner: ${enemy.label}\nEigene Verluste: $lost\n${if (victory) "Beute: $loot Gold · Ruhm: +$fame" else "Die verbleibenden Regimenter konnten sich retten."}",
            victory
        )
    }


    fun simulateBattleLive(
        state: GameState,
        enemy: EnemyType,
        tactic: Tactic,
        seed: Int = state.day * 97 + state.victories * 31 + enemy.ordinal * 17
    ): LiveBattleResult {
        if (state.armySize <= 0) {
            return LiveBattleResult(state, false, 0, 0, emptyList(), "Keine einsatzbereite Armee")
        }

        val rng = Random(seed)
        val enemyStart = when (enemy) {
            EnemyType.ORC -> 3_500 + state.victories * 700 + state.realm.territory * 900
            EnemyType.URUK -> 6_000 + state.victories * 1_000 + state.realm.territory * 1_300
            EnemyType.TAO_TEI -> 8_000 + state.victories * 1_300 + state.realm.territory * 1_700
        }
        val ownStart = state.armySize

        val rangedShare = state.regiments.sumOf { it.soldiers * it.type.ranged }.toDouble() /
                state.regiments.sumOf { it.soldiers * max(1, it.type.attack + it.type.defense + it.type.ranged) }.coerceAtLeast(1)
        val commanderBonus = commanderBattleBonus(state)
        val companionBonus = if (state.companion.met) {
            (state.companion.leadership + state.companion.tactics) / 450.0
        } else 0.0
        val wallBonus = if (tactic == Tactic.FORTIFY) {
            state.realm.level(BuildingType.WALL) * 0.055 + state.realm.level(BuildingType.TOWER) * 0.03
        } else 0.0
        val tacticBonus = when (tactic) {
            Tactic.HOLD -> 0.05
            Tactic.AGGRESSIVE -> if (enemy == EnemyType.ORC) 0.11 else -0.02
            Tactic.RANGED -> 0.05 + rangedShare * 0.35
            Tactic.FLANK -> if (enemy == EnemyType.URUK) 0.12 else 0.04
            Tactic.FORTIFY -> 0.08 + wallBonus
        }

        val ownQuality = state.armyPower.toDouble() / ownStart.coerceAtLeast(1)
        val enemyQuality = when (enemy) {
            EnemyType.ORC -> 13.0
            EnemyType.URUK -> 20.0
            EnemyType.TAO_TEI -> 18.0
        }
        val ownScore = ownStart * ownQuality * (1.0 + tacticBonus + commanderBonus + companionBonus)
        val enemyScore = enemyStart * enemyQuality
        val expectedWin = ownScore / enemyScore.coerceAtLeast(1.0)

        var ownRemaining = ownStart
        var enemyRemaining = enemyStart
        var front = 50
        val frames = mutableListOf<BattleFrame>()
        val phases = listOf(
            5 to "Die Linien formieren sich.",
            12 to "Erste Fernkampfsalven treffen die Front.",
            20 to "Die Vorhut prallt auf den Gegner.",
            30 to "Der Hauptkampf beginnt.",
            42 to "Reserven werden nach vorne geführt.",
            55 to "Die Front beginnt sich sichtbar zu verschieben.",
            68 to "Beide Seiten werfen ihre letzten frischen Kräfte hinein.",
            78 to "Die Entscheidung naht.",
            86 to "Eine Seite beginnt zu wanken.",
            90 to "Die Schlacht ist entschieden."
        )

        phases.forEachIndexed { index, phase ->
            if (ownRemaining <= 0 || enemyRemaining <= 0) return@forEachIndexed

            val progress = (index + 1) / phases.size.toDouble()
            val momentum = (expectedWin - 1.0) * 0.65 + rng.nextDouble(-0.15, 0.15)
            val ownBaseLoss = when (enemy) {
                EnemyType.ORC -> 0.010
                EnemyType.URUK -> 0.016
                EnemyType.TAO_TEI -> 0.020
            }
            val enemyBaseLoss = 0.018

            val ownLossRate = (ownBaseLoss * (1.12 - expectedWin.coerceIn(0.55, 1.55) * 0.35) *
                    (if (tactic == Tactic.FORTIFY) 0.78 else 1.0) *
                    rng.nextDouble(0.78, 1.24)).coerceIn(0.003, 0.045)
            val enemyLossRate = (enemyBaseLoss * expectedWin.coerceIn(0.50, 1.85) *
                    (1.0 + if (tactic == Tactic.RANGED) rangedShare * 0.25 else 0.0) *
                    rng.nextDouble(0.78, 1.24)).coerceIn(0.004, 0.055)

            val ownLoss = min(ownRemaining, max(1, (ownRemaining * ownLossRate).toInt()))
            val enemyLoss = min(enemyRemaining, max(1, (enemyRemaining * enemyLossRate).toInt()))
            ownRemaining -= ownLoss
            enemyRemaining -= enemyLoss

            front = (front + (momentum * 9).toInt() + rng.nextInt(-2, 3)).coerceIn(10, 90)

            val event = when {
                index == 1 && rangedShare > 0.20 -> "Deine Fernkämpfer legen einen dichten Pfeilteppich."
                index == 3 && tactic == Tactic.FLANK -> "Die Flügel schwenken ein und greifen die gegnerische Seite an."
                index == 4 && tactic == Tactic.FORTIFY -> "Mauertruppen halten ihre vorbereiteten Stellungen."
                index == 5 && front >= 62 -> "Deine Armee gewinnt deutlich Boden."
                index == 5 && front <= 38 -> "Der Gegner drückt deine Front zurück."
                index == 7 && commanderBonus > 0.10 -> "Die Kommandanten stabilisieren ihre Kontingente und treiben sie vor."
                index == 8 && expectedWin >= 1.0 -> "Der feindliche Widerstand beginnt zu brechen."
                index == 8 -> "Deine Linien geraten unter extremen Druck."
                else -> phase.second
            }

            frames += BattleFrame(
                minute = phase.first,
                ownRemaining = ownRemaining,
                enemyRemaining = enemyRemaining,
                ownLossesThisFrame = ownLoss,
                enemyLossesThisFrame = enemyLoss,
                front = front,
                event = event
            )
        }

        val victory = when {
            enemyRemaining <= 0 -> true
            ownRemaining <= 0 -> false
            else -> (enemyRemaining.toDouble() / enemyStart) < (ownRemaining.toDouble() / ownStart)
        }

        val targetOwnSurvivors = ownRemaining.coerceAtLeast(if (victory) 1 else 0)
        val survivorRatio = targetOwnSurvivors.toDouble() / ownStart.coerceAtLeast(1)
        var allocatedSurvivors = 0
        val survivorRegs = state.regiments.mapIndexedNotNull { index, reg ->
            val isLast = index == state.regiments.lastIndex
            val left = if (isLast) {
                (targetOwnSurvivors - allocatedSurvivors).coerceIn(0, reg.soldiers)
            } else {
                val n = (reg.soldiers * survivorRatio * rng.nextDouble(0.90, 1.10)).toInt().coerceIn(0, reg.soldiers)
                allocatedSurvivors += n
                n
            }
            if (left <= 0) null else reg.copy(
                soldiers = left,
                experience = min(100, reg.experience + if (victory) 7 else 3),
                morale = (reg.morale + if (victory) 5 else -10).coerceIn(10, 100)
            )
        }

        val loot = if (victory) enemyStart / 7 else 0
        val fame = if (victory) enemyStart / 45 else 8
        val finalAssignments = clampAssignments(state.copy(regiments = survivorRegs))
        val finalState = updateProgress(
            finalAssignments.copy(
                resources = finalAssignments.resources.copy(
                    gold = finalAssignments.resources.gold + loot,
                    food = finalAssignments.resources.food + if (victory) enemyStart / 18 else 0
                ),
                renown = finalAssignments.renown + fame,
                victories = finalAssignments.victories + if (victory) 1 else 0,
                defeats = finalAssignments.defeats + if (victory) 0 else 1,
                realm = finalAssignments.realm.copy(
                    threat = max(0, finalAssignments.realm.threat - if (victory) 38 else 8),
                    wallIntegrity = if (tactic == Tactic.FORTIFY) {
                        max(15, finalAssignments.realm.wallIntegrity - if (victory) 5 else 16)
                    } else finalAssignments.realm.wallIntegrity
                ),
                chronicle = (finalAssignments.chronicle + ChronicleEntry(
                    finalAssignments.day,
                    if (victory) "Live-Schlacht gewonnen" else "Live-Schlacht verloren",
                    "Gegner: ${enemy.label}. Eigene Verluste: ${ownStart - targetOwnSurvivors}. Feindliche Verluste: ${enemyStart - enemyRemaining}."
                )).takeLast(80)
            )
        )

        return LiveBattleResult(
            finalState = finalState,
            victory = victory,
            enemyStart = enemyStart,
            ownStart = ownStart,
            frames = frames,
            headline = if (victory) "Sieg" else "Niederlage"
        )
    }

    private fun commanderBattleBonus(state: GameState): Double {
        if (state.commanders.isEmpty() || state.armySize <= 0) return 0.0
        return state.commanderAssignments.sumOf { assignment ->
            val commander = state.commanders.firstOrNull { it.id == assignment.commanderId }
                ?: return@sumOf 0.0
            val commanded = assignment.total.coerceAtMost(state.armySize)
            val share = commanded.toDouble() / state.armySize.coerceAtLeast(1)
            ((commander.leadership + commander.tactics) / 300.0) * share
        }.coerceAtMost(0.55)
    }

    private fun clampAssignments(state: GameState): GameState {
        val remainingByType = UnitType.entries.associateWith { state.soldiers(it) }.toMutableMap()
        val cleaned = mutableListOf<CommanderAssignment>()
        state.commanderAssignments.forEach { assignment ->
            val commander = state.commanders.firstOrNull { it.id == assignment.commanderId } ?: return@forEach
            val units = assignment.units.mapNotNull { unit ->
                if (unit.type.culture != commander.culture) return@mapNotNull null
                val remaining = remainingByType[unit.type] ?: 0
                val amount = min(unit.amount, remaining).coerceAtLeast(0)
                remainingByType[unit.type] = remaining - amount
                if (amount > 0) UnitAllocation(unit.type, amount) else null
            }
            cleaned += CommanderAssignment(assignment.commanderId, units)
        }
        return state.copy(commanderAssignments = cleaned)
    }

    private fun removeRecruits(p: Population, culture: Culture, amount: Int): Population = when (culture) {
        Culture.HUMAN -> p.copy(humanRecruits = max(0, p.humanRecruits - amount))
        Culture.WOOD_ELF -> p.copy(woodElfRecruits = max(0, p.woodElfRecruits - amount))
        Culture.GOLD_ELF -> p.copy(goldElfRecruits = max(0, p.goldElfRecruits - amount))
        Culture.WALL -> p.copy(wallRecruits = max(0, p.wallRecruits - amount))
    }

    private fun defaultRegimentName(type: UnitType, index: Int): String {
        val prefix = when (type.culture) {
            Culture.HUMAN -> "$index. Menschenregiment"
            Culture.WOOD_ELF -> "$index. Waldlegion"
            Culture.GOLD_ELF -> "$index. Goldene Legion"
            Culture.WALL -> "$index. Mauerlegion"
        }
        return "$prefix · ${type.label}"
    }

    private fun settlementNameFor(palace: Int, territory: Int): String = when {
        palace >= 4 || territory >= 7 -> "Kronenfeste"
        palace >= 2 || territory >= 4 -> "Große Grenzfestung"
        territory >= 2 -> "Mauerstadt"
        territory >= 1 -> "Burgsiedlung"
        else -> "Grenzlager"
    }

    private fun updateProgress(state: GameState): GameState {
        val rank = when {
            state.renown >= 2200 -> "Marschall"
            state.renown >= 1300 -> "Kommandant"
            state.renown >= 700 -> "Hauptmann"
            state.renown >= 300 -> "Veteran"
            state.renown >= 100 -> "Soldat"
            else -> "Rekrut"
        }
        val title = when {
            state.realm.territory >= 8 && state.armySize >= 10_000 && state.realm.level(BuildingType.WALL) >= 5 -> "Hochkönig"
            state.realm.territory >= 5 -> "König"
            state.realm.territory >= 3 -> "Fürst"
            state.realm.territory >= 1 -> "Landherr"
            else -> "Landlos"
        }
        val completed = state.completedRealm || title == "Hochkönig"
        val chronicle = if (completed && !state.completedRealm) {
            (state.chronicle + ChronicleEntry(state.day, "Das Reich steht", "Aus dem unbekannten Soldaten ist der Herrscher eines gewaltigen Reiches geworden. Das Spiel geht weiter: Jede neue Horde kann alles erneut prüfen.")).takeLast(80)
        } else state.chronicle
        return state.copy(rank = rank, title = title, completedRealm = completed, chronicle = chronicle)
    }
}
