package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.ceil

/** Small facade for campaign actions; daily systems and combat have independent engines. */
object GameEngine {
    data class ActionResult(val state: GameState, val message: String)

    data class StartingAttributes(
        val sword: Int,
        val bow: Int,
        val riding: Int,
        val leadership: Int,
        val tactics: Int,
        val diplomacy: Int,
    ) {
        init {
            require(listOf(sword, bow, riding, leadership, tactics, diplomacy).all { it in 0..100 })
        }
    }

    private fun busy(state: GameState) = state.battleSession?.isActive == true

    fun newGame(
        name: String,
        age: Int,
        species: Species,
        portraitUri: String?,
        startingAttributes: StartingAttributes? = null,
        startingCultures: Set<Culture>? = null,
    ): GameState {
        require(startingCultures == null || startingCultures.isNotEmpty()) {
            "Mindestens eine Startkultur muss gewählt werden."
        }

        // Calls without an explicit culture selection retain the established legacy setup for
        // save/tests/API compatibility. The character-creation UI always passes an explicit set
        // and therefore uses the v0.61.1 fair-start budget below.
        val balancedStart = startingCultures?.let(::balancedStartingForces)
        val population =
            balancedStart?.first
                ?: when (species) {
                    Species.HUMAN -> Population(1500, 0, 0, 0, 120, 0, 0, 0)
                    Species.ELF -> Population(0, 900, 350, 0, 0, 100, 35, 0)
                    Species.HALF_ELF -> Population()
                }
        val units =
            balancedStart?.second
                ?: when (species) {
                    Species.HUMAN ->
                        listOf(
                            UnitAllocation(UnitType.HUMAN_SWORD, 180),
                            UnitAllocation(UnitType.HUMAN_ARCHER, 120),
                            UnitAllocation(UnitType.KNIGHT, 30),
                        )
                    Species.ELF ->
                        listOf(
                            UnitAllocation(UnitType.WOOD_RANGER, 120),
                            UnitAllocation(UnitType.WOOD_BLADE, 100),
                            UnitAllocation(UnitType.GOLD_SPEAR, 40),
                            UnitAllocation(UnitType.GOLD_ARCHER, 30),
                        )
                    Species.HALF_ELF ->
                        listOf(
                            UnitAllocation(UnitType.HUMAN_SWORD, 100),
                            UnitAllocation(UnitType.HUMAN_ARCHER, 80),
                            UnitAllocation(UnitType.WOOD_RANGER, 60),
                            UnitAllocation(UnitType.WOOD_BLADE, 40),
                            UnitAllocation(UnitType.GOLD_SPEAR, 20),
                            UnitAllocation(UnitType.GOLD_ARCHER, 10),
                            UnitAllocation(UnitType.CRANE_GUARD, 40),
                            UnitAllocation(UnitType.EAGLE_CORPS, 30),
                        )
                }
        val commanderCulture =
            startingCultures?.let { preferredCommanderCulture(species, it) }
                ?: if (species == Species.ELF) Culture.WOOD_ELF else Culture.HUMAN
        val commander = startingCommander(commanderCulture)
        val state = GameState(
            player =
                CharacterProfile(
                    name = name.trim().ifBlank { "Leon" }.take(24),
                    age = age.coerceIn(18, 120),
                    species = species,
                    portraitUri = portraitUri,
                    skillPoints = 30,
                    sword = startingAttributes?.sword ?: 55,
                    bow = startingAttributes?.bow ?: 48,
                    riding = startingAttributes?.riding ?: 42,
                    leadership = startingAttributes?.leadership ?: 25,
                    tactics = startingAttributes?.tactics ?: 22,
                    diplomacy = startingAttributes?.diplomacy ?: if (species == Species.HALF_ELF) 15 else 20,
                ),
            population = population,
            armyPools = units.map { ArmyUnitPool(it.type, it.amount) },
            commanders = listOf(commander),
        )
        return DiplomacyEngine.initialize(CharacterEngine.initialize(WorldEngine.initialize(state)))
    }

