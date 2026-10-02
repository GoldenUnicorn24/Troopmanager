package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil

/** Border quantities reserve existing troops. Invasion banners only project their world army. */
object FrontierEngine {
    data class WallVolleyResult(val state: GameState, val battle: BattleSession)

    private fun result(state: GameState, message: String) = GameEngine.ActionResult(state, message)
    private fun log(state: GameState, title: String, text: String): GameState =
        state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(2000))

    fun tick(state: GameState): GameState {
        if (state.battleSession?.isActive == true || state.frontier.lastTickDay >= state.day) return projectInvasion(state)
        var next = ensureAlliedSources(state)
        next = next.copy(frontier = next.frontier.copy(lastTickDay = next.day))
        val travelling = mutableListOf<AllyReinforcement>()
        for (aid in next.frontier.reinforcements) {
            val remaining = (aid.arrivalDay - next.day).coerceAtLeast(0)
            if (remaining > 0) travelling += aid.copy(daysRemaining = remaining)
            else {
                next = ArmyEngine.add(next, aid.type, aid.amount, experience = if (aid.emergency) 35 else 20)
                next = next.copy(population = ArmyEngine.adjustPopulation(next.population, aid.type.culture, aid.amount))
                next = log(next, "Verbündete angekommen", "${aid.amount} ${aid.type.label} aus ${aid.origin} erreichen die Mauer nach ihrer Reise und stehen jetzt im Heer.")
            }
        }
        next = next.copy(frontier = next.frontier.copy(reinforcements = travelling,
            allies = next.frontier.allies.map { if (next.day % 7 == 0) it.copy(stock = (it.stock + 15).coerceAtMost(300)) else it }))
        val weapons = next.frontier.weapons.map { stock ->
            if (stock.daysRemaining <= 0) stock else {
                val left = stock.daysRemaining - 1
                if (left == 0) {
                    next = log(next, "Mauerwaffe einsatzbereit", "${stock.type.label} steht am ${stock.section.label}; die erste Ladung ist vorbereitet.")
                    stock.copy(count = stock.count + 1, daysRemaining = 0,
                        ammunition = stock.ammunition + ammunitionCapacity(stock.type), integrity = stock.integrity.coerceIn(0, 100))
                } else stock.copy(daysRemaining = left)
            }
        }
        val designs = next.frontier.designs.map { original ->
            val d = canonicalDesign(original)
            if (d.trainingAmount <= 0) d else if (d.trainingDaysLeft > 1) d.copy(trainingDaysLeft = d.trainingDaysLeft - 1)
            else {
                next = ArmyEngine.add(next, d.unitType, d.trainingAmount, experience = 20)
                next = log(next, "Eigene Einheit ausgebildet", "${d.trainingAmount} ${d.name} treten als ${d.role.label} dem Heer bei; ihre Ausrüstung und Versorgung sind im regulären Heer wirksam.")
                d.copy(soldiers = (d.soldiers.toLong() + d.trainingAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), trainingDaysLeft = 0, trainingAmount = 0)
            }
        }
        next = next.copy(frontier = next.frontier.copy(weapons = weapons, designs = designs))

        var patrol = next.frontier.patrol?.let { p ->
            if (p.units.isNotEmpty()) p else {
                var left = p.soldiers
                val allocations = UnitType.entries.mapNotNull { type -> val n = minOf(left, next.directCommand(type)); left -= n; if (n > 0) UnitAllocation(type, n) else null }
                p.copy(soldiers = allocations.sumOf { it.amount }, units = allocations)
            }
        }
        next = next.copy(frontier = next.frontier.copy(patrol = patrol))
        val remainingHordes = mutableListOf<HordeBanner>()
        next.frontier.hordes.filterNot { it.id.startsWith("invasion-") }.forEach { horde ->
            val moving = horde.copy(daysToArrival = (horde.daysToArrival - 1).coerceAtLeast(0))
            val scout = if (patrol != null) .12 else .28
            val observed = moving.copy(estimateMinimum = (moving.soldiers * (1 - scout)).toInt(), estimateMaximum = ceil(moving.soldiers * (1 + scout)).toInt())
            if (observed.daysToArrival > 0) remainingHordes += observed
            else if (patrol != null && observed.soldiers <= 100 && patrol!!.soldiers >= observed.soldiers * 3 / 2) {
                val p = patrol!!
                val losses = (observed.soldiers / 12).coerceAtLeast(1).coerceAtMost(p.soldiers)
                val lost = distributeLosses(p.units, losses)
                next = ArmyEngine.applyLosses(next, lost)
                val survivors = p.units.mapNotNull { u -> val n = u.amount - (lost.firstOrNull { it.type == u.type }?.amount ?: 0); if (n > 0) u.copy(amount = n) else null }
                patrol = p.copy(soldiers = survivors.sumOf { it.amount }, units = survivors)
                next = next.copy(frontier = next.frontier.copy(patrol = patrol))
                next = log(next, "Patrouille fängt Orküberfall ab", "Die echte Grenzpatrouille stoppt ${observed.name} (${observed.soldiers} Orks); $losses eigene Soldaten fallen. Großhorden müssen weiterhin an der Mauer bekämpft werden.")
                next = RelationshipEngine.remember(next, "frontier_patrol", "Die Grenzpatrouille bewahrte die Dörfer vor einem Orküberfall.", 2, setOf("frontier", "military"))
            } else if (next.battleSession?.isActive == true || next.invasion != null) remainingHordes += observed
            else if (next.homeArmySize > 0) {
                next = next.copy(frontier = next.frontier.copy(patrol = patrol))
                val enemy = when (observed.kind) { HordeKind.ORC -> EnemyType.ORC; HordeKind.URUK -> EnemyType.URUK; HordeKind.TAO_TEI -> EnemyType.TAO_TEI }
                next = BattleEngine.start(next, enemy, Tactic.FORTIFY, enemyStrength = observed.soldiers,
                    enemyArmyName = observed.name, location = next.realm.settlementName, seed = observed.id.hashCode()).state
                next = log(next, "Orküberfall an der Mauer", "${observed.name} erreicht die Feste; die Garnison tritt zur Verteidigung an.")
            } else {
                next = next.copy(resources = EconomyEngine.add(next.resources, Resources(0, -observed.soldiers * 4, -observed.soldiers, 0, 0)),
                    realm = next.realm.copy(wallIntegrity = (next.realm.wallIntegrity - 4).coerceAtLeast(0)))
                next = log(next, "Grenzüberfall plündert Vorräte", "Ohne freie Garnison plündern ${observed.soldiers} Orks Nahrung und Holz vor der Mauer.")
            }
        }
        patrol = patrol?.let {
            if (it.daysLeft <= 1 || it.soldiers <= 0) {
                next = log(next, "Grenzpatrouille zurück", "${it.soldiers} Soldaten kehren in die Garnison zurück und sind wieder verfügbar.")
                null
            } else it.copy(daysLeft = it.daysLeft - 1)
        }
        if (next.day in 5..24 && next.day - next.frontier.lastRaidDay >= 8 && next.battleSession?.isActive != true) {
            val amount = (30 + next.day * 2).coerceAtMost(78)
            val id = "raid-${next.day}"
            remainingHordes += HordeBanner(id, HordeKind.ORC, "Orküberfall aus dem Aschenforst", amount,
                "Aschenforst", 4, regionId = next.world.places.firstOrNull { it.ownerId == "ash_covenant" }?.id ?: "keep",
                estimateMinimum = amount * 3 / 4, estimateMaximum = amount * 5 / 4)
            next = next.copy(frontier = next.frontier.copy(lastRaidDay = next.day))
            next = log(next, "Grenzspäher entdecken Orks", "Ein kleiner Orküberfall zieht zur Mauer. Schätzung ${amount * 3 / 4}–${amount * 5 / 4}; etwa vier Tage Vorwarnung.")
        }
        next = next.copy(frontier = next.frontier.copy(patrol = patrol, hordes = remainingHordes))
        return pressureOtherRealms(projectInvasion(next))
    }

    private fun projectInvasion(state: GameState): GameState {
        val raids = state.frontier.hordes.filterNot { it.id.startsWith("invasion-") }
        val invasion = state.invasion ?: return state.copy(frontier = state.frontier.copy(hordes = raids))
        val army = state.world.armies.firstOrNull { it.id == invasion.worldArmyId }
        val kind = when (invasion.enemy) { EnemyType.ORC -> HordeKind.ORC; EnemyType.URUK -> HordeKind.URUK; EnemyType.TAO_TEI -> HordeKind.TAO_TEI }
        val total = army?.total ?: invasion.strength
        val uncertainty = if (state.frontier.patrol != null || state.realm.scoutingDays > 0) .1 else .25
        val combined = state.day >= 45 && kind != HordeKind.TAO_TEI
        val banner = HordeBanner("invasion-${invasion.announcedDay}-${army?.id.orEmpty()}", kind,
            if (combined) "Gemeinsames Ork- und Uruk-Banner" else if (kind == HordeKind.TAO_TEI) "Tao-Tei-Schwarm – Feind aller Reiche" else "${kind.label}: ${army?.name ?: "Grenzhorde"}",
            total, state.world.place(army?.regionId.orEmpty())?.name ?: "Grenzland",
            (invasion.arrivalDay - state.day).coerceAtLeast(0), jointWith = if (combined) "Orks / Uruk-hai" else null,
            target = state.realm.settlementName, worldArmyId = army?.id, regionId = army?.regionId ?: "keep",
            estimateMinimum = (total * (1 - uncertainty)).toInt(), estimateMaximum = (total * (1 + uncertainty)).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt())
        return state.copy(frontier = state.frontier.copy(hordes = raids + banner))
    }

    /** The announced swarm is one existing force; its passage can hurt any realm on that route. */
    private fun pressureOtherRealms(state: GameState): GameState {
        val horde = state.frontier.hordes.firstOrNull { it.kind.hostileToAll && it.worldArmyId != null } ?: return state
        val place = state.world.place(horde.regionId) ?: return state
        if (place.id == "keep" || place.ownerId == PLAYER_FACTION) return state
        val faction = state.world.faction(place.ownerId) ?: return state
        val key = "${horde.id}:${place.id}"
        if (key in state.frontier.hordePressureKeys) return state
        val foodLost = minOf(faction.food, (horde.soldiers.toLong() * 2).coerceAtMost(600).toInt())
        val next = state.copy(world = state.world.copy(
            factions = state.world.factions.map { if (it.id == faction.id) it.copy(food = it.food - foodLost, lastDecision = "Tao-Tei-Durchzug abwehren", lastDecisionDay = state.day) else it },
            places = state.world.places.map { if (it.id == place.id) it.copy(prosperity = (it.prosperity - 5).coerceAtLeast(0)) else it }),
            frontier = state.frontier.copy(hordePressureKeys = (state.frontier.hordePressureKeys + key).toList().takeLast(1000).toSet()))
        return log(next, "Tao Tei bedrohen anderes Reich", "Derselbe entdeckte Schwarm verwüstet ${place.name}: ${faction.name} verliert $foodLost Nahrung und örtlichen Wohlstand. Tao Tei verschonen keine Fraktion.")
    }

    private fun ensureAlliedSources(state: GameState): GameState {
        if (!state.world.initialized) return state
        var world = state.world
        listOf(Triple("frontier_gold_elves", "frontier_gold_court", AllyPeople.GOLD_ELVES),
            Triple("frontier_wall_envoys", "frontier_wall_camp", AllyPeople.WALL_ENVOYS)).forEachIndexed { index, (factionId, placeId, people) ->
            if (world.factions.none { it.id == factionId }) {
                val place = WorldPlace(placeId, if (index == 0) "Hof der Goldelben" else "Lager der Mauerlegion", PlaceKind.CAPITAL,
                    WorldTerrain.CITY, if (index == 0) .86f else .22f, .18f, factionId, population = 1800, fortification = 35)
                world = world.copy(places = world.places + place,
                    roads = world.roads + WorldRoad("road_$placeId", placeId, "keep", if (index == 0) 90 else 60),
                    factions = world.factions + WorldFaction(factionId, people.label, people.culture, placeId, "Rat der ${people.culture.label}",
                        FactionPersonality.DIPLOMATIC, "Grenzhilfe", "Frieden und sichere Handelswege", relations = mapOf(PLAYER_FACTION to 65), aggression = 5))
            }
        }
        return DiplomacyEngine.initialize(state.copy(world = world))
    }

    private fun allyType(people: AllyPeople) = when (people) { AllyPeople.GOLD_ELVES -> UnitType.GOLD_SPEAR; AllyPeople.FREE_HOLDS -> UnitType.HUMAN_SWORD; AllyPeople.WALL_ENVOYS -> UnitType.CRANE_GUARD }
    private fun allyFaction(people: AllyPeople) = when (people) { AllyPeople.GOLD_ELVES -> "frontier_gold_elves"; AllyPeople.FREE_HOLDS -> NEUTRAL_FACTION; AllyPeople.WALL_ENVOYS -> "frontier_wall_envoys" }
    private fun allyOrigin(state: GameState, people: AllyPeople): String = state.world.faction(allyFaction(people))?.capitalId ?: when (people) { AllyPeople.GOLD_ELVES -> "frontier_gold_court"; AllyPeople.FREE_HOLDS -> "village"; AllyPeople.WALL_ENVOYS -> "frontier_wall_camp" }
    private fun availableAllyStock(state: GameState, pact: AllyPact): Int {
        val faction = state.world.faction(allyFaction(pact.people)) ?: return pact.stock
        val reserved = state.world.armies.filter { it.factionId == faction.id && it.status != WorldArmyStatus.DESTROYED }.sumOf { it.total.toLong() } +
            state.world.recruitments.filter { it.factionId == faction.id }.sumOf { it.amount.toLong() }
        return minOf(pact.stock.toLong(), (faction.population.toLong() - reserved).coerceAtLeast(0)).toInt()
    }
    fun reinforcementPrice(state: GameState, people: AllyPeople): Int {
        val trust = state.frontier.allies.firstOrNull { it.people == people }?.trust ?: 55
        val trade = if (DiplomacyEngine.hasTreaty(state, PLAYER_FACTION, allyFaction(people), TreatyKind.TRADE)) .9 else 1.0
        return ceil(allyType(people).goldCost * (1.5 - trust.coerceIn(0, 100) / 140.0) * trade).toInt().coerceAtLeast(1)
    }
    private fun allyBlocker(state: GameState, people: AllyPeople): String? = when {
        state.battleSession?.isActive == true -> "Verbündete können während der laufenden Schlacht nicht neu entsandt werden."
        state.world.initialized && DiplomacyEngine.atWar(state, allyFaction(people), PLAYER_FACTION) -> "Im Krieg mit diesem Partner ist keine Hilfe möglich."
        else -> null
    }
    fun buyReinforcements(state: GameState, people: AllyPeople, amount: Int): GameEngine.ActionResult {
        allyBlocker(state, people)?.let { return result(state, it) }
        val pact = state.frontier.allies.firstOrNull { it.people == people } ?: return result(state, "Bündnis unbekannt.")
        val available = availableAllyStock(state, pact)
        if (amount !in 1..300 || amount > available) return result(state, "Dieser Partner kann derzeit höchstens $available Soldaten stellen.")
        val price = reinforcementPrice(state, people).toLong() * amount
        if (price > state.resources.gold) return result(state, "Die Reise und Ausrüstung kosten $price Gold.")
        val paid = state.copy(resources = state.resources.copy(gold = state.resources.gold - price.toInt()))
        val next = dispatchAid(paid, pact.copy(stock = pact.stock - amount, trust = (pact.trust + 2).coerceAtMost(100), requestsWithoutReturn = 0), amount, false, price.toInt())
        return result(next, "$amount ${allyType(people).label} sind aus ${people.label} unterwegs; Ankunft an Tag ${next.frontier.reinforcements.last().arrivalDay}.")
    }
    fun requestAid(state: GameState, people: AllyPeople): GameEngine.ActionResult {
        allyBlocker(state, people)?.let { return result(state, it) }
        val pact = state.frontier.allies.firstOrNull { it.people == people } ?: return result(state, "Bündnis unbekannt.")
        if (state.invasion == null && state.frontier.hordes.isEmpty() && state.realm.threat < 60) return result(state, "Notfallhilfe ist für eine erkennbare Grenzgefahr vorgesehen.")
        val available = availableAllyStock(state, pact)
        if (pact.trust < 55 || available < 20) return result(state, "Für Notfallhilfe fehlen Vertrauen oder verfügbare Truppen; eine Gegenleistung stärkt das Bündnis.")
        if (state.day - pact.lastAidDay < 12) return result(state, "Dieser Partner erholt sich noch von der letzten Hilfe (12 Tage Abstand).")
        val amount = minOf(available, 40 + pact.trust)
        val updated = pact.copy(stock = pact.stock - amount, lastAidDay = state.day, trust = (pact.trust - 8 - pact.requestsWithoutReturn * 3).coerceAtLeast(0), requestsWithoutReturn = pact.requestsWithoutReturn + 1)
        return result(dispatchAid(state, updated, amount, true), "$amount Verbündete brechen zur Notfallhilfe auf; auch Hilfe braucht eine Reise. Wiederholte Forderungen belasten das Vertrauen.")
    }
    fun contributeToAlly(state: GameState, people: AllyPeople): GameEngine.ActionResult {
        allyBlocker(state, people)?.let { return result(state, it) }
        if (state.resources.gold < 400) return result(state, "Die Gegenleistung für Vorräte und Ausrüstung kostet 400 Gold.")
        if (state.frontier.allies.none { it.people == people }) return result(state, "Bündnis unbekannt.")
        var next = ensureAlliedSources(state).copy(resources = state.resources.copy(gold = state.resources.gold - 400),
            frontier = state.frontier.copy(allies = state.frontier.allies.map { if (it.people == people) it.copy(trust = (it.trust + 8).coerceAtMost(100), contributions = it.contributions + 1, requestsWithoutReturn = 0) else it }))
        next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == allyFaction(people)) it.copy(gold = (it.gold.toLong() + 400).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else it }))
        if (next.world.initialized) next = DiplomacyEngine.changeRelation(next, PLAYER_FACTION, allyFaction(people), 5, 8, "Gegenleistung für Grenzhilfe")
        return result(log(next, "Bündnis gestärkt", "400 Gold unterstützen ${people.label}; Vertrauen und gegenseitige Hilfe wachsen."), "Die Gegenleistung stärkt das Vertrauen um 8.")
    }
    private fun dispatchAid(state: GameState, pact: AllyPact, amount: Int, emergency: Boolean, goldTransfer: Int = 0): GameState {
        val base = ensureAlliedSources(state)
        val origin = allyOrigin(base, pact.people)
        val route = WorldEngine.route(base.world, origin, "keep")
        val distance = route.zipWithNext().sumOf { (a, b) -> base.world.roads.firstOrNull { it.connects(a) && it.other(a) == b }?.distance ?: 30 }
        val days = maxOf(if (pact.people == AllyPeople.GOLD_ELVES) 5 else 3, ceil(distance / 22.0).toInt())
        val aid = AllyReinforcement(base.frontier.nextReinforcementId, pact.people, allyType(pact.people), amount,
            base.world.place(origin)?.name ?: pact.people.label, origin, days, base.day + days, emergency, base.day)
        val factionId = allyFaction(pact.people)
        val world = base.world.copy(factions = base.world.factions.map { f -> if (f.id == factionId) f.copy(population = (f.population - amount).coerceAtLeast(0), gold = (f.gold.toLong() + goldTransfer).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else f })
        var next = base.copy(world = world, frontier = base.frontier.copy(allies = base.frontier.allies.map { if (it.people == pact.people) pact else it },
            reinforcements = base.frontier.reinforcements + aid, nextReinforcementId = base.frontier.nextReinforcementId + 1))
        if (next.world.initialized) next = DiplomacyEngine.changeRelation(next, PLAYER_FACTION, factionId, if (emergency) -2 else 2,
            pact.trust - (base.frontier.allies.firstOrNull { it.people == pact.people }?.trust ?: pact.trust), if (emergency) "Notfallhilfe für die Mauer" else "Ausgerüstete Verstärkung verkauft")
        return log(next,
            if (emergency) "Verbündete leisten Grenzhilfe" else "Verbündete entsandt", "${aid.amount} ${aid.type.label} reisen aus ${aid.origin} zur Mauer; Ankunft Tag ${aid.arrivalDay}.")
    }

    fun availablePatrolSoldiers(state: GameState): Int = UnitType.entries.sumOf { state.directCommand(it).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    fun sendPatrol(state: GameState, amount: Int, days: Int = 5): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true || state.frontier.patrol != null) return result(state, "Es läuft bereits eine Patrouille oder Schlacht.")
        if (amount !in 10..500 || days !in 2..12 || amount > availablePatrolSoldiers(state)) return result(state, "Patrouille: 10–500 freie Soldaten für 2–12 Tage; derzeit ${availablePatrolSoldiers(state)} verfügbar.")
        var left = amount
        val units = UnitType.entries.mapNotNull { type -> val n = minOf(left, state.directCommand(type)); left -= n; if (n > 0) UnitAllocation(type, n) else null }
        val food = amount.toLong() * days
        if (food > state.resources.food) return result(state, "Die Patrouille benötigt $food Nahrung.")
        val next = state.copy(resources = state.resources.copy(food = state.resources.food - food.toInt()),
            frontier = state.frontier.copy(patrol = BorderPatrol(amount, days, units)))
        return result(log(next, "Grenzpatrouille entsandt", "$amount echte Soldaten sichern für $days Tage die Dörfer; sie fehlen währenddessen in Garnison und anderen Einsätzen."), "Patrouille entsandt; $amount Soldaten sind reserviert.")
    }
    fun recallPatrol(state: GameState): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return result(state, "Während der Schlacht lässt sich die Grenzpatrouille nicht umverteilen.")
        val patrol = state.frontier.patrol ?: return result(state, "Keine Patrouille unterwegs.")
        return result(log(state.copy(frontier = state.frontier.copy(patrol = null)), "Patrouille zurückgerufen", "${patrol.soldiers} Soldaten kehren vom nahen Grenzring zur Feste zurück."), "Die Grenzpatrouille ist wieder verfügbar; verbrauchte Marschverpflegung bleibt verbraucht.")
    }
    private fun distributeLosses(units: List<UnitAllocation>, amount: Int): List<UnitAllocation> {
        var left = amount
        return units.mapNotNull { u -> val n = minOf(left, u.amount); left -= n; if (n > 0) u.copy(amount = n) else null }
    }

    fun ammunitionCapacity(type: WallWeaponType): Int = when (type) { WallWeaponType.REPEATER -> 8; WallWeaponType.FIRE_OIL -> 3; WallWeaponType.BLACK_POWDER -> 3; else -> 5 }
    private fun weaponGoods(type: WallWeaponType): Map<MilitaryGood, Int> = mapOf(MilitaryGood.SIEGE_PARTS to if (type == WallWeaponType.BLACK_POWDER) 10 else 4,
        MilitaryGood.ARROWS to if (type == WallWeaponType.REPEATER || type == WallWeaponType.BALLISTA) 60 else 0).filterValues { it > 0 }
    fun buildWallWeapon(state: GameState, type: WallWeaponType): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return result(state, "Mauerwaffen werden außerhalb einer Schlacht gebaut.")
        val old = state.frontier.weapons.firstOrNull { it.type == type } ?: WallWeaponStock(type)
        if (old.daysRemaining > 0) return result(state, "Diese Mauerwaffe wird bereits gebaut.")
        if (old.count >= 1000) return result(state, "Für diesen Waffentyp sind alle Mauerplätze belegt.")
        val goods = weaponGoods(type)
        if (state.resources.gold < type.gold || state.resources.wood < type.wood || state.resources.iron < type.iron || MilitaryEconomyEngine.missing(state.militaryStock, goods).isNotEmpty())
            return result(state, "Bau benötigt ${type.gold} Gold, ${type.wood} Holz, ${type.iron} Eisen und ${goods.entries.joinToString { "${it.value} ${it.key.label}" }}.")
        val stock = old.copy(daysRemaining = type.days, section = when (type) { WallWeaponType.BALLISTA, WallWeaponType.FIRE_OIL -> BattleSection.LEFT; WallWeaponType.CRANE_WINCH -> BattleSection.RIGHT; else -> BattleSection.CENTER })
        val next = state.copy(resources = state.resources.copy(gold = state.resources.gold - type.gold, wood = state.resources.wood - type.wood, iron = state.resources.iron - type.iron),
            militaryStock = MilitaryEconomyEngine.consume(state.militaryStock, goods), frontier = state.frontier.copy(weapons = state.frontier.weapons.filterNot { it.type == type } + stock))
        return result(log(next, "Mauerwaffe im Bau", "${type.label}: ${type.days} Tage Bauzeit, Material und Militärgüter sind gebunden."), "${type.label} wird gebaut.")
    }
    fun reloadWallWeapon(state: GameState, type: WallWeaponType): GameEngine.ActionResult {
        val old = state.frontier.weapons.firstOrNull { it.type == type } ?: return result(state, "Mauerwaffe unbekannt.")
        if (state.battleSession?.isActive == true || old.count <= 0) return result(state, "Munition wird für gebaute Waffen außerhalb der Schlacht vorbereitet.")
        val capacity = (old.count.toLong() * ammunitionCapacity(type)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val need = (capacity - old.ammunition).coerceAtLeast(0)
        if (need == 0) return result(state, "Die Mauerwaffe ist bereits voll geladen.")
        val good = if (type in listOf(WallWeaponType.BALLISTA, WallWeaponType.REPEATER)) MilitaryGood.ARROWS else MilitaryGood.SIEGE_PARTS
        val goods = mapOf(good to (need.toLong() * if (good == MilitaryGood.ARROWS) 12 else 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val gold = need.toLong() * if (type == WallWeaponType.BLACK_POWDER) 6 else 2
        if (state.resources.gold < gold || MilitaryEconomyEngine.missing(state.militaryStock, goods).isNotEmpty()) return result(state, "Nachladen kostet $gold Gold und ${goods.values.first()} ${good.label}.")
        return result(state.copy(resources = state.resources.copy(gold = state.resources.gold - gold.toInt()), militaryStock = MilitaryEconomyEngine.consume(state.militaryStock, goods),
            frontier = state.frontier.copy(weapons = state.frontier.weapons.map { if (it.type == type) it.copy(ammunition = capacity, reloadRounds = 0) else it })), "${type.label} ist geladen.")
    }
    fun repairWallWeapon(state: GameState, type: WallWeaponType): GameEngine.ActionResult {
        val old = state.frontier.weapons.firstOrNull { it.type == type } ?: return result(state, "Mauerwaffe unbekannt.")
        if (state.battleSession?.isActive == true || old.count <= 0 || old.integrity >= 100) return result(state, "Reparatur ist nach der Schlacht für beschädigte Waffen möglich.")
        val missing = 100 - old.integrity.coerceIn(0, 100)
        val parts = ceil(missing * old.count / 20.0).toInt().coerceAtLeast(1)
        val gold = parts.toLong() * 8
        if (state.resources.gold < gold || state.militaryStock.siegeParts < parts) return result(state, "Reparatur benötigt $gold Gold und $parts Belagerungsteile.")
        return result(state.copy(resources = state.resources.copy(gold = state.resources.gold - gold.toInt()), militaryStock = state.militaryStock.copy(siegeParts = state.militaryStock.siegeParts - parts),
            frontier = state.frontier.copy(weapons = state.frontier.weapons.map { if (it.type == type) it.copy(integrity = 100) else it })), "${type.label} ist repariert.")
    }
    fun isHomeFortifiedBattle(state: GameState, battle: BattleSession): Boolean = battle.tactic == Tactic.FORTIFY && state.world.encounter == null && (battle.location == null || battle.location == state.realm.settlementName)
    fun prepareBattle(state: GameState): GameState {
        val battle = state.battleSession ?: return state
        if (!isHomeFortifiedBattle(state, battle)) return state
        val ready = state.frontier.weapons.filter { it.count > 0 && it.integrity > 0 }
        if (ready.isEmpty()) return state
        return state.copy(frontier = state.frontier.copy(weapons = state.frontier.weapons.map { it.copy(reloadRounds = 0) }),
            battleSession = battle.copy(log = battle.log + BattleLogEntry(0, "Mauerabschnitte bereit: ${ready.joinToString { "${it.type.label} am ${it.section.label} (${it.ammunition} Ladungen)" }}.")))
    }
    fun fireWallWeapons(state: GameState, battle: BattleSession): WallVolleyResult {
        if (!isHomeFortifiedBattle(state, battle) || !battle.isActive || battle.phase == BattlePhase.PURSUIT) return WallVolleyResult(state, battle)
        var updated = battle
        val weapons = state.frontier.weapons.map { stock ->
            // A winch moves deployed soldiers; owning its mechanism never creates a crane corps.
            if (stock.reloadRounds > 0) stock.copy(reloadRounds = stock.reloadRounds - 1)
            else if (stock.count <= 0 || stock.ammunition <= 0 || stock.integrity <= 0 || updated.enemyRemaining <= 0 ||
                (stock.type == WallWeaponType.CRANE_WINCH && updated.contingents.none { it.type == UnitType.CRANE_GUARD && it.soldiers > 0 && !it.routed })) stock
            else {
                val craneTroops = updated.contingents.filter { it.type == UnitType.CRANE_GUARD && !it.routed }.sumOf { it.soldiers }
                val shots = minOf(stock.count, stock.ammunition, if (stock.type == WallWeaponType.CRANE_WINCH) maxOf(1, craneTroops / 5) else Int.MAX_VALUE)
                val raw = (shots.toLong() * stock.type.defense * stock.integrity.coerceIn(0, 100) / 200).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
                var left = raw
                val fronts = updated.fronts.sortedBy { if (it.section == stock.section) 0 else 1 }.map { front -> val losses = minOf(front.enemySoldiers, left); left -= losses; front.copy(enemySoldiers = front.enemySoldiers - losses) }
                val dealt = raw - left
                val crane = stock.type == WallWeaponType.CRANE_WINCH
                updated = updated.copy(fronts = fronts, log = updated.log + BattleLogEntry(updated.minute,
                    if (crane) "Kranichwinden lassen die blaue Garde am ${stock.section.label} ausfallen: $dealt Gegner ausgeschaltet. Seile brauchen eine neue Ladung."
                    else "${stock.type.label} am ${stock.section.label}: $dealt Gegner ausgeschaltet, $shots Ladungen verbraucht.", enemyLosses = dealt),
                    lastSounds = (updated.lastSounds + if (stock.type == WallWeaponType.FIRE_OIL) BattleSoundCue.FIRE else BattleSoundCue.ARTILLERY).distinct())
                stock.copy(ammunition = stock.ammunition - shots, reloadRounds = if (stock.type == WallWeaponType.REPEATER) 1 else 2)
            }
        }
        return WallVolleyResult(state.copy(frontier = state.frontier.copy(weapons = weapons)), updated)
    }
    fun afterBattle(state: GameState, battle: BattleSession): GameState {
        var next = state
        if (isHomeFortifiedBattle(state, battle)) {
            val damage = if (battle.status == BattleStatus.VICTORY) 8 else 25
            next = next.copy(frontier = next.frontier.copy(weapons = next.frontier.weapons.map { if (it.count > 0) it.copy(integrity = (it.integrity - damage).coerceAtLeast(0), reloadRounds = 0) else it }))
            if (next.frontier.weapons.any { it.count > 0 }) next = log(next, "Mauerwaffen nach Belagerung", "Die Geschütze sind nach der Belagerung beschädigt und haben begrenzte Restmunition. Reparatur und neue Ladungen benötigen Militärgüter.")
        }
        if (state.companion.met && battle.contingents.any { it.commanderId == COMPANION_COMMANDER_ID } && battle.participation == BattleParticipation.PERSONAL) {
            val bond = next.frontier.bond.copy(sharedBattles = next.frontier.bond.sharedBattles + 1)
            next = next.copy(frontier = next.frontier.copy(bond = bond.copy(stage = bondStage(bond))))
            next = RelationshipEngine.remember(next, "frontier_shared_battle", "Ihr standet gemeinsam in der Schlacht und kennt nun die Stärken und Grenzen des anderen besser.", 3, setOf("frontier", "bond"))
        }
        return next
    }

    /** Shared by battle, patrol, mission and supply loss paths; never subtract designs twice. */
    fun afterTroopLosses(before: GameState, after: GameState): GameState {
        val available = UnitType.entries.associateWith { after.soldiers(it) }.toMutableMap()
        val designs = after.frontier.designs.map { original ->
            val d = canonicalDesign(original)
            val oldPool = before.soldiers(d.unitType)
            val survivors = if (oldPool > 0) (d.soldiers.toLong() * after.soldiers(d.unitType) / oldPool).toInt() else 0
            val n = survivors.coerceIn(0, available.getValue(d.unitType))
            available[d.unitType] = available.getValue(d.unitType) - n
            d.copy(soldiers = n)
        }
        return after.copy(frontier = after.frontier.copy(designs = designs))
    }

    fun customUnitsUnlocked(state: GameState): Boolean = state.day >= 25 || state.victories >= 2 || state.renown >= 40 || state.realm.level(BuildingType.BARRACKS) >= 3
    fun customUnitValidation(culture: Culture, role: CustomUnitRole, weapon: CustomWeapon, shield: Boolean): String? {
        if (role == CustomUnitRole.CAVALRY && culture != Culture.HUMAN) return "Reiter benötigen menschliche Ritterausbildung und Pferde."
        if (role == CustomUnitRole.SIEGE && culture != Culture.WALL) return "Geschützmannschaften benötigen die Ausbildung der Mauerlegion."
        val allowed = when (role) { CustomUnitRole.INFANTRY, CustomUnitRole.CAVALRY -> setOf(CustomWeapon.SWORD, CustomWeapon.SPEAR); CustomUnitRole.RANGED -> setOf(CustomWeapon.BOW, CustomWeapon.CROSSBOW); CustomUnitRole.SIEGE -> setOf(CustomWeapon.ARTILLERY) }
        if (weapon !in allowed) return "Diese Waffe passt nicht zur gewählten Rolle."
        if (shield && role in setOf(CustomUnitRole.RANGED, CustomUnitRole.SIEGE)) return "Fernkampf und Geschütze benötigen beide Hände; ein Schild ist hier nicht möglich."
        return null
    }
    fun customUnitPreview(name: String, culture: Culture, role: CustomUnitRole, weapon: CustomWeapon, armor: CustomArmor, shield: Boolean,
        colorHex: String = "#D6B66B", portraitUri: String? = null): CustomUnitDesign {
        val type = when (role) {
            CustomUnitRole.CAVALRY -> UnitType.KNIGHT
            CustomUnitRole.SIEGE -> UnitType.DRAGON_ARTILLERY
            CustomUnitRole.RANGED -> when (culture) { Culture.HUMAN -> UnitType.HUMAN_ARCHER; Culture.WOOD_ELF -> UnitType.WOOD_RANGER; Culture.GOLD_ELF -> UnitType.GOLD_ARCHER; Culture.WALL -> UnitType.EAGLE_CORPS }
            CustomUnitRole.INFANTRY -> when (culture) { Culture.HUMAN -> UnitType.HUMAN_SWORD; Culture.WOOD_ELF -> UnitType.WOOD_BLADE; Culture.GOLD_ELF -> UnitType.GOLD_SPEAR; Culture.WALL -> if (weapon == CustomWeapon.SPEAR) UnitType.BEAR_CORPS else UnitType.TIGER_CORPS }
        }
        val attack = when (weapon) { CustomWeapon.SWORD -> 8; CustomWeapon.SPEAR -> 7; CustomWeapon.BOW -> 3; CustomWeapon.CROSSBOW -> 4; CustomWeapon.ARTILLERY -> 2 } + if (role == CustomUnitRole.CAVALRY) 2 else 0
        val defense = when (armor) { CustomArmor.LIGHT -> 4; CustomArmor.MAIL -> 7; CustomArmor.PLATE -> 10 } + if (shield) 3 else 0
        val ranged = when (weapon) { CustomWeapon.BOW -> 10; CustomWeapon.CROSSBOW -> 12; CustomWeapon.ARTILLERY -> 15; else -> 1 }
        return CustomUnitDesign(0, name.trim().take(40), culture, attack, defense, ranged, portraitUri,
            role = role, weapon = weapon, armor = armor, shield = shield, colorHex = colorHex, unitType = type)
    }
    private fun canonicalDesign(d: CustomUnitDesign): CustomUnitDesign = customUnitPreview(d.name, d.culture, d.role, d.weapon, d.armor, d.shield, d.colorHex, d.portraitUri)
        .copy(id = d.id, soldiers = d.soldiers.coerceAtLeast(0), trainingDaysLeft = d.trainingDaysLeft.coerceAtLeast(0), trainingAmount = d.trainingAmount.coerceAtLeast(0))
    fun designUnit(state: GameState, name: String, culture: Culture, role: CustomUnitRole, weapon: CustomWeapon, armor: CustomArmor, shield: Boolean,
        colorHex: String = "#D6B66B", portraitUri: String? = null): GameEngine.ActionResult {
        if (!customUnitsUnlocked(state)) return result(state, "Eigene Designs werden ab Tag 25, zwei Siegen, 40 Ruhm oder Kaserne Stufe 3 freigeschaltet.")
        if (state.battleSession?.isActive == true) return result(state, "Einheitsentwürfe werden außerhalb einer laufenden Schlacht erstellt.")
        if (name.trim().length !in 2..40 || !Regex("#[0-9A-Fa-f]{6}").matches(colorHex)) return result(state, "Name: 2–40 Zeichen. Farbe: #RRGGBB.")
        customUnitValidation(culture, role, weapon, shield)?.let { return result(state, it) }
        if (state.frontier.designs.size >= 20) return result(state, "Es sind höchstens 20 gespeicherte Designs möglich.")
        val design = customUnitPreview(name, culture, role, weapon, armor, shield, colorHex, portraitUri).copy(id = state.frontier.nextDesignId)
        return result(log(state.copy(frontier = state.frontier.copy(designs = state.frontier.designs + design, nextDesignId = state.frontier.nextDesignId + 1)),
            "Eigenes Einheitendesign", "${design.name}: ${role.label}, ${weapon.label}, ${armor.label}. Ausrüstung bestimmt Kosten und Stärke; Soldaten müssen erst ausgebildet werden."), "${design.name} gespeichert. Jetzt kann die Ausbildung beginnen.")
    }
    /** Compatibility facade: freely supplied stats never become combat stats. */
    fun designUnit(state: GameState, name: String, culture: Culture, attack: Int, defense: Int, ranged: Int, portraitUri: String?): GameEngine.ActionResult {
        if (listOf(attack, defense, ranged).any { it !in 0..15 }) return result(state, "Freie Extremwerte sind nicht zulässig; wähle konkrete Ausrüstung.")
        val role = if (ranged > attack) CustomUnitRole.RANGED else CustomUnitRole.INFANTRY
        return designUnit(state, name, culture, role, if (role == CustomUnitRole.RANGED) CustomWeapon.BOW else CustomWeapon.SWORD,
            if (defense >= 9) CustomArmor.PLATE else CustomArmor.MAIL, role == CustomUnitRole.INFANTRY, portraitUri = portraitUri)
    }
    fun customUnitRequirements(design: CustomUnitDesign, amount: Int): Map<MilitaryGood, Int> {
        val d = canonicalDesign(design); val n = amount.coerceIn(0, 1000)
        val goods = mutableMapOf<MilitaryGood, Int>()
        when (d.weapon) { CustomWeapon.SWORD -> goods[MilitaryGood.SWORDS] = n; CustomWeapon.SPEAR -> goods[MilitaryGood.SPEARS] = n;
            CustomWeapon.BOW, CustomWeapon.CROSSBOW -> { goods[MilitaryGood.BOWS] = n; goods[MilitaryGood.ARROWS] = n * if (d.weapon == CustomWeapon.CROSSBOW) 36 else 24 }; CustomWeapon.ARTILLERY -> { goods[MilitaryGood.SIEGE_PARTS] = n; goods[MilitaryGood.ARROWS] = n * 20 } }
        goods[MilitaryGood.ARMOR] = when (d.armor) { CustomArmor.LIGHT -> (n + 1) / 2; CustomArmor.MAIL -> n; CustomArmor.PLATE -> n * 2 }
        if (d.shield) goods[MilitaryGood.SHIELDS] = n
        if (d.role == CustomUnitRole.CAVALRY) goods[MilitaryGood.HORSES] = n
        return goods.filterValues { it > 0 }
    }
    fun trainCustomUnit(state: GameState, id: Long, amount: Int): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true || !customUnitsUnlocked(state)) return result(state, "Eigene Ausbildung ist derzeit nicht verfügbar.")
        val original = state.frontier.designs.firstOrNull { it.id == id } ?: return result(state, "Design nicht gefunden.")
        val d = canonicalDesign(original)
        customUnitValidation(d.culture, d.role, d.weapon, d.shield)?.let { return result(state, it) }
        if (d.trainingAmount > 0 || amount !in 1..1000) return result(state, "Pro Design läuft eine Ausbildung; die Menge muss zwischen 1 und 1000 liegen.")
        val available = ArmyEngine.recruitable(state, d.culture)
        if (amount > available) return result(state, "Für diese Kultur sind $available Rekruten frei.")
        val gold = d.goldCost.toLong() * amount
        val goods = customUnitRequirements(d, amount)
        if (gold > state.resources.gold || MilitaryEconomyEngine.missing(state.militaryStock, goods).isNotEmpty()) return result(state, "Ausbildung benötigt $gold Gold und ${goods.entries.joinToString { "${it.value} ${it.key.label}" }}.")
        val next = state.copy(resources = state.resources.copy(gold = state.resources.gold - gold.toInt()), militaryStock = MilitaryEconomyEngine.consume(state.militaryStock, goods),
            population = ArmyEngine.adjustRecruits(state.population, d.culture, -amount),
            frontier = state.frontier.copy(designs = state.frontier.designs.map { if (it.id == id) d.copy(trainingAmount = amount, trainingDaysLeft = d.trainingDays) else it }))
        return result(log(next, "Eigene Einheit in Ausbildung", "$amount ${d.name} erhalten ihre bezahlte Ausrüstung; Ausbildung dauert ${d.trainingDays} Tage."), "$amount ${d.name} werden ausgebildet, noch keine neuen einsatzfähigen Soldaten.")
    }
    fun customUnitPowerFactor(state: GameState, type: UnitType): Double {
        val total = state.soldiers(type)
        if (total <= 0) return 1.0
        var left = total
        var weighted = 0.0
        val base = (type.attack + type.defense + type.ranged).coerceAtLeast(1)
        state.frontier.designs.filter { canonicalDesign(it).unitType == type }.forEach { original ->
            val d = canonicalDesign(original); val n = minOf(left, d.soldiers); left -= n
            weighted += n * (d.powerEach.toDouble() / base).coerceIn(.6, 1.4)
        }
        return ((weighted + left) / total).coerceIn(.6, 1.4)
    }

    fun companionTrainingUnavailableReason(state: GameState): String? {
        if (!state.companion.met) return "Die Gefährtin muss zuerst kennengelernt werden."
        if (state.frontier.bond.lastTrainDay == state.day) return "Heute habt ihr bereits gemeinsam trainiert."
        return PresenceEngine.sharedActivityBlocker(state)
    }
    fun canTrainWithCompanion(state: GameState): Boolean = companionTrainingUnavailableReason(state) == null
    private fun bondStage(bond: BondVisual): String = when {
        bond.sessions >= 18 && bond.sharedBattles >= 3 -> "Unerschütterliches Duo"
        bond.sessions >= 9 || bond.sharedBattles >= 2 -> "Kampfgefährten"
        bond.sessions >= 3 -> "Eingespieltes Duo"
        else -> "Bekanntschaft"
    }
    fun trainWithCompanion(state: GameState): GameEngine.ActionResult {
        companionTrainingUnavailableReason(state)?.let { return result(state, it) }
        val bond = state.frontier.bond.copy(sessions = state.frontier.bond.sessions + 1, lastTrainDay = state.day)
        var next = state.copy(player = state.player.copy(sword = (state.player.sword + 1).coerceAtMost(100), tactics = (state.player.tactics + 1).coerceAtMost(100)),
            companion = state.companion.copy(sword = (state.companion.sword + 1).coerceAtMost(100), tactics = (state.companion.tactics + 1).coerceAtMost(100), respect = (state.companion.respect + 1).coerceAtMost(100)),
            frontier = state.frontier.copy(bond = bond.copy(stage = bondStage(bond))))
        next = RelationshipEngine.syncCommander(next)
        next = RelationshipEngine.remember(next, "frontier_training", "Beim Training an der Mauer übten wir Deckung und Rückzug als Duo. ${next.frontier.bond.stage}.", 2, setOf("frontier", "bond", "training"))
        return result(log(next, "Gemeinsames Grenztraining", "Beide verbessern Schwert und Taktik. ${bond.sessions} gemeinsame Übungen: ${next.frontier.bond.stage}."), "Ihr trainiert als ${next.frontier.bond.stage}; die romantische Beziehung wird dadurch nicht verändert.")
    }
    fun bondCombatFactor(state: GameState): Double = if (state.companion.met && !state.commanderAway(COMPANION_COMMANDER_ID) && !state.war.unavailableCommander(COMPANION_COMMANDER_ID))
        1.0 + minOf(3, state.frontier.bond.sessions / 3 + state.frontier.bond.sharedBattles) / 100.0 else 1.0

    fun validate(state: GameState) {
        val f = state.frontier
        require(f.lastTickDay in 0..state.day && f.lastRaidDay in 0..state.day && f.nextDesignId > 0 && f.nextReinforcementId > 0) { "Ungültige Frontier-Zeit oder Kennung." }
        require(f.allies.map { it.people }.distinct().size == f.allies.size && f.allies.all { it.trust in 0..100 && it.stock in 0..300 && it.requestsWithoutReturn >= 0 && it.contributions >= 0 }) { "Ungültige Bündniswerte." }
        require(f.weapons.map { it.type }.distinct().size == f.weapons.size && f.weapons.all { it.count in 0..1000 && it.daysRemaining in 0..it.type.days && it.ammunition in 0..(it.count * ammunitionCapacity(it.type)) && it.integrity in 0..100 && it.reloadRounds in 0..2 && it.section != BattleSection.RESERVE }) { "Ungültige Mauerwaffen." }
        require(f.hordes.map { it.id }.distinct().size == f.hordes.size && f.hordes.all { it.soldiers > 0 && it.daysToArrival >= 0 && it.estimateMinimum >= 0 && it.estimateMaximum >= it.estimateMinimum }) { "Ungültige Hordenmeldung." }
        f.patrol?.let { p ->
            require(p.soldiers in 1..500 && p.daysLeft in 1..12 && p.units.all { it.amount > 0 } && p.units.map { it.type }.distinct().size == p.units.size) { "Ungültige Grenzpatrouille." }
            // Empty allocations are accepted only for the additive v0.63 legacy model.
            require(p.units.isEmpty() || p.units.sumOf { it.amount } == p.soldiers) { "Patrouillensoldaten stimmen nicht überein." }
            p.units.forEach { require(it.amount <= state.soldiers(it.type)) { "Patrouille übersteigt den Truppenbestand." } }
        }
        UnitType.entries.forEach { type -> require(state.away(type) <= state.soldiers(type)) { "Gemeinsame Reservierungen übersteigen den Truppenbestand." } }
        require(state.awayArmySize <= state.armySize) { "Reservierte Armee übersteigt den Truppenbestand." }
        require(f.reinforcements.map { it.id }.distinct().size == f.reinforcements.size && f.reinforcements.all { it.id > 0 && it.amount in 1..300 && it.daysRemaining >= 0 && it.departureDay in 0..state.day && it.arrivalDay >= it.departureDay && it.type == allyType(it.people) }) { "Ungültige verbündete Reise." }
        require(f.designs.size <= 20 && f.designs.map { it.id }.distinct().size == f.designs.size && f.designs.all { it.id > 0 && it.name.isNotBlank() && it.name.length <= 40 && it.attack in 0..30 && it.defense in 0..30 && it.ranged in 0..30 && it.soldiers >= 0 && it.trainingDaysLeft in 0..24 && it.trainingAmount in 0..1000 && (it.trainingAmount > 0) == (it.trainingDaysLeft > 0) }) { "Ungültige eigene Ausbildung." }
        UnitType.entries.forEach { type -> require(f.designs.filter { canonicalDesign(it).unitType == type }.sumOf { it.soldiers.toLong() } <= state.soldiers(type)) { "Eigene Designs sind größer als ihr regulärer Truppenpool." } }
        Culture.entries.forEach { culture ->
            val reserved = state.armyPools.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } +
                state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount.toLong() } +
                f.designs.filter { it.culture == culture }.sumOf { it.trainingAmount.toLong() } +
                state.war.wounded.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } +
                state.war.captives.filter { it.own && it.type?.culture == culture }.sumOf { it.soldiers.toLong() }
            require(reserved <= ArmyEngine.population(state.population, culture)) { "Ausbildung und Heer übersteigen die Kulturbevölkerung." }
        }
        require(f.bond.sessions >= 0 && f.bond.sharedBattles >= 0 && f.bond.lastTrainDay in 0..state.day) { "Ungültige gemeinsame Ausbildung." }
    }
}
