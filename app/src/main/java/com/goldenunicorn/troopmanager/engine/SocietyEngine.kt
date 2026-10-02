package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Bounded social pressures influence the existing city, treasury, civilian population and morale. */
object SocietyEngine {
    private fun active(state: GameState, policy: SocietyPolicy) = (state.society.policyUntilDay[policy] ?: 0) > state.day
    private fun approach(value: Int, target: Int, step: Int = 1) =
        (value + (target - value).coerceIn(-step, step)).coerceIn(0, 100)
    private fun safe(value: Long) = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun policyCost(policy: SocietyPolicy): Resources = when (policy) {
        SocietyPolicy.PATROLS -> Resources(180, 0, 0, 0, 0)
        SocietyPolicy.FAIR_WAGES -> Resources(240, 0, 0, 0, 0)
        SocietyPolicy.CLINICS -> Resources(200, 0, 0, 0, 0)
        SocietyPolicy.FOOD_RELIEF -> Resources(0, 300, 0, 0, 0)
        SocietyPolicy.INTEGRATION -> Resources(180, 100, 0, 0, 0)
    }

    fun fundPolicy(state: GameState, policy: SocietyPolicy): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Politik nach der Schlacht möglich.")
        if (active(state, policy)) return GameEngine.ActionResult(state, "Diese Maßnahme läuft bereits.")
        val cost = policyCost(policy)
        if (state.resources.gold < cost.gold || state.resources.food < cost.food)
            return GameEngine.ActionResult(state, "Benötigt ${cost.gold} Gold und ${cost.food} Nahrung.")
        val society = state.society
        return GameEngine.ActionResult(state.copy(resources = state.resources.copy(gold = state.resources.gold - cost.gold,
            food = state.resources.food - cost.food), society = society.copy(policyUntilDay =
            society.policyUntilDay + (policy to state.day + 14),
            crime = (society.crime - if (policy == SocietyPolicy.PATROLS) 8 else 0).coerceAtLeast(0),
            inequality = (society.inequality - if (policy == SocietyPolicy.FAIR_WAGES) 8 else 0).coerceAtLeast(0),
            disease = (society.disease - if (policy == SocietyPolicy.CLINICS) 10 else 0).coerceAtLeast(0),
            hunger = (society.hunger - if (policy == SocietyPolicy.FOOD_RELIEF) 12 else 0).coerceAtLeast(0),
            culturalTension = (society.culturalTension - if (policy == SocietyPolicy.INTEGRATION) 10 else 0).coerceAtLeast(0))),
            "${policy.label} wirkt 14 Tage.")
    }

    fun resolveDemand(state: GameState, demandId: String, accept: Boolean): GameEngine.ActionResult {
        val demand = state.society.demands.firstOrNull { it.id == demandId }
            ?: return GameEngine.ActionResult(state, "Forderung nicht gefunden.")
        if (state.battleSession?.isActive == true || demand.deadlineDay <= state.day)
            return GameEngine.ActionResult(state, "Forderung kann derzeit nicht bearbeitet werden.")
        if (accept && ((demand.kind == PoliticalDemandKind.FOOD_RELIEF && state.resources.food < demand.kind.cost) ||
            (demand.kind != PoliticalDemandKind.FOOD_RELIEF && state.resources.gold < demand.kind.cost)))
            return GameEngine.ActionResult(state, "Die verlangten Ressourcen fehlen.")
        var next = state.copy(society = state.society.copy(demands = state.society.demands.filterNot { it.id == demandId },
            groups = state.society.groups.map { if (it.kind == demand.group) it.copy(loyalty =
                (it.loyalty + if (accept) 15 else -12).coerceIn(0, 100), lastDemandDay = state.day) else it }))
        if (accept) {
            next = next.copy(resources = next.resources.copy(
                gold = next.resources.gold - if (demand.kind != PoliticalDemandKind.FOOD_RELIEF) demand.kind.cost else 0,
                food = next.resources.food - if (demand.kind == PoliticalDemandKind.FOOD_RELIEF) demand.kind.cost else 0))
            next = when (demand.kind) {
                PoliticalDemandKind.SOLDIER_PAY -> next.copy(armyPools = next.armyPools.map { it.copy(morale = (it.morale + 8).coerceAtMost(100)) },
                    society = next.society.copy(warExhaustion = (next.society.warExhaustion - 12).coerceAtLeast(0)))
                PoliticalDemandKind.PROTECT_TRADE -> next.copy(city = next.city.copy(security = (next.city.security + 8).coerceAtMost(100)),
                    society = next.society.copy(crime = (next.society.crime - 10).coerceAtLeast(0)))
                PoliticalDemandKind.LOWER_TAXES -> next.copy(city = next.city.copy(taxLevel = TaxLevel.LOW),
                    society = next.society.copy(inequality = (next.society.inequality - 8).coerceAtLeast(0)))
                PoliticalDemandKind.FOOD_RELIEF -> next.copy(society = next.society.copy(hunger = (next.society.hunger - 20).coerceAtLeast(0)),
                    city = next.city.copy(satisfaction = (next.city.satisfaction + 6).coerceAtMost(100)))
                PoliticalDemandKind.CULTURAL_COUNCIL -> next.copy(society = next.society.copy(culturalTension =
                    (next.society.culturalTension - 20).coerceAtLeast(0)))
            }
        }
        return GameEngine.ActionResult(next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day,
            "${demand.group.label}: ${demand.kind.label}", if (accept) "Forderung erfüllt; Vertrauen und Gesellschaft reagieren." else
                "Forderung abgelehnt; die Gruppe verliert politische Loyalität.")).takeLast(2000)), if (accept) "Forderung erfüllt." else "Forderung abgelehnt.")
    }

    fun tick(state: GameState): GameState {
        if (state.society.lastTickDay >= state.day) return state
        val previous = state.society
        val war = state.diplomacy.relations.any { it.atWar && (it.firstFactionId == PLAYER_FACTION || it.secondFactionId == PLAYER_FACTION) }
        val crowding = (state.population.total.toLong() * 100 / state.city.housingCapacity.coerceAtLeast(1)).coerceAtMost(200).toInt()
        val foodNeed = (state.population.total.toLong() / 4 + state.armySize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val hungry = state.resources.food < foodNeed
        val hunger = (previous.hunger + if (hungry) 5 else -3 - if (active(state, SocietyPolicy.FOOD_RELIEF)) 2 else 0).coerceIn(0, 100)
        val inequalityTarget = when (state.city.taxLevel) { TaxLevel.LOW -> 12; TaxLevel.NORMAL -> 28; TaxLevel.HIGH -> 65 }
        val inequality = approach(previous.inequality, inequalityTarget - if (active(state, SocietyPolicy.FAIR_WAGES)) 20 else 0)
        val diseaseTarget = ((crowding - 80).coerceAtLeast(0) / 2 + hunger / 3 -
            state.realm.level(BuildingType.HOSPITAL) * 10 - if (active(state, SocietyPolicy.CLINICS)) 25 else 0).coerceIn(0, 100)
        val disease = approach(previous.disease, diseaseTarget, 2)
        val cultureCount = Culture.entries.count { ArmyEngine.population(state.population, it) > 0 }
        val tensionTarget = ((cultureCount - 1) * 5 + hunger / 3 + inequality / 5 +
            (crowding - 100).coerceAtLeast(0) / 2 - if (active(state, SocietyPolicy.INTEGRATION)) 30 else 0).coerceIn(0, 100)
        val tension = approach(previous.culturalTension, tensionTarget)
        val crimeTarget = (65 - state.city.security + inequality / 3 + hunger / 3 -
            if (active(state, SocietyPolicy.PATROLS)) 30 else 0).coerceIn(0, 100)
        val crime = approach(previous.crime, crimeTarget, 2)
        val fatigue = (previous.warExhaustion + if (war) 1 + if (hunger > 30) 1 else 0 else -3).coerceIn(0, 100)
        val expired = previous.demands.filter { it.deadlineDay <= state.day }
        var groups = previous.groups.map { group ->
            val target = when (group.kind) {
                PoliticalGroupKind.MILITARY -> 80 - fatigue / 2 - hunger / 3
                PoliticalGroupKind.MERCHANTS -> 50 + state.city.prosperity / 3 - crime / 2
                PoliticalGroupKind.NOBILITY -> 60 - if (state.city.taxLevel == TaxLevel.HIGH) 20 else 0
                PoliticalGroupKind.FARMERS -> 80 - hunger / 2 - inequality / 3
                PoliticalGroupKind.CULTURAL_REPRESENTATIVES -> 80 - tension
            }
            group.copy(loyalty = (approach(group.loyalty, target) - if (expired.any { it.group == group.kind }) 12 else 0).coerceIn(0, 100),
                lastDemandDay = if (expired.any { it.group == group.kind }) state.day else group.lastDemandDay)
        }
        var demands = previous.demands.filter { it.deadlineDay > state.day }
        if (state.day % 7 == 0 && demands.size < 3) {
            val group = groups.filter { it.loyalty < 55 && state.day - it.lastDemandDay >= 21 &&
                demands.none { d -> d.group == it.kind } }.minByOrNull { it.loyalty }
            if (group != null) {
                val kind = when (group.kind) {
                    PoliticalGroupKind.MILITARY -> PoliticalDemandKind.SOLDIER_PAY
                    PoliticalGroupKind.MERCHANTS -> PoliticalDemandKind.PROTECT_TRADE
                    PoliticalGroupKind.NOBILITY -> PoliticalDemandKind.LOWER_TAXES
                    PoliticalGroupKind.FARMERS -> if (hunger > 20) PoliticalDemandKind.FOOD_RELIEF else PoliticalDemandKind.LOWER_TAXES
                    PoliticalGroupKind.CULTURAL_REPRESENTATIVES -> PoliticalDemandKind.CULTURAL_COUNCIL
                }
                demands = demands + PoliticalDemand("demand_${group.kind.name}_${state.day}", group.kind, kind, state.day, state.day + 14)
                groups = groups.map { if (it.kind == group.kind) it.copy(lastDemandDay = state.day) else it }
            }
        }
        val political = if (groups.isEmpty()) 60 else (groups.sumOf { it.loyalty.toLong() * it.influence } /
            groups.sumOf { it.influence.toLong() }.coerceAtLeast(1)).toInt().coerceIn(0, 100)
        val migration = when {
            hunger > 55 || disease > 65 || political < 25 -> -minOf(8, state.freePopulation)
            state.day % 7 == 0 && state.city.satisfaction >= 55 && hunger < 20 && disease < 25 && crowding < 90 ->
                minOf(12, (state.city.housingCapacity - state.population.total).coerceAtLeast(0))
            else -> 0
        }
        var population = state.population
        if (migration != 0) {
            if (migration > 0) {
                val culture = Culture.entries.maxBy { ArmyEngine.population(state.population, it) }
                population = ArmyEngine.adjustPopulation(population, culture, migration)
            } else {
                var remaining = -migration
                Culture.entries.sortedByDescending { ArmyEngine.population(state.population, it) }.forEach { culture ->
                    val military = state.armyPools.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } +
                        state.trainingQueue.filter { it.type.culture == culture }.sumOf { it.amount.toLong() } +
                        state.war.wounded.filter { it.type.culture == culture }.sumOf { it.soldiers.toLong() } +
                        state.war.captives.filter { it.own && it.type?.culture == culture }.sumOf { it.soldiers.toLong() } +
                        population.recruits(culture)
                    val civilians = (ArmyEngine.population(population, culture).toLong() - military).coerceAtLeast(0)
                    val leaving = minOf(remaining.toLong(), civilians).toInt()
                    population = ArmyEngine.adjustPopulation(population, culture, -leaving)
                    remaining -= leaving
                }
            }
        }
        val theft = if (state.day % 7 == 0 && crime > 35) minOf(state.resources.gold, crime * 2) else 0
        val diseaseFood = if (disease > 30) minOf(state.resources.food, disease / 5) else 0
        val pressure = hunger / 30 + fatigue / 45 + tension / 50 + inequality / 60 + if (political < 25) 1 else 0
        val society = previous.copy(crime = crime, inequality = inequality, disease = disease, hunger = hunger,
            culturalTension = tension, politicalLoyalty = political, warExhaustion = fatigue,
            lastMigration = migration, totalImmigrants = safe(previous.totalImmigrants.toLong() + migration.coerceAtLeast(0)),
            totalEmigrants = safe(previous.totalEmigrants.toLong() - migration.coerceAtMost(0)), groups = groups, demands = demands,
            policyUntilDay = previous.policyUntilDay.filterValues { it > state.day }, lastTickDay = state.day)
        var next = state.copy(population = population, society = society,
            resources = state.resources.copy(gold = state.resources.gold - theft, food = state.resources.food - diseaseFood),
            city = state.city.copy(satisfaction = (state.city.satisfaction - pressure).coerceIn(0, 100),
                prosperity = (state.city.prosperity - crime / 50 - disease / 50).coerceIn(0, 100),
                security = (state.city.security - crime / 55 + if (active(state, SocietyPolicy.PATROLS)) 1 else 0).coerceIn(0, 100)),
            armyPools = if (state.day % 7 == 0 && fatigue > 30) state.armyPools.map {
                it.copy(morale = (it.morale - fatigue / 30).coerceAtLeast(0)) } else state.armyPools)
        if (theft > 0 || migration != 0 || expired.isNotEmpty()) next = next.copy(chronicle = (next.chronicle +
            ChronicleEntry(state.day, "Gesellschaft reagiert", "Migration $migration; Kriminalität kostet $theft Gold; ${expired.size} Forderungen verfallen.")).takeLast(2000))
        return next
    }
}