    private fun balancedStartingForces(cultures: Set<Culture>): Pair<Population, List<UnitAllocation>> {
        val ordered = Culture.entries.filter { it in cultures }
        require(ordered.isNotEmpty())

        fun share(total: Int, index: Int): Int =
            total / ordered.size + if (index < total % ordered.size) 1 else 0

        val populationByCulture =
            ordered.mapIndexed { index, culture -> culture to share(2000, index) }.toMap()
        val recruitsByCulture =
            ordered.mapIndexed { index, culture -> culture to share(160, index) }.toMap()

        fun split(total: Int, weightedTypes: List<Pair<UnitType, Int>>): List<UnitAllocation> {
            val weightSum = weightedTypes.sumOf { it.second }
            var used = 0
            return weightedTypes.mapIndexed { index, (type, weight) ->
                val amount =
                    if (index == weightedTypes.lastIndex) total - used
                    else (total.toLong() * weight / weightSum).toInt()
                used += amount
                UnitAllocation(type, amount)
            }.filter { it.amount > 0 }
        }

        val units =
            ordered.flatMapIndexed { index, culture ->
                val soldiers = share(400, index)
                val mix =
                    when (culture) {
                        Culture.HUMAN ->
                            listOf(
                                UnitType.HUMAN_SWORD to 55,
                                UnitType.HUMAN_ARCHER to 35,
                                UnitType.KNIGHT to 10,
                            )
                        Culture.WOOD_ELF ->
                            listOf(
                                UnitType.WOOD_RANGER to 55,
                                UnitType.WOOD_BLADE to 45,
                            )
                        Culture.GOLD_ELF ->
                            listOf(
                                UnitType.GOLD_SPEAR to 55,
                                UnitType.GOLD_ARCHER to 45,
                            )
                        Culture.WALL ->
                            listOf(
                                UnitType.CRANE_GUARD to 25,
                                UnitType.EAGLE_CORPS to 20,
                                UnitType.TIGER_CORPS to 20,
                                UnitType.BEAR_CORPS to 15,
                                UnitType.DEER_CORPS to 10,
                                UnitType.DRAGON_ARTILLERY to 10,
                            )
                    }
                split(soldiers, mix)
            }

        val population =
            Population(
                human = populationByCulture[Culture.HUMAN] ?: 0,
                woodElf = populationByCulture[Culture.WOOD_ELF] ?: 0,
                goldElf = populationByCulture[Culture.GOLD_ELF] ?: 0,
                wall = populationByCulture[Culture.WALL] ?: 0,
                humanRecruits = recruitsByCulture[Culture.HUMAN] ?: 0,
                woodElfRecruits = recruitsByCulture[Culture.WOOD_ELF] ?: 0,
                goldElfRecruits = recruitsByCulture[Culture.GOLD_ELF] ?: 0,
                wallRecruits = recruitsByCulture[Culture.WALL] ?: 0,
            )
        return population to units
    }

    private fun preferredCommanderCulture(species: Species, cultures: Set<Culture>): Culture =
        when (species) {
            Species.HUMAN -> if (Culture.HUMAN in cultures) Culture.HUMAN else cultures.first()
            Species.ELF ->
                when {
                    Culture.WOOD_ELF in cultures -> Culture.WOOD_ELF
                    Culture.GOLD_ELF in cultures -> Culture.GOLD_ELF
                    else -> cultures.first()
                }
            Species.HALF_ELF -> if (Culture.HUMAN in cultures) Culture.HUMAN else cultures.first()
        }

    private fun startingCommander(culture: Culture): Commander =
        when (culture) {
            Culture.HUMAN -> Commander(1, "Marcus", culture, "knight")
            Culture.WOOD_ELF -> Commander(1, "Caelen", culture, "wood_elf")
            Culture.GOLD_ELF -> Commander(1, "Aelor", culture, "gold_elf")
            Culture.WALL -> Commander(1, "Wei Jian", culture, "wall_guard")
        }

    fun isUnitUnlocked(state: GameState, type: UnitType): Boolean =
        ArmyEngine.population(state.population, type.culture) > 0

