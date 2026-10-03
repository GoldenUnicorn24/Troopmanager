package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil

/** Border quantities reserve existing troops. Invasion banners only project their world army. */
object FrontierEngine {
    data class WallVolleyResult(val state: GameState, val battle: BattleSession)
    data class CaptainOffer(
        val index: Int,
        val name: String,
        val culture: Culture,
        val leadership: Int,
        val tactics: Int,
        val trait: String,
        val cost: Int,
    )

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
        val borderControl = next.frontier.outposts.filter { it.integrity > 0 }.sumOf {
            when (it.type) {
                BorderOutpostType.WATCHTOWER -> it.level
                BorderOutpostType.FORTIFIED -> it.level * 2
                BorderOutpostType.SUPPLY -> 0
            }
        }
        val warningBonus = next.frontier.outposts
            .filter { it.type == BorderOutpostType.WATCHTOWER && it.integrity > 0 }
            .sumOf { it.level }
            .coerceAtMost(4)
        val grown = next.frontier.hordes.map { horde ->
            if (horde.id.startsWith("invasion-") || horde.daysToArrival <= 1) horde
            else {
                val baseGrowth = if (horde.kind == HordeKind.TAO_TEI) 8 else 4
                horde.copy(soldiers = horde.soldiers + (baseGrowth - borderControl).coerceAtLeast(1))
            }
        }.toMutableList()
        if (grown.none { !it.id.startsWith("invasion-") } && next.day in 4..18 && next.day % 4 == 0) {
            val small = 24 + next.day
            grown += HordeBanner(
                "raid-${next.day}",
                HordeKind.ORC,
                "Kleine Orkschar",
                small,
                "Vorland",
                3 + warningBonus,
                discovered = true,
                estimateMinimum = small - 6,
                estimateMaximum = small + 8,
            )
            next = log(next, "Späher", "Eine kleine Orkschar ist im Vorland. Wachtürme verschaffen zusätzliche Vorwarnzeit.")
        }
        if (next.day > 20 && next.day % 9 == 0 && grown.none { it.jointWith != null }) {
            grown += HordeBanner(
                "joint-${next.day}",
                HordeKind.URUK,
                "Orks und Uruks",
                80 + next.day,
                "Schwarzes Vorland",
                5 + warningBonus,
                jointWith = "Orks",
                discovered = true,
                estimateMinimum = 70,
                estimateMaximum = 140,
            )
        }
        // Persist growth and newly spawned banners before any later logic reads the frontier.
        next = next.copy(frontier = next.frontier.copy(hordes = grown))
        val nearest = next.frontier.hordes.minByOrNull { it.daysToArrival }
        val (goalKey, goal) = when {
            next.homeArmySize == 0 && next.invasion != null ->
                "invasion" to "Keine Soldaten an der Mauer. Stelle vor der Ankunft eine Garnison auf."
            nearest != null && nearest.daysToArrival <= 3 ->
                "patrol" to "${nearest.name} in ${nearest.daysToArrival} Tagen. Schick eine Patrouille oder bereite die Mauer vor."
            next.frontier.patrol == null ->
                "patrol" to "Schick eine Patrouille an die Grenze."
            next.trainingSize == 0 && next.homeArmySize < 80 ->
                "training" to "Bilde mindestens einen Trupp aus."
            next.resources.food < 200 ->
                "food" to "Sichere mindestens 200 Nahrung für Hof und Garnison."
            else ->
                "readiness" to "Halte mindestens 40 einsatzbereite Soldaten in der Heimat."
        }
        if (next.day - next.frontier.lastStoryDay >= 3 && next.frontier.pendingChoiceEvent == null) {
            val event = when ((next.day / 3) % 8) {
                0 -> FrontierChoiceEvent("caravan", "Karawane vor dem Tor", "Eine Handelskarawane bittet um Schutz bis zur nächsten Wegmarke.", "Geleit geben", "Wegzoll verlangen")
                1 -> FrontierChoiceEvent("gold_envoy", "Bote der Goldelben", "Ein Gesandter bietet Freiwillige an und erwartet eine höfische Antwort.", "Freiwillige aufnehmen", "Geschenk senden")
                2 -> FrontierChoiceEvent("refugees", "Flüchtlinge aus dem Vorland", "Familien suchen Schutz hinter deiner Mauer.", "Aufnehmen", "Weiterziehen lassen")
                3 -> FrontierChoiceEvent("deserters", "Deserteure", "Überläufer bringen Vorräte und kennen feindliche Wege.", "In die Rekruten aufnehmen", "Nur befragen")
                4 -> FrontierChoiceEvent("craftsmen", "Wandernde Handwerker", "Zimmerleute und Steinmetze bieten einen kurzen Vertrag an.", "Anheuern", "Ablehnen")
                5 -> FrontierChoiceEvent("scouts", "Freie Späher", "Erfahrene Kundschafter bieten gegen Sold neue Karten an.", "Späher bezahlen", "Gold sparen")
                6 -> FrontierChoiceEvent("wounded", "Verwundete Reisende", "Eine Gruppe Verwundeter bittet um Nahrung und einen sicheren Schlafplatz.", "Versorgen", "Abweisen")
                else -> FrontierChoiceEvent("smugglers", "Schmuggler am Fluss", "Händler bieten Eisen ohne Fragen an.", "Eisen kaufen", "Ware beschlagnahmen")
            }
            next = next.copy(frontier = next.frontier.copy(pendingChoiceEvent = event, lastStoryDay = next.day))
            next = log(next, event.title, event.text)
        }
        next = next.copy(frontier = next.frontier.copy(dailyGoal = goal, dailyGoalKey = goalKey, dailyGoalClaimed = false))
        if (next.day % 5 == 0 && next.resources.food > 300) {
            val room = 8 + next.realm.level(BuildingType.FARM) * 2
            val present = Culture.entries.filter { ArmyEngine.population(next.population, it) > 0 }
            val culture = present.maxByOrNull {
                (next.culturePatronage[it] ?: 0).toLong() * 100_000L + ArmyEngine.population(next.population, it)
            } ?: Culture.HUMAN
            var pop = ArmyEngine.adjustPopulation(next.population, culture, room)
            pop = ArmyEngine.adjustRecruits(pop, culture, room / 2)
            next = next.copy(population = pop)
            next = log(next, "Zuzug", "$room Angehörige der ${culture.label} kommen, weil Hof und Speicher tragen. Förderung und vorhandene Bevölkerung lenken den Zuzug.")
        }

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
        if (total <= 0) return state.copy(frontier = state.frontier.copy(hordes = raids))
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
        val supplyLevels = state.frontier.outposts.filter { it.type == BorderOutpostType.SUPPLY && it.integrity > 0 }.sumOf { it.level }
        val supplyFactor = (1.0 - supplyLevels * 0.10).coerceAtLeast(0.60)
        val food = kotlin.math.ceil(amount.toDouble() * days * supplyFactor).toLong()
        if (food > state.resources.food) return result(state, "Die Patrouille benötigt $food Nahrung. Versorgungsposten reduzieren den Bedarf.")
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
        state.frontier.emergencyUsed.filter { it.startsWith("levy-raised:") }.forEach { key ->
            val parts = key.split(":")
            val culture = runCatching { Culture.valueOf(parts[1]) }.getOrNull() ?: return@forEach
            val raised = parts.getOrNull(2)?.toIntOrNull() ?: return@forEach
            val type = when (culture) {
                Culture.HUMAN -> UnitType.HUMAN_SWORD
                Culture.WOOD_ELF -> UnitType.WOOD_BLADE
                Culture.GOLD_ELF -> UnitType.GOLD_SPEAR
                Culture.WALL -> UnitType.CRANE_GUARD
            }
            val pool = next.armyPools.firstOrNull { it.type == type }
            val back = raised.coerceAtMost(pool?.soldiers ?: 0)
            if (back > 0 && pool != null) {
                val left = pool.soldiers - back
                next = next.copy(armyPools = next.armyPools.filterNot { it.type == type } + listOfNotNull(if (left > 0) pool.copy(soldiers = left) else null),
                    population = ArmyEngine.adjustPopulation(next.population, culture, back))
            }
        }
        next = next.copy(frontier = next.frontier.copy(emergencyUsed = next.frontier.emergencyUsed.filterNot { it.startsWith("levy-raised:") }.toSet()))
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
        next = next.copy(frontier = next.frontier.copy(designs = next.frontier.designs.map { design ->
            val deployed = battle.contingents.filter { it.designId == design.id && it.startSoldiers > 0 }
            if (deployed.isEmpty()) design else design.copy(
                battles = design.battles + 1,
                victories = design.victories + if (battle.status == BattleStatus.VICTORY) 1 else 0,
                losses = (design.losses.toLong() + deployed.sumOf { (it.startSoldiers - it.soldiers).coerceAtLeast(0) })
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
        }))
        val campId = next.frontier.pendingCampAssaultId
        if (campId != null) {
            if (battle.status == BattleStatus.VICTORY) {
                val reward = next.frontier.pendingCampRewardGold.coerceAtLeast(0)
                next = next.copy(
                    resources = next.resources.copy(gold = (next.resources.gold.toLong() + reward).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                    frontier = next.frontier.copy(
                        hordes = next.frontier.hordes.filterNot { it.id == campId },
                        pendingCampAssaultId = null,
                        pendingCampRewardGold = 0,
                    ),
                )
                next = log(next, "Lager gefallen", "Der Lagersturm war erfolgreich. Das feindliche Lager ist vernichtet und $reward Gold Beute werden gesichert.")
            } else {
                next = next.copy(frontier = next.frontier.copy(pendingCampAssaultId = null, pendingCampRewardGold = 0))
                next = log(next, "Lager hält stand", "Der Angriff scheitert. Das Lager bleibt bestehen und kann weiter wachsen.")
            }
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

    fun customUnitsUnlocked(state: GameState): Boolean = state.day >= 40 || state.victories >= 3 || state.realm.level(BuildingType.BARRACKS) >= 4
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
        if (!customUnitsUnlocked(state)) return result(state, "Eigene Regimenter erst, wenn die Feste steht: Tag 40, drei Siege oder Kaserne Stufe 4.")
        if (state.battleSession?.isActive == true) return result(state, "Einheitsentwürfe werden außerhalb einer laufenden Schlacht erstellt.")
        if (name.trim().length !in 2..40 || !Regex("#[0-9A-Fa-f]{6}").matches(colorHex)) return result(state, "Name: 2–40 Zeichen. Farbe: #RRGGBB.")
        customUnitValidation(culture, role, weapon, shield)?.let { return result(state, it) }
        if (state.frontier.designs.size >= 20) return result(state, "Es sind höchstens 20 gespeicherte Designs möglich.")
        val foundingGold = 40 + role.ordinal * 15
        val foundingPeople = 2
        if (state.resources.gold < foundingGold) return result(state, "Der Entwurf kostet $foundingGold Gold. Die Ausbildung kommt danach extra.")
        if (state.population.recruits(culture) < foundingPeople) return result(state, "Der Stamm braucht $foundingPeople Rekruten aus ${culture.label}.")
        val design = customUnitPreview(name, culture, role, weapon, armor, shield, colorHex, portraitUri).copy(id = state.frontier.nextDesignId)
        val spent = state.population.takeRecruits(culture, foundingPeople)
        return result(log(state.copy(
            resources = state.resources.copy(gold = state.resources.gold - foundingGold),
            population = spent,
            frontier = state.frontier.copy(designs = state.frontier.designs + design, nextDesignId = state.frontier.nextDesignId + 1)),
            "Eigenes Einheitendesign", "${design.name}: ${role.label}, ${weapon.label}, ${armor.label}. Entwurf $foundingGold Gold und $foundingPeople Rekruten. Ausbildung kostet danach extra."), "${design.name} gespeichert. Entwurf: $foundingGold Gold, $foundingPeople Rekruten. Ausbildung bleibt teuer.")
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
    fun trainChild(state: GameState, memberId: String): GameEngine.ActionResult {
        val child = state.dynasty.members.firstOrNull { it.id == memberId && it.alive } ?: return result(state, "Kein Kind im Haus.")
        val age = child.age(state.day)
        if (age < 10) return result(state, "${child.name} ist $age. Leichtes Training erst ab 10.")
        val sessions = state.frontier.childSessions[memberId] ?: 0
        return result(state.copy(frontier = state.frontier.copy(childSessions = state.frontier.childSessions + (memberId to sessions + 1))),
            if (age < 16) "${child.name} übt leicht. Kampfeinsatz erst ab 16." else "${child.name} trainiert mit der Wache. Notensatz nur bei einem Banner.")
    }
    fun huntHorde(state: GameState, hordeId: String): GameEngine.ActionResult {
        val horde = state.frontier.hordes.firstOrNull { it.id == hordeId && it.discovered } ?: return result(state, "Dieses Banner ist nicht in Sicht.")
        val hunters = state.frontier.designs
            .filter { it.soldiers >= 10 && state.directCommand(it.unitType) >= 10 }
            .maxByOrNull { it.soldiers }
        val pool = hunters?.let { minOf(it.soldiers, state.directCommand(it.unitType)) } ?: availablePatrolSoldiers(state)
        if (pool < 10) return result(state, "Für eine Jagd brauchst du mindestens 10 freie Soldaten zuhause.")
        if (horde.kind == HordeKind.TAO_TEI && horde.daysToArrival > 2) return result(state, "Tao Tei sind noch zu weit. Warte, bis sie die Mauer erreichen, oder schick eine Patrouille.")
        val sent = minOf(40, pool)
        val killed = minOf(horde.soldiers, (sent * 2 / 3).coerceAtLeast(8))
        val ownLoss = (sent / 8).coerceAtLeast(1).coerceAtMost(sent)
        val losses = if (hunters != null) {
            listOf(UnitAllocation(hunters.unitType, ownLoss))
        } else {
            var left = ownLoss
            UnitType.entries.mapNotNull { type ->
                val amount = minOf(left, state.directCommand(type))
                left -= amount
                if (amount > 0) UnitAllocation(type, amount) else null
            }
        }
        val hordes = state.frontier.hordes.map {
            if (it.id != horde.id) it else it.copy(
                soldiers = (it.soldiers - killed).coerceAtLeast(0),
                daysToArrival = (it.daysToArrival + 1).coerceAtMost(12),
            )
        }.filter { it.soldiers > 0 }
        val beforeLoss = state.copy(frontier = state.frontier.copy(hordes = hordes))
        val next = afterTroopLosses(beforeLoss, ArmyEngine.applyLosses(beforeLoss, losses))
        return result(
            log(next, "Jagd", "${hunters?.name ?: "Die Wache"} stellt ${horde.name}. $killed Feinde fallen, $ownLoss eigene."),
            "${horde.kind.label} gejagt. $killed Feinde und $ownLoss eigene Soldaten fallen.",
        )
    }

    fun assaultCamp(state: GameState, hordeId: String): GameEngine.ActionResult {
        val horde = state.frontier.hordes.firstOrNull { it.id == hordeId && it.discovered } ?: return result(state, "Kein Lager in Sicht.")
        if (state.battleSession?.isActive == true || state.frontier.pendingCampAssaultId != null) return result(state, "Eine Schlacht oder ein Lagersturm läuft bereits.")
        if (state.homeArmySize < 20) return result(state, "Ein Sturm braucht mindestens 20 Soldaten zuhause.")
        if (state.resources.wood < 40) return result(state, "Der Sturm braucht 40 Holz für Leitern und Belagerung.")
        val enemy = when (horde.kind) { HordeKind.ORC -> EnemyType.ORC; HordeKind.URUK -> EnemyType.URUK; HordeKind.TAO_TEI -> EnemyType.TAO_TEI }
        if (state.homeArmySize * 2 < horde.soldiers) {
            val loss = (state.homeArmySize / 10).coerceAtLeast(1).coerceAtMost(state.homeArmySize)
            var left = loss
            val losses = UnitType.entries.mapNotNull { type ->
                val amount = minOf(left, state.directCommand(type))
                left -= amount
                if (amount > 0) UnitAllocation(type, amount) else null
            }
            val paid = state.copy(resources = state.resources.copy(wood = state.resources.wood - 40))
            val withLosses = afterTroopLosses(paid, ArmyEngine.applyLosses(paid, losses))
            return result(
                log(withLosses, "Sturm gescheitert", "${horde.name} ist zu stark. $loss Soldaten fallen beim Rückzug; das Holz ist verloren."),
                "Sturm gescheitert. $loss eigene Verluste; das Lager steht noch.",
            )
        }
        val delayed = state.frontier.hordes.map { if (it.id == horde.id) it else it.copy(daysToArrival = it.daysToArrival + 2) }
        val prepared = state.copy(
            resources = state.resources.copy(wood = state.resources.wood - 40),
            frontier = state.frontier.copy(
                hordes = delayed,
                pendingCampAssaultId = horde.id,
                pendingCampRewardGold = 80,
            ),
        )
        val battle = BattleEngine.start(
            prepared,
            enemy,
            Tactic.AGGRESSIVE,
            enemyStrength = horde.soldiers.coerceAtLeast(1),
            location = horde.name,
            enemyArmyName = horde.name,
            enemyFortification = 25,
            enemyFactionName = horde.kind.label,
        )
        return if (battle.state.battleSession == null) {
            result(
                prepared.copy(frontier = prepared.frontier.copy(pendingCampAssaultId = null, pendingCampRewardGold = 0)),
                battle.message,
            )
        } else {
            result(
                log(battle.state, "Lagersturm", "Du greifst ${horde.name} an. Andere Banner brauchen zwei Tage länger. 80 Gold gibt es erst nach einem Sieg."),
                "Sturm auf ${horde.name}. Beute und Lagervernichtung werden erst nach dem Sieg verbucht.",
            )
        }
    }



    fun nottruppe(state: GameState, culture: Culture): Int {
        val total = state.population.count(culture)
        val adults = total - total * 28 / 100
        val men = adults * 49 / 100
        val women = adults - men
        return men * 62 / 100 + women * 18 / 100
    }

    /** Only a siege with no soldiers at the wall arms civilians. They are weak and do not become a standing regiment. */
    fun armNottruppe(state: GameState): GameState {
        if (state.homeArmySize > 0 || state.invasion == null || state.day < state.invasion.arrivalDay) return state
        var next = state
        var raised = 0
        Culture.entries.forEach { culture ->
            val amount = (nottruppe(next, culture) / 2).coerceAtMost(180)
            if (amount < 8) return@forEach
            val type = when (culture) {
                Culture.HUMAN -> UnitType.HUMAN_SWORD
                Culture.WOOD_ELF -> UnitType.WOOD_BLADE
                Culture.GOLD_ELF -> UnitType.GOLD_SPEAR
                Culture.WALL -> UnitType.CRANE_GUARD
            }
            next = ArmyEngine.add(next, type, amount, experience = 2, morale = 38)
            next = next.copy(population = ArmyEngine.adjustRecruits(ArmyEngine.adjustPopulation(next.population, culture, -amount), culture, -amount),
                frontier = next.frontier.copy(emergencyUsed = next.frontier.emergencyUsed + "levy-raised:${culture.name}:$amount"))
            raised += amount
        }
        if (raised == 0) return state
        return log(next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction - 12).coerceAtLeast(8))),
            "Nottruppe", "Keine Soldaten an der Mauer. $raised Bürger greifen zu Speer und Bogen. Sie sind unerfahren und fehlen danach in der Stadt.")
    }
    fun buildOutpost(state: GameState, type: BorderOutpostType): GameEngine.ActionResult {
        if (state.frontier.outposts.size >= 3) return result(state, "Die Grenze kann derzeit höchstens drei feste Außenposten versorgen.")
        if (state.resources.gold < type.gold || state.resources.wood < type.wood || state.resources.stone < type.stone)
            return result(state, "${type.label} benötigt ${type.gold} Gold, ${type.wood} Holz und ${type.stone} Stein.")
        val id = state.frontier.nextOutpostId
        val outpost = BorderOutpost(id, type, "${type.label} $id")
        val next = state.copy(
            resources = state.resources.copy(
                gold = state.resources.gold - type.gold,
                wood = state.resources.wood - type.wood,
                stone = state.resources.stone - type.stone,
            ),
            frontier = state.frontier.copy(outposts = state.frontier.outposts + outpost, nextOutpostId = id + 1),
        )
        return result(log(next, "Außenposten errichtet", "${outpost.name} sichert die Grenze. Wachtürme geben frühere Warnung, Versorgungsposten senken Patrouillenkosten, Grenzforts bremsen Lagerwachstum."), "${outpost.name} errichtet.")
    }

