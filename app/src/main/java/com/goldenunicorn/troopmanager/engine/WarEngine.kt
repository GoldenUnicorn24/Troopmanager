package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil
import kotlin.random.Random

/** War logistics own aggregate quantities; wounded and captives never remain in an army pool. */
object WarEngine {
    data class DefenseReadiness(val garrison: Int, val reserve: Int, val wallArchers: Int, val arrows: Int,
        val wall: Int, val gate: Int, val tower: Int, val activeWeapons: Int, val warnings: List<String>)

    /** A projection of the same deployment, wall capacity and stocks used by BattleEngine. */
    fun defenseReadiness(state: GameState): DefenseReadiness {
        val battle = state.battleSession?.takeIf { it.isActive && FrontierEngine.isHomeFortifiedBattle(state, it) }
        val deployments = if (battle == null) BattleEngine.defaultDeployments(state) else emptyList()
        val capacity = BattleResolutionEngine.shootingCapacity(state, true, BattleTerrain.WALL)
        val archers = BattleStateEngine.sections.sumOf { section -> minOf(capacity, if (battle != null)
            battle.contingents.filter { it.section == section && !it.routed && it.type.ranged >= 8 && it.type != UnitType.DRAGON_ARTILLERY }.sumOf { it.soldiers }
            else deployments.filter { it.section == section }.sumOf { d -> d.units.filter { it.type.ranged >= 8 && it.type != UnitType.DRAGON_ARTILLERY }.sumOf { it.amount } }) }
        val arrows = battle?.battleArrowsRemaining ?: state.militaryStock.arrows
        val reserve = battle?.fighting(BattleSection.RESERVE) ?: deployments.filter { it.section == BattleSection.RESERVE }.sumOf { d -> d.units.sumOf { it.amount } }
        val wall = battle?.wallIntegrity ?: state.realm.wallIntegrity
        val gate = battle?.segment(BattleSection.CENTER)?.gateIntegrity ?: state.realm.wallIntegrity
        val tower = effectiveLevel(state, BuildingType.TOWER)
        val weapons = battle?.wallWeapons ?: state.frontier.weapons
        val active = weapons.filter { it.integrity > 0 && it.ammunition > 0 && it.automatic }.sumOf { it.count }
        val warnings = buildList {
            if (archers == 0) add("Keine Bogenschützen auf der Mauer")
            if (arrows < archers * 3L) add("Zu wenig Pfeile für drei volle Austausche")
            if (wall < 60) add("Mauer beschädigt: weniger Schutz und Höhenvorteil")
            if (gate < 65) add("Tor beschädigt")
            if (tower == 0) add("Kein funktionsfähiger Turm")
            if (state.commanders.any { state.commanderAway(it.id) }) add("Kommandant abwesend")
            if (state.war.wounded.sumOf { it.soldiers } * 5L >= hospitalCapacity(state) * 4L) add("Lazarett fast voll")
            if (weapons.any { it.count > 0 && it.ammunition == 0 }) add("Mauerwaffen ohne Munition")
            if (state.resources.food == 0) add("Keine Nahrung: Moral und Kampfkraft sinken")
        }
        return DefenseReadiness(state.homeArmySize, reserve, archers, arrows, wall, gate, tower, active, warnings)
    }
    private fun sum(a: Int, b: Int) = (a.toLong() + b).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    fun afterCombatLosses(before: GameState, after: GameState): GameState = FrontierEngine.afterTroopLosses(before, after.copy(war = after.war.copy(eliteUnits = after.war.eliteUnits.map { elite ->
        val original = before.soldiers(elite.type)
        elite.copy(soldiers = if (original == 0) 0 else (elite.soldiers.toLong() * after.soldiers(elite.type) / original).toInt())
    })))

    fun effectiveLevel(state: GameState, type: BuildingType): Int =
        (state.realm.level(type).toLong() * (100 - (state.war.buildingDamage[type] ?: 0).coerceIn(0, 100)) / 100).toInt()

    private fun primaryGood(type: UnitType): MilitaryGood =
        when (type) {
            UnitType.HUMAN_ARCHER,
            UnitType.WOOD_RANGER,
            UnitType.GOLD_ARCHER,
            UnitType.EAGLE_CORPS -> MilitaryGood.BOWS
            UnitType.GOLD_SPEAR,
            UnitType.CRANE_GUARD,
            UnitType.BEAR_CORPS,
            UnitType.DEER_CORPS -> MilitaryGood.SPEARS
            UnitType.DRAGON_ARTILLERY -> MilitaryGood.SIEGE_PARTS
            else -> MilitaryGood.SWORDS
        }