    fun advanceDay(state: GameState): ActionResult {
        if (busy(state)) return ActionResult(state, "Entscheide zuerst die laufende Schlacht.")
        var next = EconomyEngine.day(CityEngine.tick(WorldEngine.initialize(state)))
        next = WorldEngine.tick(next)
        next = MissionEngine.tick(next)
        next = WarEngine.tick(next)
        next = RelationshipEngine.day(next)
        next = CharacterEngine.tick(next)
        next = DynastyEngine.tick(next)
        next = DiplomacyEngine.tick(next)
        next = EspionageEngine.tick(next)
        next = SocietyEngine.tick(next)
        next = StoryDirector.tick(next)
        next = EventEngine.day(next)
        next = InvasionEngine.day(next)
        next = ProgressionEngine.update(next)
        next = PresentationEngine.tick(next)
        val message =
            if (next.battleSession?.isActive == true)
                "Die Invasion erreicht deine Festung. Die Verteidigung beginnt!"
            else "Tag ${next.day}: Wirtschaft, Ausbildung und Missionen schreiten voran."
        return ActionResult(next, message)
    }

    fun trainingDays(state: GameState, type: UnitType): Int =
        maxOf(2, type.trainingDays - state.realm.level(BuildingType.BARRACKS))

    fun recruit(state: GameState, type: UnitType, percent: Int): ActionResult {
        if (busy(state)) return ActionResult(state, "Rekrutierung nach der Schlacht möglich.")
        if (!isUnitUnlocked(state, type))
            return ActionResult(
                state,
                "Diese Kultur benötigt zuerst eine eigene Bevölkerung durch Bündnisse oder Immigration.",
            )
        if (percent !in 1..100) return ActionResult(state, "Ungültiger Rekrutierungsanteil.")
        val available = ArmyEngine.recruitable(state, type.culture)
        if (available <= 0) return ActionResult(state, "Keine passenden Rekruten verfügbar.")
        val amount = ceil(available * percent / 100.0).toInt()
        val gold = amount.toLong() * type.goldCost
        val iron = amount.toLong() * type.ironCost
        if (gold > state.resources.gold || iron > state.resources.iron)
            return ActionResult(state, "Benötigt: $gold Gold / $iron Eisen.")
        val days = trainingDays(state, type)
        val order =
            TrainingOrder(
                maxOf(System.nanoTime(), (state.trainingQueue.maxOfOrNull { it.id } ?: 0) + 1),
                type,
                amount,
                days,
            )
        return ActionResult(
            state.copy(
                population = ArmyEngine.adjustRecruits(state.population, type.culture, -amount),
                resources =
                    state.resources.copy(
                        gold = state.resources.gold - gold.toInt(),
                        iron = state.resources.iron - iron.toInt(),
                    ),
                trainingQueue = state.trainingQueue + order,
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Ausbildung begonnen",
                                "$amount ${type.label}, $days Tage bis zur Einsatzbereitschaft.",
                            ))
                        .takeLast(2000),
            ),
            "$amount ${type.label} beginnen die Ausbildung.",
        )
    }

    fun buildingCost(state: GameState, type: BuildingType): Resources {
        val base =
            when (type) {
                BuildingType.FARM -> Resources(140, 0, 70, 20, 0)
                BuildingType.SAWMILL -> Resources(120, 0, 60, 15, 0)
                BuildingType.QUARRY -> Resources(170, 0, 80, 10, 0)
                BuildingType.IRONWORKS -> Resources(220, 0, 120, 80, 0)
                BuildingType.MARKET -> Resources(280, 0, 130, 40, 0)
                BuildingType.BARRACKS -> Resources(300, 0, 180, 100, 0)
                BuildingType.WALL -> Resources(420, 0, 360, 160, 0)
                BuildingType.TOWER -> Resources(360, 0, 300, 140, 0)
                BuildingType.PALACE -> Resources(800, 0, 600, 260, 0)
                BuildingType.RESIDENTIAL -> Resources(180, 0, 100, 30, 0)
                BuildingType.WAREHOUSE -> Resources(220, 0, 140, 60, 0)
                BuildingType.HOSPITAL -> Resources(300, 0, 160, 100, 20)
                BuildingType.ACADEMY -> Resources(450, 0, 220, 180, 40)
                BuildingType.STABLES -> Resources(260, 0, 180, 50, 20)
                BuildingType.ARSENAL -> Resources(400, 0, 200, 160, 60)
                BuildingType.EMBASSY -> Resources(500, 0, 200, 180, 0)
            }
        val next = state.realm.level(type).toLong() + 1
        fun cost(v: Int) = (v * next * next).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Resources(cost(base.gold), 0, cost(base.wood), cost(base.stone), cost(base.iron))
    }

    fun build(state: GameState, type: BuildingType): ActionResult =
        CityEngine.startConstruction(state, type)

    fun buyLand(state: GameState): ActionResult {
        if (busy(state)) return ActionResult(state, "Expansion nach der Schlacht möglich.")
        val cost = 600L + state.realm.territory * 550L
        if (state.resources.gold < cost)
            return ActionResult(state, "Landrecht benötigt $cost Gold.")
        var pop = state.population
        Culture.entries
            .filter { ArmyEngine.population(pop, it) > 0 }
            .forEach { c ->
                val growth =
                    when (c) {
                        Culture.HUMAN -> 180
                        Culture.WOOD_ELF -> 100
                        Culture.GOLD_ELF -> 35
                        Culture.WALL -> 120
                    }
                val before = ArmyEngine.population(pop, c)
                pop = ArmyEngine.adjustPopulation(pop, c, growth)
                pop =
                    ArmyEngine.adjustRecruits(pop, c, (ArmyEngine.population(pop, c) - before) / 5)
            }
        val territory = state.realm.territory + 1
        val region =
            WorldRegion(
                "land_$territory",
                "Grenzland $territory",
                RegionType.OWN,
                MissionType.PATROL,
                true,
            )
        return ActionResult(
            ProgressionEngine.update(
                state.copy(
                    resources = state.resources.copy(gold = state.resources.gold - cost.toInt()),
                    population = pop,
                    realm = state.realm.copy(territory = territory),
                    regions = state.regions + region,
                    chronicle =
                        (state.chronicle +
                                ChronicleEntry(
                                    state.day,
                                    "Neues Gebiet",
                                    "Dein Reich besitzt $territory Gebiete.",
                                ))
                            .takeLast(2000),
                )
            ),
            "Neues Gebiet und zusätzliche Bevölkerung.",
        )
    }

    fun setCommanderAllocation(
        state: GameState,
        commanderId: Long,
        requested: List<UnitAllocation>,
    ): ActionResult = ArmyEngine.allocation(state, commanderId, requested)

    fun freeSoldiersForCommander(state: GameState, commanderId: Long, type: UnitType): Int =
        ArmyEngine.freeForCommander(state, commanderId, type)

    fun promoteCommander(state: GameState): ActionResult {
        if (busy(state)) return ActionResult(state, "Beförderung nach der Schlacht möglich.")
        if (state.homeArmySize < 100)
            return ActionResult(state, "Mindestens 100 Soldaten in der Festung benötigt.")
        if (state.resources.gold < 250) return ActionResult(state, "Beförderung benötigt 250 Gold.")
        val index = state.commanders.count { it.id != COMPANION_COMMANDER_ID }
        val cultures =
            state.armyPools
                .filter { state.homeSoldiers(it.type) > 0 }
                .map { it.type.culture }
                .distinct()
        val culture = cultures[index % cultures.size]
        val names =
            when (culture) {
                Culture.HUMAN -> listOf("Marcus", "Edric", "Rowan", "Gareth")
                Culture.WOOD_ELF -> listOf("Caelen", "Lethriel", "Faelar", "Sylwen")
                Culture.GOLD_ELF -> listOf("Aelor", "Thalion", "Eryndor", "Caladren")
                Culture.WALL -> listOf("Wei Jian", "Lin Zhao", "Shen Rui", "Mei Ren")
            }
        val name = names[index % names.size] + if (index >= 4) " ${index+1}" else ""
        val key =
            when (culture) {
                Culture.HUMAN -> "knight"
                Culture.WOOD_ELF -> "wood_elf"
                Culture.GOLD_ELF -> "gold_elf"
                Culture.WALL -> "wall_guard"
            }
        val c =
            Commander(
                maxOf(System.nanoTime(), (state.commanders.maxOfOrNull { it.id } ?: 0) + 1),
                name,
                culture,
                key,
                trait = listOf("Loyal", "Mutig", "Bedacht", "Diszipliniert")[index % 4],
            )
        return ActionResult(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold - 250),
                commanders = state.commanders + c,
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Neue Führungskraft",
                                "$name übernimmt ein eigenes Kommando.",
                            ))
                        .takeLast(2000),
            ),
            "$name befördert. Truppen können nun zugewiesen werden.",
        )
    }

    fun trainCommander(state: GameState, id: Long): ActionResult {
        if (busy(state) || state.commanderAway(id))
            return ActionResult(state, "Kommandant ist im Einsatz.")
        val c =
            state.commanders.find { it.id == id }
                ?: return ActionResult(state, "Kommandant nicht gefunden.")
        if (id == COMPANION_COMMANDER_ID) return RelationshipEngine.action(state, "train")
        if (state.resources.gold < 120) return ActionResult(state, "Training benötigt 120 Gold.")
        val academyBonus = state.realm.level(BuildingType.ACADEMY).coerceAtMost(3)
        val updated =
            c.copy(
                level = c.level + 1,
                sword = (c.sword + 1).coerceAtMost(100),
                bow = (c.bow + 1).coerceAtMost(100),
                leadership = (c.leadership + 2 + academyBonus).coerceAtMost(100),
                tactics = (c.tactics + 2 + academyBonus).coerceAtMost(100),
                siege = (c.siege + 1).coerceAtMost(100),
                loyalty = (c.loyalty + 1).coerceAtMost(100),
                rank = if (c.level >= 8) "Marschall" else if (c.level >= 4) "General" else c.rank,
            )
        return ActionResult(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold - 120),
                commanders = state.commanders.map { if (it.id == id) updated else it },
            ),
            "${c.name} hat trainiert.",
        )
    }

    fun repairWall(state: GameState): ActionResult {
        if (busy(state)) return ActionResult(state, "Mauer während der Schlacht nicht reparierbar.")
        val damage = 100 - state.realm.wallIntegrity
        if (damage <= 0) return ActionResult(state, "Die Mauer ist intakt.")
        val cost = damage * maxOf(1, state.realm.level(BuildingType.WALL)) * 5
        if (state.resources.stone < cost || state.resources.gold < cost)
            return ActionResult(state, "Reparatur benötigt $cost Stein und $cost Gold.")
        return ActionResult(
            state.copy(
                resources =
                    state.resources.copy(
                        stone = state.resources.stone - cost,
                        gold = state.resources.gold - cost,
                    ),
                realm = state.realm.copy(wallIntegrity = 100),
            ),
            "Mauer vollständig repariert.",
        )
    }

    fun requestAllies(state: GameState): ActionResult {
        if (busy(state))
            return ActionResult(state, "Verbündete müssen vor dem Angriff gerufen werden.")
        val invasion = state.invasion ?: return ActionResult(state, "Keine angekündigte Invasion.")
        if (invasion.alliesRequested)
            return ActionResult(state, "Verbündete wurden bereits angefordert.")
        if (state.world.initialized) return requestWorldAllies(state, invasion)
        val cost = (600 - state.player.diplomacy * 4 - state.realm.level(BuildingType.EMBASSY).coerceAtMost(5) * 30).coerceAtLeast(150)
        if (state.resources.gold < cost)
            return ActionResult(state, "Boten und Unterstützung benötigen $cost Gold.")
        val reduction = (0.1 + state.player.diplomacy / 500.0).coerceAtMost(0.3)
        return ActionResult(
            state.copy(
                resources = state.resources.copy(gold = state.resources.gold - cost),
                invasion =
                    invasion.copy(
                        alliesRequested = true,
                        strength = (invasion.strength * (1 - reduction)).toInt().coerceAtLeast(1),
                    ),
                chronicle =
                    (state.chronicle +
                            ChronicleEntry(
                                state.day,
                                "Verbündete mobilisiert",
                                "Unterstützung bindet feindliche Kräfte; die angreifende Streitmacht wird kleiner.",
                            ))
                        .takeLast(2000),
            ),
            "Verbündete schwächen den anrückenden Feind.",
        )
    }

    private fun requestWorldAllies(state: GameState, invasion: Invasion): ActionResult {
        val enemy = state.world.armies.firstOrNull { it.id == (invasion.worldArmyId ?: state.world.invasionArmyId) }
            ?: return ActionResult(state, "Zuerst muss das anrückende Heer bestätigt werden.")
        val allies = DiplomacyEngine.defensiveAllies(state, PLAYER_FACTION)
        val army = state.world.armies.firstOrNull { it.factionId in allies && it.status.isAway && it.missionId == null && it.total > 0 && it.id != state.world.invasionArmyId }
            ?: return ActionResult(state, "Ein Verteidigungsbündnis und ein verfügbares verbündetes Heer werden benötigt.")
        val faction = state.world.faction(army.factionId) ?: return ActionResult(state, "Verbündetes Reich fehlt.")
        val target = state.world.knowledgeFor(PLAYER_FACTION).observations.firstOrNull { it.armyId == enemy.id && it.day >= state.day - 3 }?.regionId ?: "village"
        val route = WorldEngine.route(state.world, army.regionId, target)
        if (route.isEmpty() || !WorldEngine.routeAllowed(state, army.factionId, route))
            return ActionResult(state, "Das verbündete Heer findet keinen erlaubten Weg zur Grenze.")
        val days = WorldEngine.travelDays(state, army, route)
        val provisions = (army.dailyFood.toLong() * (days + 3)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val topUp = (provisions - army.supplyFood).coerceAtLeast(0)
        if (faction.food < topUp) return ActionResult(state, "Der Verbündete hat nicht genug Vorräte für diesen Einsatz.")
        val cost = (600 - state.player.diplomacy * 4 - state.realm.level(BuildingType.EMBASSY).coerceAtMost(5) * 30).coerceAtLeast(150)
        if (state.resources.gold < cost) return ActionResult(state, "Boten und Unterstützung benötigen $cost Gold.")
        var next = state.copy(resources = state.resources.copy(gold = state.resources.gold - cost),
            invasion = invasion.copy(alliesRequested = true),
            diplomacy = state.diplomacy.copy(treaties = state.diplomacy.treaties.filterNot { it.connects(army.factionId, enemy.factionId) }),
            world = state.world.copy(armies = state.world.armies.map { if (it.id == army.id) it.copy(destinationId = target, route = route,
                routeIndex = 0, legProgress = 0, arrivalDay = state.day + days, supplyFood = army.supplyFood + topUp, status = WorldArmyStatus.MARCHING) else it },
                factions = state.world.factions.map { if (it.id == faction.id) it.copy(food = it.food - topUp, gold = (it.gold.toLong() + cost).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else it }))
        next = DiplomacyEngine.changeRelation(next, army.factionId, enemy.factionId, -15, -10, "Verteidigungsbündnis: Hilfe für die Letzte Mauer", atWar = true)
        return ActionResult(next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, "Verbündete mobilisiert",
            "${army.name} marschiert mit ${army.total} Soldaten zur Grenze. Geschätzte Ankunft Tag ${state.day + days}; der Feind verliert erst im tatsächlichen Kampf Truppen.")).takeLast(2000)),
            "${army.name} wurde mobilisiert; Reisezeit etwa $days Tage.")
    }

    fun companionAction(state: GameState, action: String): ActionResult =
        RelationshipEngine.action(state, action)

    fun updateCompanionIdentity(state: GameState, name: String, portraitUri: String?): GameState =
        RelationshipEngine.syncCommander(
            state.copy(
                companion =
                    state.companion.copy(
                        name = name.ifBlank { state.companion.name },
                        portraitUri = portraitUri ?: state.companion.portraitUri,
                    )
            )
        )

    fun updatePlayerPortrait(state: GameState, portraitUri: String?): GameState =
        state.copy(player = state.player.copy(portraitUri = portraitUri))

    fun markTutorialSeen(state: GameState): GameState = state.copy(tutorialSeen = true)
}