    fun upgradeOutpost(state: GameState, id: Long): GameEngine.ActionResult {
        val outpost = state.frontier.outposts.firstOrNull { it.id == id } ?: return result(state, "Außenposten nicht gefunden.")
        if (outpost.level >= 3) return result(state, "${outpost.name} ist bereits Stufe 3.")
        val gold = outpost.type.gold / 2 * outpost.level
        val wood = outpost.type.wood / 2 * outpost.level
        val stone = outpost.type.stone / 2 * outpost.level
        if (state.resources.gold < gold || state.resources.wood < wood || state.resources.stone < stone)
            return result(state, "Ausbau benötigt $gold Gold, $wood Holz und $stone Stein.")
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - gold, wood = state.resources.wood - wood, stone = state.resources.stone - stone),
            frontier = state.frontier.copy(outposts = state.frontier.outposts.map { if (it.id == id) it.copy(level = it.level + 1, integrity = 100) else it }),
        )
        return result(next, "${outpost.name} auf Stufe ${outpost.level + 1} ausgebaut.")
    }

    fun repairOutpost(state: GameState, id: Long): GameEngine.ActionResult {
        val outpost = state.frontier.outposts.firstOrNull { it.id == id } ?: return result(state, "Außenposten nicht gefunden.")
        if (outpost.integrity >= 100) return result(state, "${outpost.name} ist unbeschädigt.")
        val missing = 100 - outpost.integrity
        val wood = missing
        val stone = missing / 2
        if (state.resources.wood < wood || state.resources.stone < stone) return result(state, "Reparatur benötigt $wood Holz und $stone Stein.")
        return result(
            state.copy(
                resources = state.resources.copy(wood = state.resources.wood - wood, stone = state.resources.stone - stone),
                frontier = state.frontier.copy(outposts = state.frontier.outposts.map { if (it.id == id) it.copy(integrity = 100) else it }),
            ),
            "${outpost.name} vollständig repariert.",
        )
    }

    fun resolveChoiceEvent(state: GameState, first: Boolean): GameEngine.ActionResult {
        val event = state.frontier.pendingChoiceEvent ?: return result(state, "Kein Grenzereignis wartet auf eine Entscheidung.")
        var next = state.copy(frontier = state.frontier.copy(pendingChoiceEvent = null))
        fun allyTrust(people: AllyPeople, delta: Int) {
            next = next.copy(frontier = next.frontier.copy(allies = next.frontier.allies.map {
                if (it.people == people) it.copy(trust = (it.trust + delta).coerceIn(0, 100)) else it
            }))
        }
        when (event.key) {
            "caravan" -> if (first) {
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold + 60))
                allyTrust(AllyPeople.FREE_HOLDS, 2)
            } else next = next.copy(resources = next.resources.copy(gold = next.resources.gold + 100), city = next.city.copy(satisfaction = (next.city.satisfaction - 3).coerceAtLeast(0)))
            "gold_envoy" -> if (first) {
                if (ArmyEngine.population(next.population, Culture.GOLD_ELF) > 0) next = next.copy(population = ArmyEngine.adjustRecruits(next.population, Culture.GOLD_ELF, 6))
                allyTrust(AllyPeople.GOLD_ELVES, 2)
            } else if (next.resources.gold >= 80) {
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 80)); allyTrust(AllyPeople.GOLD_ELVES, 6)
            }
            "refugees" -> if (first) {
                val culture = Culture.entries.maxByOrNull { ArmyEngine.population(next.population, it) } ?: Culture.HUMAN
                next = next.copy(population = ArmyEngine.adjustPopulation(next.population, culture, 20), resources = next.resources.copy(food = (next.resources.food - 40).coerceAtLeast(0)), city = next.city.copy(satisfaction = (next.city.satisfaction + 2).coerceAtMost(100)))
            } else next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction - 2).coerceAtLeast(0)))
            "deserters" -> if (first) next = next.copy(population = ArmyEngine.adjustRecruits(next.population, Culture.HUMAN, 8), city = next.city.copy(security = (next.city.security - 2).coerceAtLeast(0)))
                else next = next.copy(resources = next.resources.copy(food = next.resources.food + 40), frontier = next.frontier.copy(hordes = next.frontier.hordes.map { it.copy(daysToArrival = it.daysToArrival + 1) }))
            "craftsmen" -> if (first && next.resources.gold >= 100) next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 100, wood = next.resources.wood + 80, stone = next.resources.stone + 60))
            "scouts" -> if (first && next.resources.gold >= 60) next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 60), frontier = next.frontier.copy(hordes = next.frontier.hordes.map { it.copy(daysToArrival = it.daysToArrival + 1, estimateMinimum = (it.soldiers * 9 / 10), estimateMaximum = (it.soldiers * 11 / 10)) }))
            "wounded" -> if (first) next = next.copy(resources = next.resources.copy(food = (next.resources.food - 50).coerceAtLeast(0)), city = next.city.copy(satisfaction = (next.city.satisfaction + 3).coerceAtMost(100)))
                else next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction - 3).coerceAtLeast(0)))
            "smugglers" -> if (first && next.resources.gold >= 80) next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 80, iron = next.resources.iron + 70))
                else next = next.copy(resources = next.resources.copy(gold = next.resources.gold + 40), city = next.city.copy(satisfaction = (next.city.satisfaction - 2).coerceAtLeast(0)))
        }
        return result(log(next, "Entscheidung: ${event.title}", if (first) event.firstLabel else event.secondLabel), if (first) event.firstLabel else event.secondLabel)
    }

    fun patronize(state: GameState, culture: Culture): GameEngine.ActionResult {
        val level = state.culturePatronage[culture] ?: 0
        if (level >= 5) return result(state, "${culture.label} sind schon die bevorzugte Linie.")
        val cost = 500 + level * 250
        if (state.resources.gold < cost) return result(state, "Die Förderung kostet $cost Gold.")
        val next = state.copy(resources = state.resources.copy(gold = state.resources.gold - cost), culturePatronage = state.culturePatronage + (culture to level + 1))
        return result(log(next, "Volk gefördert", "${culture.label} auf Stufe ${level + 1}. Mehr Zuzug und mehr Rekruten, solange Wohnraum da ist."), "${culture.label} gefördert. Zuzug und Rekruten steigen.")
    }

    fun captainOffers(state: GameState): List<CaptainOffer> {
        val favored = state.culturePatronage.maxByOrNull { it.value }?.key
            ?: Culture.entries.maxByOrNull { ArmyEngine.population(state.population, it) }
            ?: Culture.HUMAN
        val secondary = Culture.entries.firstOrNull { it != favored && ArmyEngine.population(state.population, it) > 0 } ?: Culture.HUMAN
        return listOf(
            CaptainOffer(0, "Aren Falkenblick", favored, 58, 48, "Inspirierend", 600),
            CaptainOffer(1, "Lysa Sturmhand", secondary, 48, 61, "Taktikerin", 650),
            CaptainOffer(2, "Torren Schildwall", favored, 64, 43, "Standhaft", 700),
        )
    }

    fun hireCaptain(state: GameState): GameEngine.ActionResult = hireCaptain(state, 0)

    fun hireCaptain(state: GameState, offerIndex: Int): GameEngine.ActionResult {
        if (state.commanders.size >= 3) return result(state, "Drei Hauptleute reichen für diese Feste.")
        if (state.realm.level(BuildingType.BARRACKS) < 4) return result(state, "Ein weiterer Hauptmann braucht Kaserne Stufe 4.")
        val offer = captainOffers(state).firstOrNull { it.index == offerIndex } ?: return result(state, "Dieses Angebot ist nicht mehr verfügbar.")
        if (state.resources.gold < offer.cost) return result(state, "${offer.name} verlangt ${offer.cost} Gold.")
        val id = (state.commanders.maxOfOrNull { it.id } ?: 0) + 1
        val captain = Commander(
            id = id,
            name = offer.name,
            culture = offer.culture,
            portraitKey = "knight",
            leadership = offer.leadership,
            tactics = offer.tactics,
            loyalty = 70 + offer.index * 4,
            trait = offer.trait,
            rank = "Hauptmann",
        )
        val next = state.copy(resources = state.resources.copy(gold = state.resources.gold - offer.cost), commanders = state.commanders + captain)
        return result(
            log(next, "Hauptmann verpflichtet", "${captain.name} (${captain.culture.label}) tritt als ${captain.trait} in deinen Dienst."),
            "${captain.name} verpflichtet · Führung ${captain.leadership} · Taktik ${captain.tactics}.",
        )
    }
    fun claimDailyGoal(state: GameState): GameEngine.ActionResult {
        if (state.frontier.dailyGoalClaimed) return result(state, "Der Tageslohn ist schon genommen.")
        val done = when (state.frontier.dailyGoalKey) {
            "invasion" -> state.homeArmySize > 0
            "patrol" -> state.frontier.patrol != null
            "training" -> state.trainingSize > 0
            "food" -> state.resources.food >= 200
            "readiness" -> state.homeArmySize >= 40
            else -> false
        }
        if (!done) return result(state, state.frontier.dailyGoal.ifBlank { "Das Tagesziel ist noch nicht erfüllt." })
        val gold = 40 + state.realm.level(BuildingType.MARKET) * 5
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold + gold),
            frontier = state.frontier.copy(dailyGoalClaimed = true),
        )
        return result(log(next, "Tageslohn", "Das konkrete Tagesziel ist erfüllt. Der Abendbericht bringt $gold Gold."), "Tagesziel erfüllt: $gold Gold.")
    }
    fun assignCaptain(state: GameState, designId: Long, commanderId: Long?): GameEngine.ActionResult {
        val design = state.frontier.designs.firstOrNull { it.id == designId } ?: return result(state, "Regiment nicht gefunden.")
        if (commanderId != null && state.commanders.none { it.id == commanderId }) return result(state, "Hauptmann nicht gefunden.")
        if (commanderId != null && state.frontier.designs.any { it.id != designId && it.captainId == commanderId })
            return result(state, "Dieser Hauptmann führt bereits ein anderes eigenes Regiment.")
        val next = state.copy(frontier = state.frontier.copy(designs = state.frontier.designs.map {
            if (it.id == designId) it.copy(captainId = commanderId) else it
        }))
        val name = commanderId?.let { id -> state.commanders.firstOrNull { it.id == id }?.name }
        return result(next, if (name == null) "${design.name} steht wieder unter direktem Kommando." else "$name übernimmt ${design.name}.")
    }

    fun deleteDesign(state: GameState, id: Long): GameEngine.ActionResult {
        val design = state.frontier.designs.firstOrNull { it.id == id } ?: return result(state, "Entwurf nicht gefunden.")
        if (design.trainingAmount > 0) return result(state, "${design.name} wird noch ausgebildet.")
        return result(state.copy(frontier = state.frontier.copy(designs = state.frontier.designs.filterNot { it.id == id })),
            "${design.name} gelöscht. Bereits ausgebildete Soldaten bleiben im Pool ${design.unitType.label}. Gold kommt nicht zurück.")
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
            val veteran = 1.0 + d.veteranLevel * 0.025
            val captain = d.captainId?.let { id -> state.commanders.firstOrNull { it.id == id } }?.let {
                1.0 + (it.leadership + it.tactics).coerceAtMost(200) / 2000.0
            } ?: 1.0
            weighted += n * (d.powerEach.toDouble() / base * veteran * captain).coerceIn(.6, 1.55)
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
