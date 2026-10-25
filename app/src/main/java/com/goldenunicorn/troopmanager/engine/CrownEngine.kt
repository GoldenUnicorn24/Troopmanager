package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Kronlande: provinces, Heerbann, mercenaries, trade partners and edicts.
 * Province yields flow through [EconomyEngine] so the daily forecast stays truthful;
 * everything else runs once per day in [tick].
 */
object CrownEngine {
    private fun safe(value: Long): Int = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)

    fun chronicle(state: GameState, title: String, text: String): GameState =
        state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(CHRONICLE_LIMIT))

    // ------------------------------------------------------------ lifecycle

    fun initialize(state: GameState): GameState {
        if (state.crown.initialized) return state
        val partners = CrownCatalog.partners.map {
            TradePartnerState(it.id, unlocked = it.renownRequired == 0 && !it.needsLand)
        }
        val crown = state.crown.copy(
            initialized = true,
            partners = partners,
        )
        return RivalEngine.initialize(HeroineSagaEngine.initialize(state.copy(crown = crown)))
    }

    fun tick(state: GameState): GameState {
        var next = initialize(state)
        if (next.battleSession?.isActive == true) return next
        next = provincesDay(next)
        next = settlersDay(next)
        next = mercenaryDay(next)
        next = tradeDay(next)
        next = armouryDay(next)
        next = hordeRaids(next)
        next = edictSideEffects(next)
        next = CrownWarEngine.tick(next)
        next = HeroineSagaEngine.tick(next)
        next = ActEngine.tick(next)
        next = RivalEngine.tick(next)
        return next
    }

    // ------------------------------------------------------------ queries

    fun owned(state: GameState): List<CrownProvince> = state.crown.ownedProvinces

    fun ownsIn(state: GameState, land: CrownLand): Boolean = owned(state).any { it.land == land }

    fun reachable(state: GameState, land: CrownLand): Boolean =
        land == CrownLand.GRENZMARK || ownsIn(state, land) ||
            (CrownCatalog.neighbors[land] ?: emptySet()).any { ownsIn(state, it) }

    fun has(state: GameState, edict: CrownEdict): Boolean = edict in state.crown.edicts

    fun maxEdicts(state: GameState): Int = when (state.title) {
        "Kaiser der Kronlande" -> 5
        "Großkönig" -> 4
        "König", "Hochkönig" -> 3
        "Fürst", "Regionaler Herrscher" -> 2
        else -> 1
    }

    fun trainingFactor(state: GameState): Double = if (has(state, CrownEdict.WAR_ECONOMY)) 0.5 else 1.0

    /** Gold cost multiplier for training recruits. */
    fun recruitCostFactor(state: GameState): Double =
        (if (has(state, CrownEdict.WAR_ECONOMY)) 0.6 else 1.0) * (if (has(state, CrownEdict.CONSCRIPTION)) 0.85 else 1.0)

    private fun loyaltyFactor(p: CrownProvince): Double = 0.45 + p.loyalty / 100.0

    /** Daily province output for one resource; added to [EconomyEngine] production as an event bonus. */
    fun provinceYield(state: GameState, kind: ResourceKind): Int {
        val goldFactor = if (has(state, CrownEdict.WAR_ECONOMY)) 0.8 else 1.0
        return safe(owned(state).sumOf { p ->
            val pop = p.population.toDouble()
            val dev = 1.0 + p.development * 0.22
            val mining = p.terrain.contains("Erz") || p.terrain.contains("Bergbau") || p.terrain.contains("Schmiede")
            val value = when (kind) {
                ResourceKind.GOLD -> pop / 150.0 * (p.wealth / 5.0) * goldFactor
                ResourceKind.FOOD -> pop / 8.0 * (if (p.terrain.contains("Korn") || p.terrain.contains("Weide")) 1.6 else 1.0)
                ResourceKind.WOOD -> pop / 60.0 * (if (p.land == CrownLand.WALDHAIN) 1.8 else 1.0)
                ResourceKind.STONE -> pop / 80.0 * (if (p.land == CrownLand.KRANICHWALL) 1.6 else 1.0)
                ResourceKind.IRON -> pop / 110.0 * (if (mining) 3.0 else 1.0)
            }
            (value * dev * loyaltyFactor(p)).toLong()
        })
    }

    fun dailySettlers(state: GameState): Int {
        val multiplier = if (has(state, CrownEdict.CONSCRIPTION)) 2.5 else 1.0
        return safe(owned(state).sumOf { (it.population / 700.0 * multiplier * loyaltyFactor(it)).toLong() })
    }

    fun mercenaryUpkeep(state: GameState): Int = state.crown.mercenaries.sumOf { it.upkeep }

    fun levyCooldown(state: GameState): Int = (state.crown.lastLevyDay + 20 - state.day).coerceAtLeast(0)

    fun levyAmount(state: GameState): Int {
        val share = if (has(state, CrownEdict.CONSCRIPTION)) 0.10 else 0.06
        return safe(owned(state).sumOf { (it.population * share).toLong() })
    }

    // ------------------------------------------------------------ titles

    fun imperialTitle(state: GameState): String? {
        val provinces = state.crown.ownedProvinces.size
        val queen = state.relationship.romanceStage == RomanceStage.CO_RULERS
        return when {
            provinces >= 28 && state.armySize >= 100_000 && queen -> "Kaiser der Kronlande"
            provinces >= 15 && state.armySize >= 40_000 -> "Großkönig"
            else -> null
        }
    }

    // ------------------------------------------------------------ daily systems

    private fun provincesDay(state: GameState): GameState {
        var lostTerritory = 0
        val notes = mutableListOf<String>()
        val growth = if (has(state, CrownEdict.SETTLEMENT)) 0.005 else 0.0025
        val queenBonus = if (state.relationship.romanceStage >= RomanceStage.MARRIED) 5 else 0
        val provinces = state.crown.provinces.map { p ->
            if (p.owner == ProvinceOwner.PLAYER) {
                val target = 45 + p.development * 6 + p.fortification * 2 + queenBonus +
                    (if (has(state, CrownEdict.ROYAL_PATRONAGE)) 8 else 0) +
                    (if (has(state, CrownEdict.IRON_FIST)) 10 else 0) -
                    (if (has(state, CrownEdict.CONSCRIPTION)) 6 else 0)
                val loyalty = when {
                    p.loyalty < target -> p.loyalty + 1
                    p.loyalty > target -> p.loyalty - 1
                    else -> p.loyalty
                }.coerceIn(0, 100)
                var unrest = if (loyalty < 25) p.unrest + (25 - loyalty) / 5 + 1
                    else p.unrest - (if (has(state, CrownEdict.IRON_FIST)) 6 else 2)
                unrest = unrest.coerceIn(0, 100)
                val population = (p.population + (p.population * growth).toInt().coerceAtLeast(1)).coerceAtMost(150_000)
                if (unrest >= 100) {
                    lostTerritory++
                    notes += p.name
                    p.copy(owner = ProvinceOwner.REBELS, garrison = population / 30, baseGarrison = population / 30,
                        garrisonQuality = 10, loyalty = 20, unrest = 0, population = population, acquiredDay = null)
                } else p.copy(loyalty = loyalty, unrest = unrest, population = population)
            } else {
                val cap = if (p.owner == ProvinceOwner.FREE) p.baseGarrison else garrisonCap(state, p)
                val regen = (cap / 40).coerceAtLeast(5)
                p.copy(garrison = (p.garrison + regen).coerceAtMost(cap))
            }
        }
        var next = state.copy(crown = state.crown.withProvinces(provinces))
        if (lostTerritory > 0) {
            next = next.copy(realm = next.realm.copy(territory = (next.realm.territory - lostTerritory).coerceAtLeast(1)))
            next = chronicle(next, "Aufstand in den Kronlanden", "${notes.joinToString()} erhebt sich gegen deine Herrschaft. Erobere die Provinz zurück oder halte die Loyalität künftig höher.")
        }
        return next
    }

    private fun settlersDay(state: GameState): GameState {
        val owned = owned(state)
        if (owned.isEmpty()) return state.copy(crown = state.crown.copy(lastSettlers = 0))
        val multiplier = if (has(state, CrownEdict.CONSCRIPTION)) 2.5 else 1.0
        var population = state.population
        var housing = state.city.housingCapacity.toLong()
        var total = 0
        val provinces = state.crown.provinces.map { p ->
            if (p.owner != ProvinceOwner.PLAYER) return@map p
            val settlers = (p.population / 700.0 * multiplier * loyaltyFactor(p)).toInt()
            if (settlers <= 0) return@map p
            population = ArmyEngine.adjustPopulation(population, p.culture, settlers)
            population = ArmyEngine.adjustRecruits(population, p.culture, settlers)
            housing += settlers
            total += settlers
            p.copy(population = (p.population - settlers / 2).coerceAtLeast(500))
        }
        return state.copy(
            population = population,
            city = state.city.copy(housingCapacity = safe(housing)),
            crown = state.crown.withProvinces(provinces).copy(lastSettlers = total),
        )
    }

    private fun mercenaryDay(state: GameState): GameState {
        if (state.crown.mercenaries.isEmpty()) return state.copy(crown = state.crown.copy(lastMercenaryUpkeep = 0))
        // Contracts can never exceed the soldiers that actually survive in the pools.
        val remaining = state.armyPools.associate { it.type to it.soldiers }.toMutableMap()
        val clamped = state.crown.mercenaries.mapNotNull { c ->
            val left = remaining[c.type] ?: 0
            val soldiers = minOf(c.soldiers, left)
            remaining[c.type] = left - soldiers
            if (soldiers <= 0) null else c.copy(soldiers = soldiers, upkeep = (c.upkeep.toLong() * soldiers / c.soldiers.coerceAtLeast(1)).toInt().coerceAtLeast(1))
        }
        val upkeep = clamped.sumOf { it.upkeep }
        var next = state.copy(crown = state.crown.copy(mercenaries = clamped, lastMercenaryUpkeep = upkeep))
        if (next.resources.gold >= upkeep) {
            return next.copy(resources = next.resources.copy(gold = next.resources.gold - upkeep))
        }
        // Unpaid mercenaries desert in part.
        val deserters = clamped.associate { it.type to (it.soldiers / 20).coerceAtLeast(1) }
        next = next.copy(resources = next.resources.copy(gold = 0))
        next = removeHomeSoldiers(next, deserters)
        val contracts = next.crown.mercenaries.mapNotNull { c ->
            val lost = deserters[c.type] ?: 0
            if (c.soldiers - lost <= 0) null else c.copy(soldiers = c.soldiers - lost)
        }
        next = next.copy(crown = next.crown.copy(mercenaries = contracts))
        return chronicle(next, "Sold bleibt aus", "Ohne Bezahlung verlassen ${deserters.values.sum()} Söldner dein Heer.")
    }

    private fun tradeDay(state: GameState): GameState {
        var resources = state.resources
        var stock = state.militaryStock
        var income = 0
        var next = state
        val freeTrade = has(state, CrownEdict.FREE_TRADE)
        val partners = state.crown.partners.map { ps ->
            val def = CrownCatalog.partner(ps.id) ?: return@map ps
            var p = ps
            if (!p.unlocked && state.renown >= def.renownRequired && (!def.needsLand || reachable(state, def.land))) {
                p = p.copy(unlocked = true)
                next = chronicle(next, "Neuer Handelspartner", "${def.name} ist bereit, mit dir Handel zu treiben.")
            }
            if (!p.unlocked || p.routes <= 0) return@map p.copy(lastIncome = 0)
            val volume = (160.0 * p.routes * (1.0 + p.agreement * 0.3)).toInt()
            val relationFactor = 0.75 + p.relation / 200.0
            val tradeFactor = if (freeTrade) 1.35 else 1.0
            var gained = 0
            if (def.buys == ResourceKind.GOLD) {
                val pay = minOf(resources.gold, volume / 2)
                resources = resources.copy(gold = resources.gold - pay)
                val got = (pay * 2.2 * relationFactor * tradeFactor).toInt()
                resources = def.sells.withValue(resources, safe(def.sells.value(resources).toLong() + got))
                stock = MilitaryEconomyEngine.add(stock, MilitaryGood.HORSES, (volume / 40).coerceAtLeast(1))
                gained = got / 3
            } else {
                val available = def.buys.value(resources)
                val reserve = available / 5
                val export = minOf(volume, (available - reserve).coerceAtLeast(0))
                val price = when (def.buys) {
                    ResourceKind.FOOD -> 0.9
                    ResourceKind.WOOD -> 1.15
                    ResourceKind.STONE -> 1.35
                    ResourceKind.IRON -> 2.1
                    ResourceKind.GOLD -> 1.0
                }
                val gold = (export * price * relationFactor * tradeFactor).toInt()
                resources = def.buys.withValue(resources, available - export)
                resources = resources.copy(gold = safe(resources.gold.toLong() + gold))
                val imported = (volume * 0.4 * relationFactor).toInt()
                resources = def.sells.withValue(resources, safe(def.sells.value(resources).toLong() + imported))
                gained = gold
            }
            gained = (gained * ReignEngine.tradeFactor(next)).toInt()
            income += gained
            p.copy(
                relation = (p.relation + 1 + if (freeTrade) 1 else 0).coerceAtMost(100),
                totalProfit = p.totalProfit + gained,
                lastIncome = gained,
            )
        }
        // Trade can exceed normal stock limits only up to the realm's storage capacity.
        val capped = ResourceKind.entries.fold(resources) { acc, kind ->
            val cap = maxOf(kind.value(next.city.storageCapacity), kind.value(state.resources))
            kind.withValue(acc, kind.value(acc).coerceAtMost(cap))
        }
        return next.copy(
            resources = capped,
            militaryStock = stock,
            crown = next.crown.copy(partners = partners, lastTradeIncome = income),
        )
    }

    private fun armouryDay(state: GameState): GameState {
        val count = owned(state).size
        if (count == 0) return state
        val factor = if (has(state, CrownEdict.WAR_ECONOMY)) 4 else 1
        var stock = state.militaryStock
        listOf(MilitaryGood.SWORDS, MilitaryGood.SPEARS, MilitaryGood.ARMOR, MilitaryGood.SHIELDS, MilitaryGood.BOWS).forEach {
            stock = MilitaryEconomyEngine.add(stock, it, count * 6 * factor)
        }
        stock = MilitaryEconomyEngine.add(stock, MilitaryGood.ARROWS, count * 150 * factor)
        val horseLands = owned(state).count { it.land == CrownLand.ALDMARK || it.land == CrownLand.ASCHESTEPPE }
        stock = MilitaryEconomyEngine.add(stock, MilitaryGood.HORSES, (count + horseLands * 4) * factor)
        stock = MilitaryEconomyEngine.add(stock, MilitaryGood.MEDICINE, count * 2 * factor)
        return state.copy(militaryStock = stock)
    }

    private fun hordeRaids(state: GameState): GameState {
        if (state.day % 9 != 0) return state
        val hordeLands = state.crown.provinces.filter { it.owner == ProvinceOwner.HORDE }.map { it.land }.toSet()
        if (hordeLands.isEmpty()) return state
        val exposed = owned(state).filter { p ->
            p.land in hordeLands || (CrownCatalog.neighbors[p.land] ?: emptySet()).any { it in hordeLands }
        }
        val target = exposed.minByOrNull { it.fortification * 1000 + it.loyalty } ?: return state
        val raiders = state.crown.provinces.filter { it.owner == ProvinceOwner.HORDE }
            .minByOrNull { if (it.land == target.land) 0 else 1 }?.ownerLabel ?: "Orks"
        if (target.fortification >= 3) return chronicle(state, "Überfall abgewehrt", "$raiders rennen gegen die Befestigungen von ${target.name} an und ziehen sich zurück.")
        val loss = (target.population * (0.05 - target.fortification * 0.015)).toInt()
        val provinces = state.crown.provinces.map {
            if (it.id == target.id) it.copy(population = (it.population - loss).coerceAtLeast(500), loyalty = (it.loyalty - 6).coerceAtLeast(0)) else it
        }
        return chronicle(state.copy(crown = state.crown.withProvinces(provinces)), "$raiders plündern ${target.name}",
            "$raiders fallen in ${target.name} ein: $loss Untertanen fliehen oder sterben. Befestige die Provinz, um künftige Überfälle abzuwehren.")
    }

    private fun edictSideEffects(state: GameState): GameState {
        var satisfaction = state.city.satisfaction
        if (has(state, CrownEdict.CONSCRIPTION) && state.day % 3 == 0) satisfaction -= 1
        if (has(state, CrownEdict.IRON_FIST) && state.day % 2 == 0) satisfaction -= 1
        if (has(state, CrownEdict.ROYAL_PATRONAGE) && state.day % 4 == 0) satisfaction += 1
        return if (satisfaction == state.city.satisfaction) state
        else state.copy(city = state.city.copy(satisfaction = satisfaction.coerceIn(0, 100)))
    }

    // ------------------------------------------------------------ soldiers

    /** Removes soldiers that are at home (never those on missions) and the matching population. */
    fun removeHomeSoldiers(state: GameState, losses: Map<UnitType, Int>): GameState {
        var population = state.population
        val pools = state.armyPools.mapNotNull { p ->
            val count = (losses[p.type] ?: 0).coerceIn(0, state.homeSoldiers(p.type))
            population = ArmyEngine.adjustPopulation(population, p.type.culture, -count)
            if (p.soldiers - count <= 0) null else p.copy(soldiers = p.soldiers - count)
        }
        return ArmyEngine.clampAssignments(state.copy(armyPools = pools, population = population))
    }

    /** Adds ready soldiers together with the people they are, so civilian numbers stay correct. */
    fun addSoldiers(state: GameState, type: UnitType, amount: Int, experience: Int, morale: Int): GameState {
        if (amount <= 0) return state
        val withPeople = state.copy(
            population = ArmyEngine.adjustPopulation(state.population, type.culture, amount),
            city = state.city.copy(housingCapacity = safe(state.city.housingCapacity.toLong() + amount)),
        )
        return ArmyEngine.add(withPeople, type, amount, experience, morale)
    }

    // ------------------------------------------------------------ provinces

    fun province(state: GameState, id: String): CrownProvince? = state.crown.provinces.firstOrNull { it.id == id }

    private fun replace(state: GameState, province: CrownProvince): GameState =
        state.copy(crown = state.crown.withProvinces(state.crown.provinces.map { if (it.id == province.id) province else it }))

    fun landPrice(state: GameState, p: CrownProvince): Int {
        val base = 300L + p.population / 6 + p.wealth * 250L
        return safe((base * (if (has(state, CrownEdict.SETTLEMENT)) 0.7 else 1.0)).toLong())
    }

    fun annexPrice(p: CrownProvince): Int = safe(p.population / 3L + p.wealth * 600L)

    fun developCost(state: GameState, p: CrownProvince): Resources {
        val step = p.development + 1
        val factor = if (has(state, CrownEdict.ROYAL_PATRONAGE)) 0.75 else 1.0
        return Resources(
            gold = (1500 * step * factor).toInt(), food = 0,
            wood = (800 * step * factor).toInt(), stone = (800 * step * factor).toInt(), iron = 0,
        )
    }

    fun fortifyCost(p: CrownProvince): Resources {
        val step = p.fortification + 1
        return Resources(gold = 2000 * step, food = 0, wood = 600 * step, stone = 1500 * step, iron = 200 * step)
    }

    fun feastCost(state: GameState, p: CrownProvince): Int =
        safe(((p.population / 20L) * (if (has(state, CrownEdict.ROYAL_PATRONAGE)) 0.6 else 1.0)).toLong()).coerceAtLeast(150)

    private fun affordable(r: Resources, cost: Resources) = ResourceKind.entries.all { it.value(r) >= it.value(cost) }

    private fun pay(r: Resources, cost: Resources): Resources =
        ResourceKind.entries.fold(r) { acc, kind -> kind.withValue(acc, kind.value(acc) - kind.value(cost)) }

    private fun costText(cost: Resources): String = ResourceKind.entries
        .filter { it.value(cost) > 0 }
        .joinToString(" · ") { "${it.value(cost)} ${it.label}" }

    fun acquire(state: GameState, provinceId: String, how: String): GameState {
        val p = province(state, provinceId) ?: return state
        if (p.owner == ProvinceOwner.PLAYER) return state
        val loyalty = when (how) { "Kauf" -> 60; "Anschluss" -> 72; else -> if (has(state, CrownEdict.IRON_FIST)) 45 else 35 }
        val acquired = p.copy(
            owner = ProvinceOwner.PLAYER, garrison = 0, acquiredDay = state.day, loyalty = loyalty,
            unrest = if (how == "Eroberung") 20 else 0,
        )
        var next = replace(state, acquired)
        // First settlers arrive immediately: the province opens its people to your levy.
        val settlers = p.population / 20
        var population = ArmyEngine.adjustPopulation(next.population, p.culture, settlers)
        population = ArmyEngine.adjustRecruits(population, p.culture, settlers / 2)
        var storage = next.city.storageCapacity
        ResourceKind.entries.forEach { kind ->
            val bonus = when (kind) { ResourceKind.FOOD -> 15_000L; ResourceKind.GOLD -> 10_000L; else -> 5_000L }
            storage = kind.withValue(storage, safe(kind.value(storage) + bonus))
        }
        next = next.copy(
            population = population,
            city = next.city.copy(
                housingCapacity = safe(next.city.housingCapacity.toLong() + settlers + 1500),
                storageCapacity = storage,
            ),
            realm = next.realm.copy(territory = next.realm.territory + 1),
            renown = next.renown + 15 + p.wealth * 3,
        )
        next = chronicle(next, "${p.name} gehört der Krone", "$how in ${p.land.label}: ${p.name} (${p.population} Untertanen) schließt sich deinem Reich an. $settlers Siedler ziehen in deine Lande.")
        next = CelebrationEngine.provinceMilestone(next)
        next = RivalEngine.onProvinceAcquired(next, p)
        return ProgressionEngine.update(next)
    }

    fun buy(state: GameState, id: String): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.FREE) return ok(state, "Nur freies Land kann gekauft werden.")
        if (!reachable(state, p.land)) return ok(state, "${p.land.label} ist noch nicht erreichbar. Sichere zuerst ein Nachbarland.")
        val cost = landPrice(state, p)
        if (state.resources.gold < cost) return ok(state, "Landrecht kostet $cost Gold.")
        val paid = state.copy(resources = state.resources.copy(gold = state.resources.gold - cost))
        return ok(acquire(paid, id, "Kauf"), "${p.name} gekauft. Neue Untertanen ziehen zu.")
    }

    fun envoy(state: GameState, id: String): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.RIVAL && p.owner != ProvinceOwner.FREE) return ok(state, "Mit ${p.ownerLabel} gibt es keine Verhandlungen.")
        if (!reachable(state, p.land)) return ok(state, "${p.land.label} ist noch nicht erreichbar.")
        if (p.lastEnvoyDay == state.day) return ok(state, "Die Gesandten sind heute bereits unterwegs.")
        val cost = 250 + p.wealth * 50
        if (state.resources.gold < cost) return ok(state, "Gesandtschaft kostet $cost Gold.")
        val companionHelp = if (state.companion.met) state.companion.diplomacy / 15 else 0
        val gain = 6 + state.player.diplomacy / 8 + companionHelp + state.realm.level(BuildingType.EMBASSY) * 3 + state.renown / 400
        val next = replace(
            state.copy(resources = state.resources.copy(gold = state.resources.gold - cost)),
            p.copy(favor = (p.favor + gain).coerceAtMost(100), lastEnvoyDay = state.day),
        )
        return ok(next, "Gesandte in ${p.name}: Gunst +$gain (${(p.favor + gain).coerceAtMost(100)}/100).")
    }

    fun annex(state: GameState, id: String): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.RIVAL && p.owner != ProvinceOwner.FREE) return ok(state, "Diese Provinz lässt sich nicht diplomatisch gewinnen.")
        if (p.favor < 100) return ok(state, "${p.name} braucht 100 Gunst. Aktuell: ${p.favor}.")
        val cost = annexPrice(p)
        if (state.resources.gold < cost) return ok(state, "Der Anschluss kostet $cost Gold an Entschädigungen.")
        val paid = state.copy(resources = state.resources.copy(gold = state.resources.gold - cost))
        return ok(acquire(paid, id, "Anschluss"), "${p.name} schließt sich friedlich deiner Krone an.")
    }

    fun develop(state: GameState, id: String): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.PLAYER) return ok(state, "Nur eigene Provinzen können ausgebaut werden.")
        if (p.development >= 5) return ok(state, "${p.name} ist voll ausgebaut.")
        val cost = developCost(state, p)
        if (!affordable(state.resources, cost)) return ok(state, "Ausbau benötigt ${costText(cost)}.")
        val bonus = if (has(state, CrownEdict.ROYAL_PATRONAGE)) 8 else 5
        val next = replace(state.copy(resources = pay(state.resources, cost)),
            p.copy(development = p.development + 1, loyalty = (p.loyalty + bonus).coerceAtMost(100)))
        return ok(next, "${p.name} auf Ausbaustufe ${p.development + 1}: mehr Ertrag und Loyalität.")
    }

    fun fortify(state: GameState, id: String): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.PLAYER) return ok(state, "Nur eigene Provinzen können befestigt werden.")
        if (p.fortification >= 3) return ok(state, "${p.name} ist maximal befestigt.")
        val cost = fortifyCost(p)
        if (!affordable(state.resources, cost)) return ok(state, "Befestigung benötigt ${costText(cost)}.")
        val next = replace(state.copy(resources = pay(state.resources, cost)), p.copy(fortification = p.fortification + 1))
        return ok(next, "${p.name} erhält Befestigungsstufe ${p.fortification + 1}.")
    }

    fun feast(state: GameState, id: String): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner != ProvinceOwner.PLAYER) return ok(state, "Nur in eigenen Provinzen.")
        if (state.day - p.lastFeastDay < 5) return ok(state, "Das nächste Fest ist in ${5 - (state.day - p.lastFeastDay)} Tagen möglich.")
        val cost = feastCost(state, p)
        if (state.resources.gold < cost) return ok(state, "Ein Fest kostet $cost Gold.")
        val gain = if (has(state, CrownEdict.ROYAL_PATRONAGE)) 18 else 12
        val next = replace(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost)),
            p.copy(loyalty = (p.loyalty + gain).coerceAtMost(100), unrest = (p.unrest - 15).coerceAtLeast(0), lastFeastDay = state.day))
        return ok(next, "Volksfest in ${p.name}: Loyalität +$gain.")
    }

    fun levy(state: GameState): GameEngine.ActionResult {
        if (owned(state).isEmpty()) return ok(state, "Der Heerbann braucht mindestens eine Kronprovinz.")
        val cooldown = levyCooldown(state)
        if (cooldown > 0) return ok(state, "Der nächste Heerbann ist in $cooldown Tagen möglich.")
        val share = if (has(state, CrownEdict.CONSCRIPTION)) 0.10 else 0.06
        var population = state.population
        var total = 0
        val provinces = state.crown.provinces.map { p ->
            if (p.owner != ProvinceOwner.PLAYER) return@map p
            val levy = (p.population * share).toInt()
            population = ArmyEngine.adjustPopulation(population, p.culture, levy)
            population = ArmyEngine.adjustRecruits(population, p.culture, levy)
            total += levy
            p.copy(population = (p.population - levy).coerceAtLeast(500), loyalty = (p.loyalty - 6).coerceAtLeast(0), lastLevyDay = state.day)
        }
        var next = state.copy(
            population = population,
            city = state.city.copy(housingCapacity = safe(state.city.housingCapacity.toLong() + total)),
            crown = state.crown.withProvinces(provinces).copy(lastLevyDay = state.day),
        )
        next = chronicle(next, "Heerbann der Kronlande", "$total Männer und Frauen folgen deinem Ruf zu den Waffen. Bilde sie im Heer aus.")
        return ok(next, "Heerbann: $total neue Rekruten stehen bereit.")
    }

    // ------------------------------------------------------------ campaigns

    data class CampaignPreview(
        val ownTroops: Int,
        val ownPower: Long,
        val enemyTroops: Int,
        val enemyPower: Long,
        val ratio: Double,
        val expectedLosses: Int,
        val supplyGold: Int,
        val supplyFood: Int,
        val label: String,
    )

    fun campaignsAllowed(state: GameState): Int =
        1 + (if (state.crown.act.act >= 2) 1 else 0) + (if (state.crown.arts.level(CombatArt.STRATEGY) >= 5) 1 else 0) + (if (state.crown.godMode) 50 else 0)

    /** Enemy garrisons grow as the war escalates: each act makes unconquered lands harder. */
    fun garrisonCap(state: GameState, p: CrownProvince): Int =
        (p.baseGarrison * (1.0 + 0.7 * (state.crown.act.act - 1).coerceIn(0, 3) + state.day / 400.0)).toInt()

    fun campaignsLeft(state: GameState): Int {
        val used = if (state.crown.lastCampaignDay == state.day) state.crown.campaignsToday else 0
        return (campaignsAllowed(state) - used).coerceAtLeast(0)
    }

    // ------------------------------------------------------------ companion in battle

    data class CompanionAbility(val name: String, val description: String, val ratio: Double, val losses: Double)

    /** The companion's special ability, or null when she is not available (not met, wounded or away). */
    fun companionAbility(state: GameState): CompanionAbility? {
        val saga = state.crown.saga
        if (!state.companion.met || saga.companionId == null) return null
        if (saga.companionWoundedUntil > state.day || state.commanderAway(COMPANION_COMMANDER_ID)) return null
        return when (saga.companionId) {
            "alina" -> CompanionAbility("Pfeilhagel", "Ihre Bogenschützen dünnen jede Verteidigung aus.", 1.15, 0.95)
            "seraphine" -> CompanionAbility("Ritterlicher Sturm", "Sie führt den Sturmangriff an vorderster Front.", 1.20, 1.0)
            "liang" -> CompanionAbility("Kriegslist", "Sie findet die Schwachstelle in jeder Stellung.", 1.12, 0.80)
            "ithiliel" -> CompanionAbility("Klingentanz", "Wo sie kämpft, bricht die Moral des Feindes.", 1.14, 0.90)
            else -> CompanionAbility("Treue Klinge", "Sie kämpft an deiner Seite.", 1.10, 0.95)
        }
    }

    fun companionWoundedDays(state: GameState): Int = (state.crown.saga.companionWoundedUntil - state.day).coerceAtLeast(0)

    private fun maybeWoundCompanion(state: GameState, victory: Boolean, risky: Boolean, seed: Int): GameState {
        if (companionAbility(state) == null) return state
        val chance = (if (victory) 0.04 else 0.30) + (if (risky) 0.15 else 0.0)
        if (Random(seed).nextDouble() >= chance) return state
        val days = (8 - state.realm.level(BuildingType.HOSPITAL)).coerceIn(3, 8)
        val name = state.companion.name
        var next = state.copy(
            crown = state.crown.copy(saga = state.crown.saga.copy(companionWoundedUntil = state.day + days)),
            companion = state.companion.copy(affection = (state.companion.affection + 3).coerceAtMost(100)),
        )
        next = RelationshipEngine.remember(next, "wounded", "$name wird im Kampf verwundet. Du wachst an ihrem Lager.", 3, setOf("battle"))
        return chronicle(next, "$name verwundet", "$name wurde im Kampf verwundet und braucht $days Tage zur Genesung.")
    }

    private fun commandFactor(state: GameState): Double {
        val arts = state.crown.arts
        val companion = if (state.companion.met && state.crown.saga.companionId != null)
            (state.companion.leadership + state.companion.tactics) / 900.0 else 0.0
        val generals = state.crown.saga.heroines.count { it.stage == HeroineStage.GENERAL } * 0.04
        return (1.0 + state.player.leadership / 500.0 + state.player.tactics / 500.0 +
            (arts.level(CombatArt.WARCRY) + arts.level(CombatArt.STRATEGY)) * 0.02 + companion + generals) * ReignEngine.powerFactor(state)
    }

    private fun committed(state: GameState, sharePercent: Int): Map<UnitType, Int> =
        state.armyPools.associate { it.type to (state.homeSoldiers(it.type).toLong() * sharePercent / 100).toInt() }
            .filterValues { it > 0 }

    fun preview(state: GameState, id: String, sharePercent: Int): CampaignPreview? {
        val p = province(state, id) ?: return null
        val troops = committed(state, sharePercent.coerceIn(1, 100))
        val own = troops.entries.sumOf { (type, n) ->
            val pool = state.armyPools.first { it.type == type }
            (n.toDouble() * pool.power / pool.soldiers.coerceAtLeast(1)).toLong()
        }
        val ability = companionAbility(state)
        val ownPower = (own * commandFactor(state) * (ability?.ratio ?: 1.0)).toLong()
        val difficulty = when (state.settings.difficulty) { Difficulty.STORY -> 0.8; Difficulty.VETERAN -> 1.2; else -> 1.0 }
        val champion = when (id) {
            state.crown.championBonusProvinceId -> 0.8
            state.crown.championPenaltyProvinceId -> 1.1
            else -> 1.0
        }
        val enemyPower = (p.garrison.toDouble() * p.garrisonQuality * (1.0 + p.fortification * 0.18) * difficulty * champion).toLong()
        var ratio = if (enemyPower <= 0) 99.0 else ownPower.toDouble() / enemyPower
        if (state.crown.godMode) ratio = maxOf(ratio, 8.0)
        val total = troops.values.sum()
        val lossFraction = (if (ratio >= 1.0) (0.30 / ratio.pow(1.15)).coerceIn(0.01, 0.45)
            else (0.25 + 0.2 * (1.0 - ratio)).coerceIn(0.25, 0.6)) * (ability?.losses ?: 1.0)
        val label = when {
            total == 0 -> "Keine Truppen daheim"
            ratio >= 2.5 -> "Überwältigend"
            ratio >= 1.4 -> "Klarer Vorteil"
            ratio >= 1.05 -> "Knapp überlegen"
            ratio >= 0.9 -> "Offen – Würfel des Krieges"
            else -> "Unterlegen"
        }
        return CampaignPreview(total, ownPower, p.garrison, enemyPower, ratio, (total * lossFraction).roundToInt(),
            (total / 8).coerceAtLeast(50), ((total / 2) * ReignEngine.provisionFactor(state)).toInt().coerceAtLeast(100), label)
    }

    fun conquer(state: GameState, id: String, sharePercent: Int): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return ok(state, "Beende zuerst die laufende Schlacht.")
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner == ProvinceOwner.PLAYER) return ok(state, "${p.name} gehört bereits dir.")
        if (!reachable(state, p.land)) return ok(state, "${p.land.label} ist noch nicht erreichbar.")
        if (campaignsLeft(state) <= 0) return ok(state, "Dein Heer braucht Ruhe. Morgen sind neue Feldzüge möglich.")
        if (state.crown.pendingMarch != null) return ok(state, "Ein Feldzug wartet noch auf deine Entscheidung unterwegs.")
        val preview = preview(state, id, sharePercent) ?: return ok(state, "Feldzug nicht möglich.")
        if (preview.ownTroops <= 0) return ok(state, "Keine Soldaten daheim, die ausrücken könnten.")
        if (state.resources.gold < preview.supplyGold || state.resources.food < preview.supplyFood)
            return ok(state, "Nachschub fehlt: ${preview.supplyGold} Gold und ${preview.supplyFood} Nahrung.")
        MarchEngine.maybeStart(state, id, sharePercent.coerceIn(1, 100))?.let { return it }
        return resolveConquest(state, id, sharePercent, PendingMarch(id, sharePercent))
    }

    /** Fights the campaign; [march] carries modifiers from an event on the way. */
    fun resolveConquest(state: GameState, id: String, sharePercent: Int, march: PendingMarch): GameEngine.ActionResult {
        val p = province(state, id) ?: return ok(state, "Provinz nicht gefunden.")
        if (p.owner == ProvinceOwner.PLAYER) return ok(state, "${p.name} gehört bereits dir.")
        val base = preview(state, id, sharePercent) ?: return ok(state, "Feldzug nicht möglich.")
        if (base.ownTroops <= 0) return ok(state, "Keine Soldaten daheim, die ausrücken könnten.")
        val preview = base.copy(ratio = base.ratio * march.ratioFactor)
        val supplyGold = minOf(preview.supplyGold, state.resources.gold)
        val supplyFood = minOf(preview.supplyFood, state.resources.food)
        val troops = committed(state, sharePercent.coerceIn(1, 100))
        val rng = Random(state.day * 7919 + p.id.hashCode() + state.crown.campaignVictories * 31)
        val roll = rng.nextDouble(0.92, 1.08)
        val victory = p.garrison <= 0 || preview.ratio * roll >= 1.0
        val ability = companionAbility(state)
        val lossFraction = (if (victory) (0.30 / (preview.ratio * roll).pow(1.15)).coerceIn(0.01, 0.45)
            else (0.25 + 0.2 * (1.0 - preview.ratio)).coerceIn(0.25, 0.6)) * march.lossFactor * (ability?.losses ?: 1.0)
        val losses = troops.mapValues { (_, n) -> (n * lossFraction).roundToInt() }
        val ownLosses = losses.values.sum()
        val enemyLosses = if (victory) p.garrison else (p.garrison * (0.5 * preview.ratio).coerceIn(0.05, 0.6)).toInt()

        var next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - supplyGold, food = state.resources.food - supplyFood),
            crown = state.crown.copy(
                pendingMarch = null,
                lastCampaignDay = state.day,
                campaignsToday = (if (state.crown.lastCampaignDay == state.day) state.crown.campaignsToday else 0) + 1,
                championBonusProvinceId = state.crown.championBonusProvinceId.takeUnless { it == id },
                championPenaltyProvinceId = state.crown.championPenaltyProvinceId.takeUnless { it == id },
            ),
        )
        next = removeHomeSoldiers(next, losses)
        next = next.copy(armyPools = next.armyPools.map { pool ->
            if (pool.type !in troops) pool else pool.copy(
                experience = (pool.experience + if (victory) 6 else 3).coerceAtMost(100),
                morale = (pool.morale + if (victory) 4 else -8).coerceIn(10, 100),
            )
        })
        var loot = 0
        var renown: Int
        val text: String
        if (victory) {
            loot = safe(p.wealth.toLong() * p.population / 12 + p.garrison * 2L)
            renown = 10 + p.garrison / 40
            next = next.copy(
                resources = next.resources.copy(gold = safe(next.resources.gold.toLong() + loot)),
                victories = next.victories + 1,
                renown = next.renown + renown,
                crown = next.crown.copy(campaignVictories = next.crown.campaignVictories + 1),
            )
            next = acquire(next, id, "Eroberung")
            if (next.companion.met) next = next.copy(companion = next.companion.copy(
                trust = (next.companion.trust + 2).coerceAtMost(100), respect = (next.companion.respect + 3).coerceAtMost(100)))
            val helper = ability?.let { " ${next.companion.name}: ${it.name}." } ?: ""
            text = "Sieg in ${p.name}! ${preview.ownTroops} Soldaten stürmen gegen ${p.garrison} Verteidiger. Verluste: $ownLosses. Beute: $loot Gold.$helper"
        } else {
            renown = 2
            next = replace(next, p.copy(garrison = (p.garrison - enemyLosses).coerceAtLeast(0)))
            next = next.copy(defeats = next.defeats + 1, renown = next.renown + renown)
            text = "Rückzug aus ${p.name}. Verluste: $ownLosses. Die Verteidiger verlieren $enemyLosses Mann – ein neuer Angriff hat bessere Chancen."
        }
        val report = CampaignReport(state.day, p.id, p.name, victory, preview.ownTroops, ownLosses, p.garrison, enemyLosses, loot, renown, text)
        next = next.copy(crown = next.crown.copy(reports = (listOf(report) + next.crown.reports).take(25)))
        next = chronicle(next, if (victory) "Feldzug gewonnen" else "Feldzug abgebrochen", text)
        next = maybeWoundCompanion(next, victory, march.companionRisk, state.day * 31 + p.id.hashCode())
        val wounded = next.crown.saga.companionWoundedUntil > state.day && state.crown.saga.companionWoundedUntil <= state.day
        return ok(ProgressionEngine.update(next), if (wounded) "$text ${next.companion.name} wurde verwundet!" else text)
    }

    // ------------------------------------------------------------ edicts

    fun toggleEdict(state: GameState, edict: CrownEdict): GameEngine.ActionResult {
        if (has(state, edict)) {
            return ok(state.copy(crown = state.crown.copy(edicts = state.crown.edicts - edict)), "${edict.label} aufgehoben.")
        }
        if (state.crown.edicts.size >= maxEdicts(state)) return ok(state, "Dein Titel erlaubt ${maxEdicts(state)} Edikte gleichzeitig. Höhere Titel erlauben mehr.")
        val cost = 2500
        if (state.resources.gold < cost) return ok(state, "Ein Edikt zu verkünden kostet $cost Gold.")
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - cost),
            crown = state.crown.copy(edicts = state.crown.edicts + edict),
        )
        return ok(chronicle(next, "Edikt verkündet", "${edict.label}: ${edict.description}"), "${edict.label} gilt ab sofort.")
    }

    // ------------------------------------------------------------ mercenaries

    fun companyBlocker(state: GameState, def: CrownCatalog.MercenaryCompanyDef): String? = when {
        state.renown < def.renownRequired -> "Benötigt ${def.renownRequired} Ruhm"
        def.partnerId != null && (state.crown.partner(def.partnerId)?.agreement ?: 0) < 1 ->
            "Benötigt Handelsabkommen mit ${CrownCatalog.partner(def.partnerId)?.name ?: def.partnerId}"
        else -> null
    }

    fun hire(state: GameState, companyId: String): GameEngine.ActionResult {
        val def = CrownCatalog.company(companyId) ?: return ok(state, "Kompanie nicht gefunden.")
        companyBlocker(state, def)?.let { return ok(state, it + ".") }
        if (state.resources.gold < def.cost) return ok(state, "${def.name} verlangt ${def.cost} Gold Handgeld.")
        var next = state.copy(resources = state.resources.copy(gold = state.resources.gold - def.cost))
        next = addSoldiers(next, def.type, def.soldiers, 45, 78)
        val contract = MercenaryContract("merc_${next.crown.nextId}", def.id, def.type, def.soldiers, def.upkeep, state.day)
        next = next.copy(crown = next.crown.copy(mercenaries = next.crown.mercenaries + contract, nextId = next.crown.nextId + 1))
        next = chronicle(next, "Söldner angeworben", "${def.name} (${def.soldiers} ${def.type.label}) tritt in deinen Dienst. Sold: ${def.upkeep} Gold pro Tag.")
        return ok(next, "${def.soldiers} ${def.type.label} der ${def.name} sind sofort einsatzbereit.")
    }

    fun dismiss(state: GameState, contractId: String): GameEngine.ActionResult {
        val c = state.crown.mercenaries.firstOrNull { it.id == contractId } ?: return ok(state, "Vertrag nicht gefunden.")
        var next = removeHomeSoldiers(state, mapOf(c.type to c.soldiers))
        next = next.copy(crown = next.crown.copy(mercenaries = next.crown.mercenaries.filterNot { it.id == contractId }))
        return ok(next, "Der Vertrag ist gelöst. Die Söldner ziehen ab.")
    }

    // ------------------------------------------------------------ trade

    fun maxRoutes(state: GameState, partner: TradePartnerState): Int =
        1 + partner.agreement * 2 + state.realm.level(BuildingType.EMBASSY) + (if (has(state, CrownEdict.FREE_TRADE)) 1 else 0)

    fun routeCost(partner: TradePartnerState): Int = 1200 * (partner.routes + 1)

    fun goodsPrice(def: CrownCatalog.TradePartnerDef, partner: TradePartnerState): Int =
        (def.packagePrice * (1.0 - partner.relation / 400.0) * (if (partner.agreement >= 2) 0.85 else 1.0)).toInt()

    private fun updatePartner(state: GameState, partner: TradePartnerState): GameState =
        state.copy(crown = state.crown.copy(partners = state.crown.partners.map { if (it.id == partner.id) partner else it }))

    fun openRoute(state: GameState, partnerId: String): GameEngine.ActionResult {
        val p = state.crown.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        val def = CrownCatalog.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        if (!p.unlocked) return ok(state, "${def.name} handelt noch nicht mit dir.")
        if (p.routes >= maxRoutes(state, p)) return ok(state, "Mehr Routen erst mit Abkommen, Bündnis oder Botschaft.")
        val cost = routeCost(p)
        if (state.resources.gold < cost) return ok(state, "Eine neue Karawanenroute kostet $cost Gold.")
        val next = updatePartner(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost)),
            p.copy(routes = p.routes + 1, relation = (p.relation + 3).coerceAtMost(100)))
        return ok(next, "Route ${p.routes + 1} zu ${def.name} eröffnet.")
    }

    fun gift(state: GameState, partnerId: String): GameEngine.ActionResult {
        val p = state.crown.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        if (!p.unlocked) return ok(state, "Noch kein Kontakt.")
        if (state.day - p.lastGiftDay < 3) return ok(state, "Ein weiteres Geschenk würde aufdringlich wirken.")
        val cost = 600
        if (state.resources.gold < cost) return ok(state, "Ein angemessenes Geschenk kostet $cost Gold.")
        val gain = 8 + (if (state.companion.met) state.companion.diplomacy / 20 else 0) + state.player.diplomacy / 25
        val next = updatePartner(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost)),
            p.copy(relation = (p.relation + gain).coerceAtMost(100), lastGiftDay = state.day))
        return ok(next, "Beziehung +$gain.")
    }

    fun agreementCost(p: TradePartnerState): Int = if (p.agreement == 0) 3000 else 9000

    fun signAgreement(state: GameState, partnerId: String): GameEngine.ActionResult {
        val p = state.crown.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        val def = CrownCatalog.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        if (p.agreement >= 2) return ok(state, "Ihr seid bereits verbündet.")
        val needed = if (p.agreement == 0) 45 else 80
        if (p.relation < needed) return ok(state, "${def.name} verlangt Beziehung $needed.")
        val cost = agreementCost(p)
        if (state.resources.gold < cost) return ok(state, "Vertrag kostet $cost Gold.")
        val label = if (p.agreement == 0) "Handelsabkommen" else "Handelsbündnis"
        var next = updatePartner(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost)), p.copy(agreement = p.agreement + 1))
        next = chronicle(next, label, "$label mit ${def.name} unterzeichnet.")
        return ok(next, "$label mit ${def.name}: mehr Routen, bessere Preise, neue Söldner.")
    }

    fun buyGoods(state: GameState, partnerId: String): GameEngine.ActionResult {
        val p = state.crown.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        val def = CrownCatalog.partner(partnerId) ?: return ok(state, "Partner unbekannt.")
        if (!p.unlocked) return ok(state, "Noch kein Kontakt.")
        val price = goodsPrice(def, p)
        if (state.resources.gold < price) return ok(state, "Die Lieferung kostet $price Gold.")
        var stock = state.militaryStock
        def.goods.forEach { (good, amount) -> stock = MilitaryEconomyEngine.add(stock, good, amount) }
        val next = updatePartner(state.copy(resources = state.resources.copy(gold = state.resources.gold - price), militaryStock = stock),
            p.copy(relation = (p.relation + 1).coerceAtMost(100)))
        return ok(next, "Lieferung erhalten: " + def.goods.joinToString(" · ") { "${it.second} ${it.first.label}" })
    }

    private fun fmtN(n: Int): String = String.format(java.util.Locale.GERMANY, "%,d", n)

    // ------------------------------------------------------------ mass training

    /** Trains every available recruit of each culture into the chosen unit type. */
    fun trainAll(state: GameState, choices: Map<Culture, UnitType>): GameEngine.ActionResult {
        var next = state
        val done = mutableListOf<String>()
        val failed = mutableListOf<String>()
        choices.forEach { (culture, type) ->
            if (type.culture != culture) return@forEach
            if (ArmyEngine.recruitable(next, culture) <= 0) return@forEach
            // Train as many as gold and military goods allow; explain the bottleneck otherwise.
            val all = ArmyEngine.recruitable(next, culture)
            val n = GameEngine.maxRecruit(next, type)
            val result = if (n > 0) GameEngine.recruitAmount(next, type, n) else GameEngine.recruitAmount(next, type, all)
            if (result.state != next) {
                next = result.state
                done += if (n >= all) "${fmtN(n)} ${type.label}" else "${fmtN(n)} ${type.label} (von ${fmtN(all)})"
            } else failed += "${type.label}: ${result.message}"
        }
        val text = when {
            done.isEmpty() && failed.isEmpty() -> "Keine Rekruten verfügbar. Rufe den Heerbann aus oder erobere Provinzen."
            done.isEmpty() -> failed.first()
            failed.isEmpty() -> "Ausbildung begonnen: ${done.joinToString()}."
            else -> "Ausbildung begonnen: ${done.joinToString()}. ${failed.first()}"
        }
        return ok(next, text)
    }
}