    fun hospitalCapacity(state: GameState): Int {
        val hospital = effectiveLevel(state, BuildingType.HOSPITAL).coerceAtLeast(0)
        val healing = CharacterEngine.bonuses(state).healing
        return 60 + hospital * 180 + healing * 20
    }

    fun validate(state: GameState) {
        val war = state.war
        require(war.wounded.size <= UnitType.entries.size * 16 && war.wounded.map { it.id }.distinct().size == war.wounded.size && war.wounded.map { it.type to it.recoveryDay }.distinct().size == war.wounded.size) { "Ungültige Verwundetenverbände." }
        require(war.wounded.all { it.soldiers > 0 && it.recoveryDay >= 1 && it.experience in 0..100 && it.equipment in 0..100 }) { "Ungültige Verwundete." }
        require(war.captives.size <= UnitType.entries.size * (1 + EnemyType.entries.size + state.world.factions.size) && war.captives.map { it.id }.distinct().size == war.captives.size && war.captives.all { it.soldiers > 0 && it.capturedDay >= 0 && (!it.own || it.type != null) }) { "Ungültige Gefangene." }
        require(war.buildingDamage.values.all { it in 0..100 } && war.gateReinforcement in 0..20 && war.siegeFoodStored in 0..4_000) { "Ungültige Belagerungsvorbereitung." }
        require(war.equipment.map { it.type }.distinct().size == war.equipment.size && war.equipment.all { it.stock in 0..1_000_000 }) { "Ungültiges Arsenal." }
        require(war.commanderConditions.map { it.commanderId }.distinct().size == war.commanderConditions.size && war.commanderConditions.all { condition -> state.commanders.any { it.id == condition.commanderId } && condition.untilDay >= 0 }) { "Ungültiger Kommandantenzustand." }
        require(war.eliteUnits.size <= 32 && war.eliteUnits.map { it.id }.distinct().size == war.eliteUnits.size && war.eliteUnits.all { it.name.isNotBlank() && it.soldiers >= 0 && it.battles >= 0 && it.victories in 0..it.battles && it.history.size <= 20 }) { "Ungültige Eliteverbände." }
        UnitType.entries.forEach { type -> require(war.eliteUnits.filter { it.type == type }.sumOf { it.soldiers.toLong() } <= state.soldiers(type)) { "Eliteverbände übersteigen den Armeepool." } }
        require(war.history.map { it.id }.distinct().size == war.history.size) { "Ungültige Schlachthistorie." }
        war.history.forEach { record ->
            require(record.ownStart >= 0 && record.enemyStart > 0 && record.ownRemaining in 0..record.ownStart && record.enemyRemaining in 0..record.enemyStart && record.minute in 0..95 && record.inputs.size <= 160 && BattleEngine.validPlan(record.plan)) { "Ungültiger Schlachtbericht." }
            require(record.replay == null || record.replay.rulesVersion in 1..2) { "Unbekannte Replay-Regeln." }
            require(listOf(record.casualties.dead, record.casualties.wounded, record.casualties.missing, record.casualties.captured).all { it >= 0 } && listOf(record.casualties.dead, record.casualties.wounded, record.casualties.missing, record.casualties.captured).sumOf { it.toLong() } == record.ownStart.toLong() - record.ownRemaining) { "Ungültige Verlustbilanz." }
        }
        state.battleSession?.let { battle ->
            BattleStateEngine.validate(battle)
            require(battle.replayStart == null || battle.replayStart.rulesVersion in 1..2) { "Unbekannte Replay-Regeln." }
            require(battle.commandPoints in 0..battle.maxCommandPoints && battle.maxCommandPoints in 1..100 && battle.inputs.size <= 160 && battle.inputs.all { it.plan == null || BattleEngine.validPlan(it.plan) } && battle.enemyFortification in 0..100 && (battle.deployedMorale == null || battle.deployedMorale in 0..100) && battle.enemyExperience in 0..10000) { "Ungültige Befehlspunkte." }
            require(battle.rangedWeather.isFinite() && battle.cavalryWeather.isFinite() && battle.seasonPenalty.isFinite() && battle.rangedWeather in 0.25..1.5 && battle.cavalryWeather in 0.25..1.5 && battle.seasonPenalty in 0.5..1.5) { "Ungültige Schlachtbedingungen." }
        }
        Culture.entries.forEach { culture ->
            val represented = state.armyPools.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } + state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount.toLong() } + war.wounded.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } + war.captives.filter { it.own && it.type?.culture == culture }.sumOf { it.soldiers.toLong() } + state.population.recruits(culture)
            require(represented <= ArmyEngine.population(state.population, culture)) { "Lebende Militärpersonen überschreiten die Bevölkerung." }
        }
    }

    fun tick(state: GameState): GameState {
        if (state.battleSession?.isActive == true || state.war.lastTickDay >= state.day) return state
        var next = state
        val healing = CharacterEngine.bonuses(state).healing
        val due = state.war.wounded.filter { it.recoveryDay <= state.day + if (healing >= 3) 1 else 0 }
        due.forEach { cohort ->
            next = ArmyEngine.add(next, cohort.type, cohort.soldiers, cohort.experience, 65)
            next = next.copy(armyPools = next.armyPools.map { pool ->
                if (pool.type != cohort.type) pool else {
                    val oldCount = pool.soldiers - cohort.soldiers
                    pool.copy(equipment = ((pool.equipment.toLong() * oldCount + cohort.equipment.toLong() * cohort.soldiers) / pool.soldiers.coerceAtLeast(1)).toInt().coerceIn(0, 100))
                }
            })
        }
        val conditions = state.war.commanderConditions.mapNotNull {
            if (it.status in listOf(CombatantStatus.WOUNDED, CombatantStatus.UNCONSCIOUS) && it.untilDay <= state.day + if (healing >= 3) 1 else 0) null else it
        }
        next = next.copy(war = next.war.copy(
            wounded = state.war.wounded - due.toSet(), commanderConditions = conditions,
            playerCondition = if (state.war.playerCondition in listOf(CombatantStatus.WOUNDED, CombatantStatus.UNCONSCIOUS) && state.war.playerRecoveryDay <= state.day) CombatantStatus.ACTIVE else state.war.playerCondition,
            lastTickDay = state.day,
        ))
        if (due.isNotEmpty()) next = next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, "Verwundete kehren zurück", "${due.sumOf { it.soldiers }} Soldaten verlassen das Lazarett und stehen wieder im Heer.")).takeLast(2000))
        val arsenal = effectiveLevel(next, BuildingType.ARSENAL).coerceAtMost(20)
        if (arsenal > 0) {
            var military = next.militaryStock
            val pools =
                next.armyPools.map { pool ->
                    val home = next.homeSoldiers(pool.type)
                    if (home <= 0 || pool.equipment >= 100) return@map pool
                    val primary = primaryGood(pool.type)
                    val costPerPoint = ceil(home / 80.0).toInt().coerceAtLeast(1)
                    val possibleByPrimary = military.amount(primary) / costPerPoint
                    val possibleByArmor =
                        if (pool.type == UnitType.WOOD_RANGER || pool.type == UnitType.EAGLE_CORPS)
                            Int.MAX_VALUE
                        else military.armor / costPerPoint
                    val points =
                        minOf(
                            100 - pool.equipment,
                            arsenal.coerceAtLeast(1),
                            possibleByPrimary,
                            possibleByArmor,
                        )
                    if (points <= 0) return@map pool
                    military =
                        military.withAmount(
                            primary,
                            military.amount(primary) - points * costPerPoint,
                        )
                    if (possibleByArmor != Int.MAX_VALUE)
                        military =
                            military.copy(
                                armor = (military.armor - points * costPerPoint).coerceAtLeast(0)
                            )
                    pool.copy(equipment = pool.equipment + points)
                }
            next = next.copy(militaryStock = military, armyPools = pools)
        }
        val remaining = next.armyPools.associate { it.type to it.soldiers }.toMutableMap()
        val elites = next.war.eliteUnits.map { elite ->
            val count = minOf(elite.soldiers, remaining[elite.type] ?: 0)
            remaining[elite.type] = (remaining[elite.type] ?: 0) - count
            elite.copy(soldiers = count)
        }
        return ArmyEngine.clampAssignments(next.copy(war = next.war.copy(eliteUnits = elites)))
    }

    fun repairBuilding(state: GameState, type: BuildingType): GameEngine.ActionResult {
        val damage = state.war.buildingDamage[type] ?: 0
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Reparaturen sind während einer Schlacht nicht möglich.")
        if (damage <= 0) return GameEngine.ActionResult(state, "Dieses Gebäude ist unbeschädigt.")
        val cost = Resources(damage * 3, 0, damage * 2, damage * 2, 0)
        if (state.resources.gold < cost.gold || state.resources.wood < cost.wood || state.resources.stone < cost.stone) return GameEngine.ActionResult(state, "Reparatur braucht ${cost.gold} Gold, ${cost.wood} Holz und ${cost.stone} Stein.")
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost.gold, wood = state.resources.wood - cost.wood, stone = state.resources.stone - cost.stone), war = state.war.copy(buildingDamage = state.war.buildingDamage - type)), "${type.label} wurde repariert.")
    }

    fun prepareSiege(state: GameState, action: String): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Bereite die Stadt vor Schlachtbeginn vor.")
        return when (action) {
            "food" -> if (state.resources.food < 400 || state.war.siegeFoodStored >= 4_000) GameEngine.ActionResult(state, "400 Nahrung benötigt; das Belagerungsdepot fasst 4.000.")
                else GameEngine.ActionResult(state.copy(resources = state.resources.copy(food = state.resources.food - 400), war = state.war.copy(siegeFoodStored = state.war.siegeFoodStored + 400)), "400 Nahrung geschützt eingelagert.")
            "evacuate" -> if (state.war.civiliansEvacuated || state.resources.gold < 100 || state.resources.food < 200) GameEngine.ActionResult(state, "Evakuierung braucht 100 Gold und 200 Nahrung und kann einmal vorbereitet werden.")
                else GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - 100, food = state.resources.food - 200), war = state.war.copy(civiliansEvacuated = true)), "Zivilisten sind geschützt; zivile Belagerungsverluste sinken um 80 %.")
            "gate" -> if (state.resources.wood < 100 || state.resources.iron < 30 || state.war.gateReinforcement >= 20) GameEngine.ActionResult(state, "Torverstärkung braucht 100 Holz und 30 Eisen (maximal 20 Schutzpunkte).")
                else GameEngine.ActionResult(state.copy(resources = state.resources.copy(wood = state.resources.wood - 100, iron = state.resources.iron - 30), war = state.war.copy(gateReinforcement = state.war.gateReinforcement + 5)), "Tor verstärkt; eingehender Belagerungsschaden sinkt.")
            else -> GameEngine.ActionResult(state, "Unbekannte Vorbereitung.")
        }
    }

    fun improviseEquipment(state: GameState, type: UnitType): GameEngine.ActionResult {
        val pool = state.armyPools.firstOrNull { it.type == type } ?: return GameEngine.ActionResult(state, "Kein Verband vorhanden.")
        if (state.battleSession?.isActive == true || state.away(type) > 0 || pool.equipment >= 50) return GameEngine.ActionResult(state, "Notrüstung ist nur für stark beschädigte Heimattruppen verfügbar.")
        val wood = ceil(pool.soldiers / 10.0).toInt().coerceAtLeast(1)
        if (state.resources.wood < wood) return GameEngine.ActionResult(state, "Notrüstung braucht $wood Holz; sie ersetzt hochwertige Ausrüstung durch improvisierte Qualität.")
        val batch = state.war.equipment.firstOrNull { it.type == type } ?: EquipmentBatch(type)
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(wood = state.resources.wood - wood), armyPools = state.armyPools.map { if (it.type == type) it.copy(equipment = (it.equipment + 25).coerceAtMost(60)) else it }, war = state.war.copy(equipment = state.war.equipment.filterNot { it.type == type } + batch.copy(quality = EquipmentQuality.IMPROVISED))), "Notrüstung verbessert den Zustand um 25; improvisierte Waffen mindern die Kampfkraft bis zum Arsenal-Ausbau.")
    }

    fun upgradeEquipment(state: GameState, type: UnitType): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true || state.away(type) > 0) return GameEngine.ActionResult(state, "Ausrüstung wird erst nach der Rückkehr verbessert.")
        val pool = state.armyPools.firstOrNull { it.type == type } ?: return GameEngine.ActionResult(state, "Kein Verband vorhanden.")
        val batch = state.war.equipment.firstOrNull { it.type == type } ?: EquipmentBatch(type)
        val target =
            EquipmentQuality.entries.getOrNull(batch.quality.ordinal + 1)
                ?: return GameEngine.ActionResult(state, "Legendäre Qualität erreicht.")
        val level = effectiveLevel(state, BuildingType.ARSENAL)
        val requiredLevel =
            when (target) {
                EquipmentQuality.IMPROVISED -> 0
                EquipmentQuality.NORMAL -> 1
                EquipmentQuality.GOOD -> 2
                EquipmentQuality.MASTERWORK -> 4
                EquipmentQuality.LEGENDARY -> 8
            }
        val cost = ceil(pool.soldiers / 10.0).toInt().coerceAtLeast(1)
        val primary = primaryGood(type)
        val primaryMissing = (cost - state.militaryStock.amount(primary)).coerceAtLeast(0)
        val armorMissing = (cost - state.militaryStock.armor).coerceAtLeast(0)
        if (
            level < requiredLevel ||
                primaryMissing > 0 ||
                armorMissing > 0 ||
                state.resources.gold < cost * 2L
        )
            return GameEngine.ActionResult(
                state,
                "${target.label}: Arsenal $requiredLevel, $cost ${primary.label}, $cost Rüstungen und ${cost * 2L} Gold benötigt.",
            )
        var military =
            state.militaryStock.withAmount(
                primary,
                state.militaryStock.amount(primary) - cost,
            )
        military = military.copy(armor = (military.armor - cost).coerceAtLeast(0))
        val upgraded = batch.copy(quality = target, stock = 0)
        return GameEngine.ActionResult(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold - cost * 2),
                militaryStock = military,
                war =
                    state.war.copy(
                        equipment =
                            state.war.equipment.filterNot { it.type == type } + upgraded
                    ),
            ),
            "${type.label} tragen jetzt ${target.label.lowercase()}e Ausrüstung.",
        )
    }

    fun ransomCommander(state: GameState, commanderId: Long?): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Lösegeld erst nach der Schlacht verhandeln.")
        val captured = if (commanderId == null) state.war.playerCondition == CombatantStatus.CAPTURED else state.war.commanderConditions.any { it.commanderId == commanderId && it.status == CombatantStatus.CAPTURED }
        val cost = if (commanderId == null) 1000 else 500
        if (!captured || state.resources.gold < cost) return GameEngine.ActionResult(state, "Gefangene Freilassung benötigt $cost Gold.")
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost), war = state.war.copy(commanderConditions = if (commanderId == null) state.war.commanderConditions else state.war.commanderConditions.filterNot { it.commanderId == commanderId }, playerCondition = if (commanderId == null) CombatantStatus.WOUNDED else state.war.playerCondition, playerRecoveryDay = if (commanderId == null) state.day + 3 else state.war.playerRecoveryDay)), "Freilassung ausgehandelt.")
    }

    fun captiveAction(state: GameState, id: String, action: CaptiveAction): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Gefangenenverwaltung ist nach der Schlacht möglich.")
        val group = state.war.captives.firstOrNull { it.id == id } ?: return GameEngine.ActionResult(state, "Gefangene nicht gefunden.")
        if (group.own) return GameEngine.ActionResult(state, "Eigene Soldaten können gegen gegnerische Gefangene getauscht werden.")
        var next = state
        var consumed = group.soldiers
        when (action) {
            CaptiveAction.RELEASE -> {
                next = next.copy(renown = sum(next.renown, (group.soldiers / 25).coerceAtLeast(1)))
                group.factionId?.let { factionId -> if (next.world.faction(factionId) != null) next = DiplomacyEngine.changeRelation(next, PLAYER_FACTION, factionId, 3, 2, "Gefangene freigelassen") }
            }
            CaptiveAction.RANSOM -> {
                val factionId = group.factionId ?: when (group.enemy) { EnemyType.ORC -> "ash_covenant"; EnemyType.URUK -> "copper_league"; EnemyType.TAO_TEI -> "verdant_compact" }
                val payer = next.world.faction(factionId) ?: return GameEngine.ActionResult(state, "Für diese Gefangenen bietet kein Reich Lösegeld an.")
                consumed = minOf(group.soldiers, payer.gold / 4, (next.city.storageCapacity.gold - next.resources.gold).coerceAtLeast(0) / 4)
                if (consumed == 0) return GameEngine.ActionResult(state, "Das Reich kann kein Lösegeld zahlen oder dein Goldlager ist voll.")
                val payment = consumed * 4
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold + payment), world = next.world.copy(factions = next.world.factions.map { if (it.id == factionId) it.copy(gold = it.gold - payment) else it }))
            }
            CaptiveAction.EXCHANGE -> {
                var available = group.soldiers
                val ownGroups = next.war.captives.mapNotNull { own ->
                    if (!own.own || available == 0 || own.type == null) own else {
                        val returned = minOf(available, own.soldiers); available -= returned
                        next = ArmyEngine.add(next, own.type, returned, 10, 65)
                        if (returned == own.soldiers) null else own.copy(soldiers = own.soldiers - returned)
                    }
                }
                consumed -= available
                if (consumed == 0) return GameEngine.ActionResult(state, "Es gibt keine eigenen Soldaten für einen Austausch.")
                next = next.copy(war = next.war.copy(captives = ownGroups))
            }
            CaptiveAction.RECRUIT -> {
                if (group.soldiers < 20 || state.player.diplomacy < 40 || state.day - group.capturedDay < 3) return GameEngine.ActionResult(state, "Freiwillige Aufnahme: mindestens 20 Gefangene, Diplomatie 40 und drei Tage Bedenkzeit.")
                val recruits = group.soldiers / 5
                consumed = recruits
                val type = group.type ?: when (next.world.faction(group.factionId ?: "")?.culture) {
                    Culture.WOOD_ELF -> UnitType.WOOD_BLADE
                    Culture.GOLD_ELF -> UnitType.GOLD_SPEAR
                    Culture.WALL -> UnitType.BEAR_CORPS
                    else -> UnitType.HUMAN_SWORD
                }
                next = ArmyEngine.add(next, type, recruits, 5, 45)
                next = next.copy(population = ArmyEngine.adjustPopulation(next.population, type.culture, recruits))
            }
        }
        val groups = next.war.captives.mapNotNull { if (it.id != id) it else if (it.soldiers == consumed) null else it.copy(soldiers = it.soldiers - consumed) }
        next = next.copy(war = next.war.copy(captives = groups), chronicle = (next.chronicle + ChronicleEntry(state.day, action.label, "$consumed Gefangene: ${action.label}.")).takeLast(2000))
        return GameEngine.ActionResult(next, "$consumed Gefangene: ${action.label}.")
    }

    fun createElite(state: GameState, type: UnitType, name: String, soldiers: Int): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Verbände können nach der Schlacht benannt werden.")
        val named = name.trim().take(40)
        val assigned = state.war.eliteUnits.filter { it.type == type }.sumOf { it.soldiers }
        val pool = state.armyPools.firstOrNull { it.type == type }
        if (named.isEmpty() || soldiers <= 0 || pool == null || soldiers > pool.soldiers - assigned || pool.experience < 30 || state.war.eliteUnits.size >= 32) return GameEngine.ActionResult(state, "Benannte Elite braucht erfahrene (30) unbenannte Soldaten; maximal 32 Verbände.")
        val id = "elite_${state.day}_${state.war.eliteUnits.size}_${type.name}"
        return GameEngine.ActionResult(state.copy(war = state.war.copy(eliteUnits = state.war.eliteUnits + EliteUnit(id, named, type, soldiers))), "$named erhält ein eigenes Banner und eine Dienstgeschichte.")
    }

    fun recordOutcome(state: GameState, session: BattleSession, victory: Boolean): GameState {
        val id = "battle_${state.day}_${state.war.history.lastOrNull()?.id?.hashCode() ?: 0}_${session.seed}"
        val hospital = effectiveLevel(state, BuildingType.HOSPITAL).coerceAtMost(6)
        val reports = mutableListOf<CasualtyReport>()
        val wounded = state.war.wounded.toMutableList()
        val captives = state.war.captives.toMutableList()
        var population = state.population
        var militaryStock = state.militaryStock
        var projectedPatients = wounded.sumOf { it.soldiers }
        val capacity = hospitalCapacity(state)
        val fieldMedicine = ResearchTech.FIELD_MEDICINE in state.research.completed
        session.contingents.groupBy { it.type }.forEach { (type, troops) ->
            val lost = troops.sumOf { it.startSoldiers - it.soldiers }
            val medicalBonus =
                (if (fieldMedicine) 6 else 0) +
                    if (militaryStock.medicine > 0) 4 else 0
            val injured =
                (
                    lost.toLong() *
                        (35 + hospital * 5 + session.healingBonus + medicalBonus)
                            .coerceAtMost(85) /
                        100
                ).toInt()
            val heldWall = victory && session.tactic == Tactic.FORTIFY
            val collapse = session.outcomeGrade in listOf(BattleOutcomeGrade.ROUT, BattleOutcomeGrade.CRUSHING_DEFEAT)
            val captured = if (victory || session.orderedRetreat) 0 else lost / if (collapse) 7 else 10
            val missing = if (heldWall) 0 else lost / if (session.orderedRetreat) 100 else if (collapse) 12 else 20
            val survivingInjured = minOf(injured, lost - captured - missing)
            val dead = lost - survivingInjured - captured - missing
            reports += CasualtyReport(dead, survivingInjured, missing, captured)
            // ArmyEngine removes every battlefield loss from population. Living casualties stay citizens.
            population = ArmyEngine.adjustPopulation(population, type.culture, survivingInjured + captured)
            if (survivingInjured > 0) {
                val medicineNeeded = ceil(survivingInjured / 4.0).toInt()
                val medicineUsed = minOf(militaryStock.medicine, medicineNeeded)
                militaryStock =
                    militaryStock.copy(
                        medicine = (militaryStock.medicine - medicineUsed).coerceAtLeast(0)
                    )
                projectedPatients += survivingInjured
                val overflow = (projectedPatients - capacity).coerceAtLeast(0)
                val overloadDays =
                    if (overflow == 0) 0
                    else ceil(overflow.toDouble() / capacity.coerceAtLeast(1))
                        .toInt()
                        .coerceAtMost(5)
                val recoveryDay =
                    state.day +
                        (
                            8 -
                                hospital -
                                if (fieldMedicine) 1 else 0 -
                                if (medicineNeeded > 0 && medicineUsed >= medicineNeeded) 1 else 0 +
                                overloadDays
                        ).coerceAtLeast(2)
                val previous = wounded.firstOrNull { it.type == type && it.recoveryDay == recoveryDay }
                wounded.removeAll { it.type == type && it.recoveryDay == recoveryDay }
                wounded += WoundedCohort(previous?.id ?: "${id}_wounded_${type.name}", type, sum(previous?.soldiers ?: 0, survivingInjured), recoveryDay, troops.maxOf { it.experience }, troops.minOf { it.equipment })
            }
            if (captured > 0) {
                val previous = captives.firstOrNull { it.own && it.type == type }
                captives.removeAll { it.own && it.type == type }
                captives += CaptiveGroup(previous?.id ?: "${id}_own_${type.name}", session.enemy, sum(previous?.soldiers ?: 0, captured), true, type, state.day)
            }
        }
        if (victory) {
            val caught = (session.enemyStart - session.enemyRemaining) / if (session.inputs.lastOrNull()?.decision == BattleDecision.PURSUE) 8 else 15
            if (caught > 0) {
                val roster = session.enemyUnits.ifEmpty { listOf(UnitAllocation(UnitType.HUMAN_SWORD, session.enemyStart)) }
                var remaining = caught
                roster.forEachIndexed { index, unit ->
                    val amount = if (index == roster.lastIndex) remaining else (caught.toLong() * unit.amount / session.enemyStart.coerceAtLeast(1)).toInt()
                    remaining -= amount
                    val type = if (session.enemyUnits.isEmpty()) null else unit.type
                    if (amount > 0) {
                        val previous = captives.firstOrNull { !it.own && it.enemy == session.enemy && it.factionId == session.enemyFactionId && it.type == type }
                        captives.removeAll { !it.own && it.enemy == session.enemy && it.factionId == session.enemyFactionId && it.type == type }
                        captives += CaptiveGroup(previous?.id ?: "${id}_enemy_${type?.name ?: "unknown"}", session.enemy, sum(previous?.soldiers ?: 0, amount), type = type, capturedDay = state.day, factionId = session.enemyFactionId)
                    }
                }
            }
        }
        val casualties = CasualtyReport(reports.sumOf { it.dead }, reports.sumOf { it.wounded }, reports.sumOf { it.missing }, reports.sumOf { it.captured })
        val rng = Random(session.seed xor 0x574152)
        val conditions = state.war.commanderConditions.toMutableList()
        session.contingents.mapNotNull { it.commanderId }.distinct().forEach { commanderId ->
            val troops = session.contingents.filter { it.commanderId == commanderId }
            if (troops.any { it.commanderWounded }) {
                val rescued = troops.any { it.commanderRescued }
                val status = when {
                    !victory && !rescued && !session.orderedRetreat && rng.nextDouble() < 0.40 -> CombatantStatus.CAPTURED
                    state.settings.permadeath && !rescued && rng.nextDouble() < 0.08 -> CombatantStatus.DEAD
                    !rescued && rng.nextBoolean() -> CombatantStatus.UNCONSCIOUS
                    else -> CombatantStatus.WOUNDED
                }
                conditions.removeAll { it.commanderId == commanderId }
                conditions += CommanderCondition(commanderId, status, state.day + if (rescued) 3 else 7)
            }
        }
        var playerStatus = state.war.playerCondition
        if (session.participation == BattleParticipation.PERSONAL && session.minute > 0 && rng.nextDouble() < 0.10 + casualties.total.toDouble() / session.ownStart.coerceAtLeast(1) * 0.4) {
            playerStatus = when {
                !victory && !session.orderedRetreat && rng.nextDouble() < 0.25 -> CombatantStatus.CAPTURED
                state.settings.permadeath && state.settings.dynasty && rng.nextDouble() < 0.04 -> CombatantStatus.DEAD
                else -> CombatantStatus.WOUNDED
            }
        }
        val damage = state.war.buildingDamage.toMutableMap()
        if (session.tactic == Tactic.FORTIFY) {
            val integrityLost = ((session.replayStart?.realm?.wallIntegrity ?: 100) - session.wallIntegrity).coerceAtLeast(0)
            state.realm.buildings.filterValues { it > 0 }.keys.forEach { building ->
                val suffered = when (building) { BuildingType.WALL -> integrityLost; BuildingType.TOWER -> integrityLost / 2; BuildingType.BARRACKS, BuildingType.MARKET, BuildingType.RESIDENTIAL -> integrityLost / 3; else -> integrityLost / 5 }
                if (suffered > 0) damage[building] = ((damage[building] ?: 0) + suffered).coerceAtMost(100)
            }
            var civilLoss = state.civilianPopulation.toLong() * integrityLost / 2000
            if (state.war.civiliansEvacuated) civilLoss /= 5
            Culture.entries.forEach { culture ->
                val lost = (civilLoss * ArmyEngine.population(state.population, culture) / state.population.total.coerceAtLeast(1)).toInt()
                val civilianCapacity = (ArmyEngine.population(population, culture) - state.armyPools.filter { it.type.culture == culture }.sumOf { it.soldiers } - state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount } - population.recruits(culture) - wounded.filter { it.type.culture == culture }.sumOf { it.soldiers } - captives.filter { it.own && it.type?.culture == culture }.sumOf { it.soldiers }).coerceAtLeast(0)
                population = ArmyEngine.adjustPopulation(population, culture, -minOf(lost, civilianCapacity))
            }
        }
        val elites = state.war.eliteUnits.map { elite ->
            val troops = session.contingents.filter { it.type == elite.type }
            if (troops.isEmpty()) elite else {
                val count = elite.soldiers.coerceAtMost(state.soldiers(elite.type))
                val battles = elite.battles + 1
                elite.copy(soldiers = count, battles = battles, victories = elite.victories + if (victory) 1 else 0, traits = if (victory && battles >= 5) (elite.traits + "Unbeugsam").distinct() else elite.traits, history = (elite.history + "Tag ${state.day}: ${if (victory) "Sieg" else "Niederlage"}, $count verbleiben").takeLast(20))
            }
        }
        val final = session.copy(casualties = casualties)
        val record = BattleRecord(id, state.day, session.location ?: state.realm.settlementName, session.enemy, session.seed, session.tactic, session.ownStart, session.enemyStart, session.ownRemaining, session.enemyRemaining, victory, session.minute, casualties, session.contingents.mapNotNull { it.commanderId }.distinct(), session.log.filter { it.text.contains("Kommandant") || it.text.contains("bricht") || it.text.contains("Rückzug") }.map { it.text }.takeLast(12), session.terrain, session.participation, session.replayStart, session.inputs, session.enemyFactionName, session.enemyUnits, session.enemyFactionId, session.enemyArmyName,
            outcomeGrade = session.outcomeGrade, segments = session.segments, siegeDevices = session.siegeDevices,
            exchanges = session.exchanges, arrowsUsed = (session.battleArrowsLoaded - session.battleArrowsRemaining).coerceAtLeast(0),
            weaponChargesUsed = session.weaponChargesUsed, plan = session.plan,
            enemyCasualties = BattleReportEngine.enemyCasualties(session),
            enemyFled = if (victory) session.enemyRemaining else session.fronts.filter { it.intent == BattleAiIntent.WITHDRAW }.sumOf { it.enemySoldiers },
            ownFled = session.contingents.filter { it.routed }.sumOf { it.soldiers })
        val records = state.war.history + record
        val history = records.mapIndexed { index, entry ->
            if (index < records.size - 24 && entry.replay != null) entry.copy(replay = null, inputs = emptyList()) else entry
        }
        return ArmyEngine.clampAssignments(
            state.copy(
                population = population,
                militaryStock = militaryStock,
                battleSession = final,
                war =
                    state.war.copy(
                        wounded = wounded,
                        captives = captives,
                        commanderConditions = conditions,
                        playerCondition = playerStatus,
                        playerRecoveryDay = state.day + 5,
                        buildingDamage = damage,
                        eliteUnits = elites,
                        history = history,
                        gateReinforcement =
                            if (session.tactic == Tactic.FORTIFY) 0
                            else state.war.gateReinforcement,
                        civiliansEvacuated =
                            if (session.tactic == Tactic.FORTIFY) false
                            else state.war.civiliansEvacuated,
                    ),
            )
        )
    }
}
