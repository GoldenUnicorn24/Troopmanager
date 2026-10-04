package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Treaty commitments, bilateral memories and payments share the campaign's real treasuries. */
object DiplomacyEngine {
    fun initialize(state: GameState): GameState {
        if (!state.world.initialized) return state
        val relations = state.diplomacy.relations.toMutableList()
        state.world.factions.forEachIndexed { index, first ->
            state.world.factions.drop(index + 1).forEach { second ->
                if (relations.none { it.connects(first.id, second.id) }) {
                    val score = first.relations[second.id] ?: second.relations[first.id] ?: 0
                    val war = second.id in first.wars || first.id in second.wars
                    relations += DiplomacyRelation(first.id, second.id, score,
                        trust = (35 + score / 3).coerceIn(0, 100), atWar = war,
                        warStartedDay = if (war) state.day else null)
                }
            }
        }
        val politics = state.diplomacy.politics + state.world.factions.filter {
            it.id !in listOf(PLAYER_FACTION, NEUTRAL_FACTION) && state.diplomacy.politics.none { p -> p.factionId == it.id }
        }.map { FactionPolitics(it.id, lastAgedDay = state.day) }
        return state.copy(diplomacy = state.diplomacy.copy(initialized = true, relations = relations, politics = politics))
    }

    fun relation(state: GameState, first: String, second: String): DiplomacyRelation =
        state.diplomacy.relations.firstOrNull { it.connects(first, second) }
            ?: DiplomacyRelation(first, second, relation = state.world.faction(first)?.relations?.get(second) ?: 0,
                atWar = second in (state.world.faction(first)?.wars ?: emptyList()))

    fun atWar(state: GameState, first: String, second: String): Boolean = relation(state, first, second).atWar

    fun hasTreaty(state: GameState, first: String, second: String, kind: TreatyKind): Boolean =
        state.diplomacy.treaties.any { it.kind == kind && it.connects(first, second) && it.expiresDay > state.day }

    fun canEnterTerritory(state: GameState, first: String, second: String): Boolean =
        first == second || second == NEUTRAL_FACTION || atWar(state, first, second) ||
            listOf(TreatyKind.MILITARY_ACCESS, TreatyKind.DEFENSIVE_ALLIANCE, TreatyKind.OFFENSIVE_ALLIANCE,
                TreatyKind.VASSAL, TreatyKind.PROTECTION, TreatyKind.DYNASTIC_ALLIANCE).any { hasTreaty(state, first, second, it) }

    fun defensiveAllies(state: GameState, factionId: String): List<String> =
        state.diplomacy.treaties.filter { it.expiresDay > state.day &&
            it.kind in listOf(TreatyKind.DEFENSIVE_ALLIANCE, TreatyKind.VASSAL, TreatyKind.PROTECTION, TreatyKind.DYNASTIC_ALLIANCE) &&
            (it.firstFactionId == factionId || it.secondFactionId == factionId) }
            .map { if (it.firstFactionId == factionId) it.secondFactionId else it.firstFactionId }.distinct()

    private fun safe(value: Long) = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    private fun value(resources: Resources): Long = resources.gold.toLong() + resources.food / 2L +
        resources.wood.toLong() + resources.stone * 2L + resources.iron * 3L

    private fun affordable(stock: Resources, cost: Resources) = ResourceKind.entries.all {
        it.value(cost) >= 0 && it.value(stock) >= it.value(cost)
    }

    /** Changes are mirrored in WorldFaction so strategic AI and UI see one diplomatic truth. */
    fun changeRelation(state: GameState, first: String, second: String, delta: Int, trustDelta: Int,
        text: String, atWar: Boolean? = null): GameState {
        val old = relation(state, first, second)
        val war = atWar ?: old.atWar
        val updated = old.copy(relation = (old.relation + delta).coerceIn(-100, 100),
            trust = (old.trust + trustDelta).coerceIn(0, 100),
            respect = (old.respect + if (trustDelta > 0) 2 else if (trustDelta < -5) -4 else 0).coerceIn(0, 100),
            fear = (old.fear + if (war && !old.atWar) 10 else 0).coerceIn(0, 100), atWar = war,
            warStartedDay = if (war) old.warStartedDay ?: state.day else null,
            playerWarGoal = if (war && !old.atWar && PLAYER_FACTION in listOf(first, second)) WarGoal.FORCE_PEACE else old.playerWarGoal,
            warGoalRegionId = if (war && !old.atWar) null else old.warGoalRegionId,
            warGoalAllyId = if (war && !old.atWar) null else old.warGoalAllyId,
            warLosses = if (war && !old.atWar) emptyMap() else old.warLosses,
            history = (old.history + DiplomaticMemory(state.day, text)).takeLast(40))
        return state.copy(diplomacy = state.diplomacy.copy(relations =
            state.diplomacy.relations.filterNot { it.connects(first, second) } + updated),
            world = state.world.copy(factions = state.world.factions.map { faction ->
                val other = when (faction.id) { first -> second; second -> first; else -> null }
                if (other == null) faction else faction.copy(relations = faction.relations + (other to updated.relation),
                    wars = if (war) (faction.wars + other).distinct() else faction.wars - other)
            }, enemyCommanders = state.world.enemyCommanders.map { commander ->
                val opponent = if (first == PLAYER_FACTION) second else if (second == PLAYER_FACTION) first else null
                if (commander.factionId == opponent && war && !old.atWar) commander.copy(
                    memories = (commander.memories + "Tag ${state.day}: $text").takeLast(20),
                    rivalry = (commander.rivalry + if (trustDelta <= -30) 10 else 3).coerceAtMost(100)) else commander
            }))
    }

    private fun score(state: GameState, target: WorldFaction, proposal: DiplomaticProposal): Int {
        val r = relation(state, PLAYER_FACTION, target.id)
        val ambassador = CharacterEngine.bonuses(state).diplomacy
        val temperament = when (target.personality) {
            FactionPersonality.DIPLOMATIC, FactionPersonality.HONORABLE -> 12
            FactionPersonality.MERCANTILE -> if (proposal.kind == TreatyKind.TRADE) 22 else 3
            FactionPersonality.PARANOID, FactionPersonality.ISOLATIONIST -> -18
            FactionPersonality.AGGRESSIVE, FactionPersonality.EXPANSIONIST -> -8
            else -> 0
        }
        val observedArmy = state.world.knowledgeFor(target.id).observations.filter {
            it.factionId == PLAYER_FACTION && state.day - it.day <= 30
        }.groupBy { it.armyId }.values.sumOf { reports ->
            val newest = reports.maxBy { it.day }; (newest.minimum.toLong() + newest.maximum) / 2
        }
        val armyPressure = (observedArmy * 20 / target.population.coerceAtLeast(1)).coerceAtMost(25).toInt()
        val burden = when (proposal.kind) {
            TreatyKind.NON_AGGRESSION, TreatyKind.TRADE -> 15
            TreatyKind.PEACE -> if (r.atWar) 22 - (state.day - (r.warStartedDay ?: state.day)).coerceAtMost(20) else 5
            TreatyKind.MILITARY_ACCESS -> 32
            TreatyKind.DEFENSIVE_ALLIANCE, TreatyKind.PROTECTION -> 40
            TreatyKind.OFFENSIVE_ALLIANCE, TreatyKind.DYNASTIC_ALLIANCE -> 52
            TreatyKind.TRIBUTE -> if (proposal.playerPaysTribute) 8 else 55
            TreatyKind.VASSAL -> if (proposal.playerPaysTribute) 5 else 75
        }
        val gift = ((value(proposal.offered) - value(proposal.requested)) / 25).coerceIn(-100, 100).toInt()
        return r.relation / 3 + r.trust / 3 + r.respect / 5 + r.fear / 10 + temperament +
            state.player.diplomacy / 4 + OriginEngine.diplomacyBonus(state) +
            ambassador + armyPressure + gift - burden +
            if (proposal.kind == TreatyKind.PEACE) WarGoalsEngine.peacePressure(state, target.id) else 0
    }

    fun propose(state: GameState, targetFactionId: String, kind: TreatyKind,
        offered: Resources = Resources(0, 0, 0, 0, 0), requested: Resources = Resources(0, 0, 0, 0, 0),
        durationDays: Int = 60, tributeGold: Int = 50, playerPaysTribute: Boolean = true): GameEngine.ActionResult {
        val next = initialize(state)
        val target = next.world.faction(targetFactionId)
            ?: return GameEngine.ActionResult(state, "Unbekanntes Reich.")
        if (targetFactionId == PLAYER_FACTION || state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Diplomatie ist derzeit nicht möglich.")
        if (kind == TreatyKind.DYNASTIC_ALLIANCE && !state.settings.dynasty)
            return GameEngine.ActionResult(state, "Dynastische Politik ist in den Einstellungen deaktiviert.")
        if (requested.wood != 0 || requested.stone != 0 || requested.iron != 0 ||
            !affordable(next.resources, offered) || !affordable(Resources(target.gold, target.food, 0, 0, 0), requested) ||
            durationDays !in 7..365 || tributeGold !in 0..10000)
            return GameEngine.ActionResult(state, "Ressourcen, Laufzeit oder Tributhöhe sind ungültig.")
        if (atWar(next, PLAYER_FACTION, targetFactionId) && kind != TreatyKind.PEACE)
            return GameEngine.ActionResult(state, "Im Krieg muss zuerst Frieden ausgehandelt werden.")
        if (hasTreaty(next, PLAYER_FACTION, targetFactionId, kind))
            return GameEngine.ActionResult(state, "Dieser Vertrag besteht bereits.")
        val proposal = DiplomaticProposal("proposal_${next.diplomacy.nextId}", targetFactionId, kind,
            offered, requested, durationDays, tributeGold, playerPaysTribute, next.day, next.day + 7)
        val evaluation = score(next, target, proposal)
        if (evaluation >= 20) return sign(next, proposal)
        if (evaluation < -65) return GameEngine.ActionResult(next.copy(diplomacy = next.diplomacy.copy(
            proposals = (next.diplomacy.proposals + proposal.copy(status = ProposalStatus.DECLINED,
                explanation = "${target.ruler} lehnt wegen Misstrauen und Vertragsrisiken ab.")).takeLast(24),
            nextId = next.diplomacy.nextId + 1)), "${target.name} lehnt das Angebot ab.")
        val extra = (20 - evaluation) * 25
        val counter = proposal.copy(offered = offered.copy(gold = safe(offered.gold.toLong() + extra)),
            explanation = "${target.ruler} verlangt $extra zusätzliches Gold für Nutzen und Risiko des Vertrags.")
        return GameEngine.ActionResult(next.copy(diplomacy = next.diplomacy.copy(
            proposals = (next.diplomacy.proposals.filterNot { it.targetFactionId == targetFactionId &&
                it.status == ProposalStatus.COUNTER_OFFER } + counter).takeLast(24), nextId = next.diplomacy.nextId + 1)),
            counter.explanation)
    }

    fun acceptCounter(state: GameState, proposalId: String): GameEngine.ActionResult {
        val proposal = state.diplomacy.proposals.firstOrNull { it.id == proposalId }
            ?: return GameEngine.ActionResult(state, "Angebot nicht gefunden.")
        if (proposal.status != ProposalStatus.COUNTER_OFFER || proposal.expiresDay <= state.day)
            return GameEngine.ActionResult(state, "Dieses Angebot ist nicht mehr gültig.")
        if (state.battleSession?.isActive == true || (proposal.kind == TreatyKind.DYNASTIC_ALLIANCE && !state.settings.dynasty))
            return GameEngine.ActionResult(state, "Dieser Vertrag ist derzeit nicht verfügbar.")
        if (atWar(state, PLAYER_FACTION, proposal.targetFactionId) && proposal.kind != TreatyKind.PEACE)
            return GameEngine.ActionResult(state, "Die politische Lage hat sich geändert: zuerst Frieden schließen.")
        if (hasTreaty(state, PLAYER_FACTION, proposal.targetFactionId, proposal.kind))
            return GameEngine.ActionResult(state, "Der Vertrag besteht bereits.")
        return sign(state, proposal)
    }

    fun declineCounter(state: GameState, proposalId: String): GameEngine.ActionResult = GameEngine.ActionResult(
        state.copy(diplomacy = state.diplomacy.copy(proposals = state.diplomacy.proposals.map {
            if (it.id == proposalId && it.status == ProposalStatus.COUNTER_OFFER) it.copy(status = ProposalStatus.DECLINED) else it
        })), "Gegenangebot abgelehnt.")

    private fun sign(state: GameState, proposal: DiplomaticProposal): GameEngine.ActionResult {
        val target = state.world.faction(proposal.targetFactionId)
            ?: return GameEngine.ActionResult(state, "Reich nicht gefunden.")
        if (!affordable(state.resources, proposal.offered) ||
            !affordable(Resources(target.gold, target.food, 0, 0, 0), proposal.requested))
            return GameEngine.ActionResult(state, "Die vereinbarten Ressourcen sind nicht verfügbar.")
        var resources = state.resources
        ResourceKind.entries.forEach { kind ->
            val amount = kind.value(resources).toLong() - kind.value(proposal.offered) + kind.value(proposal.requested)
            if (amount > kind.value(state.city.storageCapacity))
                return GameEngine.ActionResult(state, "Für die Zahlung reicht die Lagerkapazität nicht aus.")
            resources = kind.withValue(resources, safe(amount))
        }
        val payer = if (proposal.kind in listOf(TreatyKind.TRIBUTE, TreatyKind.VASSAL, TreatyKind.PROTECTION))
            if (proposal.playerPaysTribute) PLAYER_FACTION else target.id else null
        val treaty = Treaty("treaty_${state.diplomacy.nextId}", proposal.kind, PLAYER_FACTION, target.id,
            state.day, state.day + proposal.durationDays, payer, if (payer != null) proposal.tributeGold else 0, state.day)
        var next = state.copy(resources = resources, world = state.world.copy(factions = state.world.factions.map {
            if (it.id == target.id) it.copy(gold = safe(it.gold.toLong() + proposal.offered.gold - proposal.requested.gold +
                proposal.offered.wood + proposal.offered.stone * 2L + proposal.offered.iron * 3L),
                food = safe(it.food.toLong() + proposal.offered.food - proposal.requested.food)) else it
        }), diplomacy = state.diplomacy.copy(treaties = state.diplomacy.treaties + treaty,
            proposals = (state.diplomacy.proposals.filterNot { it.id == proposal.id } +
                proposal.copy(status = ProposalStatus.ACCEPTED, explanation = "Vertrag unterzeichnet.")).takeLast(24),
            nextId = state.diplomacy.nextId + 1))
        next = changeRelation(next, PLAYER_FACTION, target.id, 6, 5, "${proposal.kind.label} unterzeichnet.",
            atWar = if (proposal.kind == TreatyKind.PEACE) false else null)
        return GameEngine.ActionResult(next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day,
            "${proposal.kind.label} mit ${target.name}", "Vertrag bis Tag ${treaty.expiresDay}; Zahlungen sofort übertragen.")).takeLast(2000)),
            "${target.name}: ${proposal.kind.label} geschlossen.")
    }

    fun declareWar(state: GameState, targetFactionId: String): GameEngine.ActionResult {
        val next = initialize(state)
        if (targetFactionId == PLAYER_FACTION || targetFactionId == NEUTRAL_FACTION ||
            next.world.faction(targetFactionId) == null || state.battleSession?.isActive == true)
            return GameEngine.ActionResult(state, "Dieses Reich kann nicht angegriffen werden.")
        if (atWar(next, PLAYER_FACTION, targetFactionId)) return GameEngine.ActionResult(state, "Es herrscht bereits Krieg.")
        return GameEngine.ActionResult(startWar(next, PLAYER_FACTION, targetFactionId), "Krieg erklärt. Vertragsbruch schadet Vertrauen und Ruf.")
    }

    private fun startWar(state: GameState, attacker: String, defender: String): GameState {
        val defenderAllies = defensiveAllies(state, defender).filter { it != attacker }
        val offensiveAllies = state.diplomacy.treaties.filter { it.kind == TreatyKind.OFFENSIVE_ALLIANCE &&
            it.expiresDay > state.day && (it.firstFactionId == attacker || it.secondFactionId == attacker) }
            .map { if (it.firstFactionId == attacker) it.secondFactionId else it.firstFactionId }.filter { it != defender }
        val breach = state.diplomacy.treaties.any { it.connects(attacker, defender) && it.expiresDay > state.day }
        var next = state.copy(diplomacy = state.diplomacy.copy(treaties =
            state.diplomacy.treaties.filterNot { it.connects(attacker, defender) }))
        next = changeRelation(next, attacker, defender, -45, if (breach) -40 else -15,
            if (breach) "Krieg trotz Vertrag: das Vertrauen ist gebrochen." else "Krieg erklärt.", true)
        defenderAllies.forEach {
            next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties.filterNot { treaty -> treaty.connects(attacker, it) }))
            next = changeRelation(next, attacker, it, -25, -12, "Verteidigungsbündnis mobilisiert; widersprüchliche Verträge aufgehoben.", true)
        }
        offensiveAllies.forEach {
            next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties.filterNot { treaty -> treaty.connects(it, defender) }))
            next = changeRelation(next, it, defender, -25, -12, "Offensivbündnis mobilisiert; widersprüchliche Verträge aufgehoben.", true)
        }
        if (breach) next.world.factions.filter { it.id != attacker && it.id != defender }.forEach {
            next = changeRelation(next, attacker, it.id, -8, -10, "Der Vertragsbruch gegen $defender erschüttert das Vertrauen.")
        }
        return next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, "Krieg erklärt",
            "${state.world.faction(attacker)?.name} gegen ${state.world.faction(defender)?.name}; Verbündete reagieren.")).takeLast(2000))
    }

    fun tick(state: GameState): GameState {
        var next = initialize(state)
        if (!next.diplomacy.initialized || next.diplomacy.lastTickDay >= state.day) return next
        next = next.copy(diplomacy = next.diplomacy.copy(lastTickDay = state.day,
            treaties = next.diplomacy.treaties.filter { it.expiresDay > state.day },
            proposals = next.diplomacy.proposals.map { if (it.status == ProposalStatus.COUNTER_OFFER &&
                it.expiresDay <= state.day) it.copy(status = ProposalStatus.EXPIRED) else it }))
        next.diplomacy.treaties.toList().forEach { treaty ->
            if (treaty.kind == TreatyKind.TRADE) next = tradeDay(next, treaty)
            if (treaty.payerFactionId != null && treaty.tributeGold > 0 && state.day - treaty.lastPaymentDay >= 7)
                next = tributeDay(next, treaty)
        }
        next = evolvePolitics(next)
        if (state.day - next.diplomacy.lastAiDecisionDay >= 7) next = politicalAi(next)
        return shareIntelligence(next)
    }

    private fun tributeDay(state: GameState, treaty: Treaty): GameState {
        val payer = treaty.payerFactionId ?: return state
        val receiver = if (payer == treaty.firstFactionId) treaty.secondFactionId else treaty.firstFactionId
        val available = if (payer == PLAYER_FACTION) state.resources.gold else state.world.faction(payer)?.gold ?: 0
        val paid = minOf(available, treaty.tributeGold)
        var next = transferGold(state, payer, receiver, paid)
        next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties.map {
            if (it.id == treaty.id) it.copy(lastPaymentDay = state.day) else it
        }))
        if (paid < treaty.tributeGold) next = changeRelation(next, payer, receiver, -10, -12,
            "Tribut ausgefallen: $paid von ${treaty.tributeGold} Gold.")
        return next
    }

    private fun transferGold(state: GameState, payer: String, receiver: String, amount: Int): GameState {
        val receiverStock = if (receiver == PLAYER_FACTION) state.resources.gold else state.world.faction(receiver)?.gold ?: 0
        val cap = if (receiver == PLAYER_FACTION) state.city.storageCapacity.gold else Int.MAX_VALUE
        val paid = minOf(amount, (cap.toLong() - receiverStock).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return state.copy(resources = state.resources.copy(gold = when (PLAYER_FACTION) {
            payer -> state.resources.gold - paid; receiver -> state.resources.gold + paid; else -> state.resources.gold }),
            world = state.world.copy(factions = state.world.factions.map {
                when (it.id) { payer -> it.copy(gold = (it.gold - paid).coerceAtLeast(0));
                    receiver -> it.copy(gold = safe(it.gold.toLong() + paid)); else -> it }
            }))
    }

    /** Daily caravan trades consume the seller's food and buyer's gold; there is no generated subsidy. */
    private fun tradeDay(state: GameState, treaty: Treaty): GameState {
        val firstFood = if (treaty.firstFactionId == PLAYER_FACTION) state.resources.food else state.world.faction(treaty.firstFactionId)?.food ?: 0
        val secondFood = if (treaty.secondFactionId == PLAYER_FACTION) state.resources.food else state.world.faction(treaty.secondFactionId)?.food ?: 0
        if (kotlin.math.abs(firstFood.toLong() - secondFood) < 300) return state
        val seller = if (firstFood > secondFood) treaty.firstFactionId else treaty.secondFactionId
        val buyer = if (seller == treaty.firstFactionId) treaty.secondFactionId else treaty.firstFactionId
        val buyerGold = if (buyer == PLAYER_FACTION) state.resources.gold else state.world.faction(buyer)?.gold ?: 0
        val buyerFood = if (buyer == PLAYER_FACTION) state.resources.food else state.world.faction(buyer)?.food ?: 0
        val cap = if (buyer == PLAYER_FACTION) state.city.storageCapacity.food else Int.MAX_VALUE
        val quantity = minOf(40, buyerGold, (cap.toLong() - buyerFood).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        if (quantity <= 0) return state
        val next = transferGold(state, buyer, seller, quantity)
        val actual = if (buyer == PLAYER_FACTION) state.resources.gold - next.resources.gold else
            (state.world.faction(buyer)?.gold ?: 0) - (next.world.faction(buyer)?.gold ?: 0)
        return next.copy(resources = next.resources.copy(food = when (PLAYER_FACTION) {
            seller -> next.resources.food - actual; buyer -> next.resources.food + actual; else -> next.resources.food }),
            world = next.world.copy(factions = next.world.factions.map {
                when (it.id) { seller -> it.copy(food = (it.food - actual).coerceAtLeast(0));
                    buyer -> it.copy(food = safe(it.food.toLong() + actual)); else -> it }
            }), city = next.city.copy(prosperity = if (PLAYER_FACTION in listOf(seller, buyer))
                (next.city.prosperity + 1).coerceAtMost(100) else next.city.prosperity))
    }

    /** Rulers, hunger and loyalty produce history from existing treasuries, people and banners. */
    private fun evolvePolitics(state: GameState): GameState {
        var next = state
        state.diplomacy.politics.forEach { original ->
            var politics = original
            var faction = next.world.faction(original.factionId) ?: return@forEach
            fun record(title: String, text: String) {
                politics = politics.copy(lastCrisisDay = state.day, history =
                    (politics.history + DiplomaticMemory(state.day, "$title: $text")).takeLast(30))
                next = next.copy(chronicle = (next.chronicle + ChronicleEntry(state.day, "${faction.name}: $title", text)).takeLast(2000),
                    world = next.world.copy(factions = next.world.factions.map {
                        if (it.id == faction.id) it.copy(lastDecision = title, lastDecisionDay = state.day) else it }))
            }
            val years = ((state.day - politics.lastAgedDay).coerceAtLeast(0) / 365)
            if (years > 0) {
                faction = faction.copy(rulerAge = (faction.rulerAge.toLong() + years).coerceAtMost(150).toInt())
                politics = politics.copy(lastAgedDay = politics.lastAgedDay + years * 365)
                val oldAge = faction.rulerAge >= 95 || (faction.rulerAge >= 78 &&
                    ((faction.id.hashCode().toLong() + state.day * 31L).and(Long.MAX_VALUE) % 100) < faction.rulerAge - 75)
                if (oldAge) {
                    val oldRuler = faction.ruler
                    val deathAge = faction.rulerAge
                    val commanders = next.world.enemyCommanders.filter { it.factionId == faction.id }
                    val successor = commanders.maxByOrNull { it.rulerLoyalty + it.experience / 20 }
                    val disputed = commanders.any { it.rulerLoyalty < 45 }
                    faction = faction.copy(ruler = successor?.name ?: "Erbin von ${next.world.place(faction.capitalId)?.name ?: faction.name}",
                        rulerAge = 30, successionCount = safe(faction.successionCount.toLong() + 1))
                    politics = politics.copy(stability = (politics.stability - if (disputed) 22 else 8).coerceAtLeast(0),
                        disputedSuccessionUntilDay = if (disputed) state.day + 30 else 0)
                    next = next.copy(world = next.world.copy(enemyCommanders = next.world.enemyCommanders.map {
                        if (it.factionId != faction.id) it else it.copy(rulerLoyalty =
                            if (it.id == successor?.id) 90 else (it.rulerLoyalty - if (disputed) 10 else 0).coerceAtLeast(0)) },
                        factions = next.world.factions.map { if (it.id == faction.id) faction else it }))
                    record(if (disputed) "Umstrittene Nachfolge" else "Herrscherwechsel",
                        "$oldRuler stirbt im Alter von $deathAge. ${faction.ruler} übernimmt; ${if (disputed) "Rivalen bestreiten die Nachfolge." else "der Hof wahrt die Kontinuität."}")
                } else next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == faction.id) faction else it }))
            }
            faction = next.world.faction(original.factionId) ?: return@forEach
            val hungry = faction.food < faction.population.coerceAtLeast(1) / 2
            val fatigue = (politics.warExhaustion + if (faction.wars.isNotEmpty()) 1 else -3).coerceIn(0, 100)
            val hungerDays = if (hungry) safe(politics.hungerDays.toLong() + 1) else (politics.hungerDays - 3).coerceAtLeast(0)
            val commanders = next.world.enemyCommanders.filter { it.factionId == faction.id }
            val loyalty = if (commanders.isEmpty()) 60 else commanders.map { it.rulerLoyalty }.average().toInt()
            val stabilityTarget = (65 + loyalty / 10 - fatigue / 3 - hungerDays.coerceAtMost(100) / 2 -
                if (politics.disputedSuccessionUntilDay > state.day) 15 else 0).coerceIn(0, 100)
            politics = politics.copy(warExhaustion = fatigue, hungerDays = hungerDays,
                stability = (politics.stability + (stabilityTarget - politics.stability).coerceIn(-2, 2)).coerceIn(0, 100))
            if (hungry && hungerDays >= 7 && state.day % 7 == 0) {
                val known = next.world.knowledgeFor(faction.id).exploredRegions.mapNotNull { next.world.place(it)?.ownerId }.distinct()
                val seller = next.world.factions.filter { it.id != PLAYER_FACTION && it.id != faction.id && it.id in known &&
                    it.food > it.population && it.gold <= Int.MAX_VALUE - 120 && !atWar(next, faction.id, it.id) }.maxByOrNull { it.food }
                if (seller != null && faction.gold >= 120) {
                    val food = minOf(240, seller.food)
                    next = next.copy(world = next.world.copy(factions = next.world.factions.map { when (it.id) {
                        faction.id -> it.copy(gold = it.gold - 120, food = safe(it.food.toLong() + food))
                        seller.id -> it.copy(gold = safe(it.gold.toLong() + 120), food = it.food - food)
                        else -> it
                    } }))
                    record("Nahrungskrise", "${faction.ruler} kauft $food Nahrung von ${seller.name} für 120 Gold aus der eigenen Schatzkammer.")
                } else {
                    val troops = next.world.armies.filter { it.factionId == faction.id }.sumOf { it.total.toLong() } +
                        next.world.recruitments.filter { it.factionId == faction.id }.sumOf { it.amount.toLong() }
                    val leaving = minOf((faction.population.toLong() - troops).coerceAtLeast(0), faction.population / 200L + 1).toInt()
                    val affectedPlace = next.world.places.filter { it.ownerId == faction.id }.maxByOrNull { it.population }?.id
                    next = next.copy(world = next.world.copy(factions = next.world.factions.map {
                        if (it.id == faction.id) it.copy(population = it.population - leaving) else it }, places = next.world.places.map {
                        if (it.ownerId == faction.id) it.copy(prosperity = (it.prosperity - 3).coerceAtLeast(0),
                            population = (it.population - if (it.id == affectedPlace) leaving.coerceAtMost(it.population) else 0).coerceAtLeast(0)) else it }, armies = next.world.armies.map {
                        if (it.factionId == faction.id) it.copy(morale = (it.morale - 4).coerceAtLeast(0)) else it }))
                    if (politics.lastCrisisDay < state.day - 13) record("Hungersnot", "Die Vorräte reichen nicht. $leaving Zivilisten verlassen das Reich; Wohlstand und Heeresmoral sinken.")
                }
                faction = next.world.faction(faction.id) ?: faction
            }
            if (state.day >= politics.cooldownUntilDay && state.day % 7 == 0) {
                val vassal = next.diplomacy.treaties.firstOrNull { it.kind == TreatyKind.VASSAL && it.payerFactionId == faction.id &&
                    (relation(next, it.firstFactionId, it.secondFactionId).trust < 20 || fatigue > 75 || politics.stability < 30) }
                val rebelCommander = commanders.minByOrNull { it.rulerLoyalty }
                when {
                    vassal != null -> {
                        val overlord = if (vassal.firstFactionId == faction.id) vassal.secondFactionId else vassal.firstFactionId
                        next = startWar(next, faction.id, overlord)
                        politics = politics.copy(cooldownUntilDay = state.day + 60)
                        record("Vasallenaufstand", "${faction.ruler} verweigert weiteren Tribut an ${next.world.faction(overlord)?.name}; vorhandene Truppen und Vorräte tragen den Krieg.")
                    }
                    politics.stability <= 40 && rebelCommander != null && rebelCommander.rulerLoyalty <= 20 && faction.gold >= 100 -> {
                        val oldRuler = faction.ruler
                        faction = faction.copy(ruler = rebelCommander.name, rulerAge = 35,
                            successionCount = safe(faction.successionCount.toLong() + 1), gold = faction.gold - 100,
                            aggression = (faction.aggression + if (rebelCommander.personality in listOf(FactionPersonality.AGGRESSIVE, FactionPersonality.EXPANSIONIST)) 10 else -5).coerceIn(0, 100))
                        next = next.copy(world = next.world.copy(factions = next.world.factions.map { if (it.id == faction.id) faction else it },
                            enemyCommanders = next.world.enemyCommanders.map { if (it.factionId == faction.id)
                                it.copy(rulerLoyalty = if (it.id == rebelCommander.id) 90 else 50) else it }, armies = next.world.armies.map {
                                if (it.factionId == faction.id) it.copy(morale = (it.morale - 8).coerceAtLeast(0)) else it }))
                        politics = politics.copy(stability = 50, cooldownUntilDay = state.day + 60, disputedSuccessionUntilDay = state.day + 30)
                        record("Putsch am Hof", "${rebelCommander.name} entmachtet $oldRuler. Die Machtübernahme kostet 100 Gold; die vorhandenen Heere verlieren Moral.")
                    }
                    politics.stability < 30 && (hungerDays >= 21 || loyalty < 35 || politics.disputedSuccessionUntilDay > state.day) -> {
                        val holdings = next.world.places.filter { it.ownerId == faction.id }
                        val province = holdings.filter { it.id != faction.capitalId }.minByOrNull { it.prosperity }
                            ?: holdings.firstOrNull { politics.stability < 15 }
                        if (province != null) {
                            val commanderId = rebelCommander?.takeIf { it.rulerLoyalty < 35 }?.id
                            val rebelArmyIds = next.world.armies.filter { it.factionId == faction.id &&
                                (it.regionId == province.id || commanderId != null && it.enemyCommanderId == commanderId) &&
                                it.status != WorldArmyStatus.ENGAGED }.map { it.id }.toSet()
                            val movedCommanders = next.world.armies.filter { it.id in rebelArmyIds }.mapNotNull { it.enemyCommanderId }.toSet()
                            val movingTroops = next.world.armies.filter { it.id in rebelArmyIds }.sumOf { it.total.toLong() }
                            val stillReserved = next.world.armies.filter { it.factionId == faction.id && it.id !in rebelArmyIds }.sumOf { it.total.toLong() } +
                                next.world.recruitments.filter { it.factionId == faction.id && it.armyId !in rebelArmyIds }.sumOf { it.amount.toLong() }
                            val movingPeople = minOf((faction.population.toLong() - stillReserved).coerceAtLeast(0), movingTroops + province.population,
                                Int.MAX_VALUE.toLong() - (next.world.faction(NEUTRAL_FACTION)?.population ?: 0)).toInt()
                            if (movingPeople >= movingTroops) {
                            next = next.copy(world = next.world.copy(places = next.world.places.map {
                                if (it.id == province.id) it.copy(ownerId = NEUTRAL_FACTION) else it },
                                factions = next.world.factions.map { when (it.id) {
                                    faction.id -> it.copy(population = it.population - movingPeople)
                                    NEUTRAL_FACTION -> it.copy(population = it.population + movingPeople)
                                    else -> it
                                } }, recruitments = next.world.recruitments.filterNot { it.armyId in rebelArmyIds },
                                armies = next.world.armies.map { if (it.id in rebelArmyIds) it.copy(factionId = NEUTRAL_FACTION,
                                    route = emptyList(), routeIndex = 0, destinationId = null, arrivalDay = null, legProgress = 0,
                                    status = if (it.total > 0) WorldArmyStatus.HOLDING else WorldArmyStatus.DESTROYED) else it },
                                enemyCommanders = next.world.enemyCommanders.map { if (it.id in movedCommanders)
                                    it.copy(factionId = NEUTRAL_FACTION, rulerLoyalty = 60) else it }))
                            next = changeRelation(next, faction.id, NEUTRAL_FACTION, -20, -10, "${province.name} erklärt Autonomie.", true)
                            politics = politics.copy(stability = (politics.stability + 12).coerceAtMost(100), cooldownUntilDay = state.day + 60)
                            record("Gebietsaufstand", "${province.name} löst sich und schließt sich den Freien Grenzmarken an. ${rebelArmyIds.size} vorhandene Banner wechseln die Seite; keine neuen Truppen entstehen.")
                            }
                        }
                    }
                    else -> {
                        val broken = next.diplomacy.treaties.firstOrNull { it.kind in listOf(TreatyKind.DEFENSIVE_ALLIANCE,
                            TreatyKind.OFFENSIVE_ALLIANCE, TreatyKind.DYNASTIC_ALLIANCE) &&
                            (it.firstFactionId == faction.id || it.secondFactionId == faction.id) &&
                            relation(next, it.firstFactionId, it.secondFactionId).trust < 15 && faction.personality != FactionPersonality.HONORABLE }
                        if (broken != null) {
                            val former = if (broken.firstFactionId == faction.id) broken.secondFactionId else broken.firstFactionId
                            next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties.filterNot { it.id == broken.id }))
                            next = changeRelation(next, faction.id, former, -10, -12, "Bündnis nach anhaltendem Misstrauen aufgekündigt.")
                            politics = politics.copy(cooldownUntilDay = state.day + 30)
                            record("Bündnisbruch", "${faction.ruler} beendet das Bündnis mit ${next.world.faction(former)?.name}; Schutzpflicht und Aufklärung dieses Bündnisses enden.")
                            val known = next.world.knowledgeFor(faction.id).exploredRegions.mapNotNull { next.world.place(it)?.ownerId }.toSet()
                            val alternative = next.world.factions.filter { it.id !in listOf(PLAYER_FACTION, NEUTRAL_FACTION, faction.id, former) &&
                                it.id in known && !atWar(next, faction.id, it.id) && relation(next, faction.id, it.id).trust >= 35 &&
                                relation(next, faction.id, it.id).relation >= 10 }.maxByOrNull { relation(next, faction.id, it.id).relation }
                            if (alternative != null && faction.gold >= 150 && !hasTreaty(next, faction.id, alternative.id, TreatyKind.DEFENSIVE_ALLIANCE)) {
                                next = transferGold(next, faction.id, alternative.id, 150)
                                next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties +
                                    Treaty("ai_alliance_${faction.id}_${state.day}", TreatyKind.DEFENSIVE_ALLIANCE, faction.id, alternative.id, state.day, state.day + 60)))
                                next = changeRelation(next, faction.id, alternative.id, 5, 5, "Neues Verteidigungsbündnis nach Gesandtschaft für 150 Gold.")
                                record("Bündniswechsel", "${alternative.name} wird für 60 Tage neuer Schutzpartner; die Gesandtschaft überträgt 150 vorhandenes Gold.")
                            }
                        }
                    }
                }
            }
            next = next.copy(diplomacy = next.diplomacy.copy(politics = next.diplomacy.politics.map {
                if (it.factionId == politics.factionId) politics else it }))
        }
        return next
    }

    private fun politicalAi(state: GameState): GameState {
        var next = state.copy(diplomacy = state.diplomacy.copy(lastAiDecisionDay = state.day))
        val factions = next.world.factions.filter { it.id != PLAYER_FACTION && it.id != NEUTRAL_FACTION }
        factions.forEach { faction ->
            val knowledge = next.world.knowledgeFor(faction.id)
            val neighbors = next.world.roads.flatMap { road ->
                val a = next.world.place(road.from); val b = next.world.place(road.to)
                if (a == null || b == null) emptyList() else when (faction.id) {
                    a.ownerId -> listOf(b.ownerId); b.ownerId -> listOf(a.ownerId); else -> emptyList()
                }
            }.distinct().filter { it != faction.id && it != NEUTRAL_FACTION && next.world.faction(it) != null }
            val targetId = neighbors.sorted().firstOrNull { id -> relation(next, faction.id, id).atWar }
                ?: neighbors.sorted().minByOrNull { relation(next, faction.id, it).relation } ?: return@forEach
            val r = relation(next, faction.id, targetId)
            val warDays = state.day - (r.warStartedDay ?: state.day)
            val decision = when {
                r.atWar && (faction.food < faction.population || warDays >= 45) -> "Frieden zur Erholung"
                !r.atWar && r.relation < -20 && faction.aggression >= 55 && faction.gold >= 350 &&
                    faction.food > faction.population && next.diplomacy.treaties.none {
                        it.connects(faction.id, targetId) && it.expiresDay > state.day } &&
                    knowledge.exploredRegions.any { next.world.place(it)?.ownerId == targetId } -> "Krieg um die Grenze"
                !r.atWar && r.relation >= 0 && faction.personality in listOf(FactionPersonality.MERCANTILE,
                    FactionPersonality.DIPLOMATIC, FactionPersonality.HONORABLE) -> "Handel und Annäherung"
                else -> "Grenzen und Verträge sichern"
            }
            if (decision == "Frieden zur Erholung") {
                if (targetId == PLAYER_FACTION) {
                    if (next.diplomacy.proposals.none { it.targetFactionId == faction.id && it.kind == TreatyKind.PEACE &&
                        it.status == ProposalStatus.COUNTER_OFFER && it.expiresDay > state.day }) {
                        val peace = DiplomaticProposal("proposal_${next.diplomacy.nextId}", faction.id, TreatyKind.PEACE,
                            durationDays = 30, createdDay = state.day, expiresDay = state.day + 7,
                            explanation = "${faction.ruler} bietet 30 Tage Frieden zur Erholung an. Der Krieg endet bei deiner Zustimmung.")
                        next = next.copy(diplomacy = next.diplomacy.copy(proposals = (next.diplomacy.proposals + peace).takeLast(24),
                            nextId = next.diplomacy.nextId + 1))
                    }
                } else {
                    next = changeRelation(next, faction.id, targetId, 8, 3, decision, false)
                    next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties +
                        Treaty("ai_peace_${faction.id}_${state.day}", TreatyKind.PEACE, faction.id, targetId, state.day, state.day + 30)))
                }
            } else if (decision == "Krieg um die Grenze") next = startWar(next, faction.id, targetId)
            else if (decision == "Handel und Annäherung" && targetId != PLAYER_FACTION &&
                !hasTreaty(next, faction.id, targetId, TreatyKind.TRADE)) {
                next = changeRelation(next, faction.id, targetId, 3, 2, decision)
                next = next.copy(diplomacy = next.diplomacy.copy(treaties = next.diplomacy.treaties +
                    Treaty("ai_trade_${faction.id}_${state.day}", TreatyKind.TRADE, faction.id, targetId, state.day, state.day + 60)))
            }
            next = next.copy(world = next.world.copy(factions = next.world.factions.map {
                if (it.id == faction.id) it.copy(lastDecision = decision, lastDecisionDay = state.day) else it
            }))
        }
        return next
    }

    private fun shareIntelligence(state: GameState): GameState {
        val allies = defensiveAllies(state, PLAYER_FACTION) + state.diplomacy.treaties.filter {
            it.kind == TreatyKind.MILITARY_ACCESS && it.expiresDay > state.day &&
                (it.firstFactionId == PLAYER_FACTION || it.secondFactionId == PLAYER_FACTION)
        }.map { if (it.firstFactionId == PLAYER_FACTION) it.secondFactionId else it.firstFactionId }
        if (allies.isEmpty()) return state
        val own = state.world.knowledgeFor(PLAYER_FACTION)
        val reports = allies.flatMap { state.world.knowledgeFor(it).observations }
        val explored = (own.exploredRegions + allies.flatMap { state.world.knowledgeFor(it).exploredRegions }).distinct()
        val updated = own.copy(exploredRegions = explored, observations =
            (own.observations + reports).groupBy { it.armyId }.map { (_, entries) -> entries.maxBy { it.day } })
        return state.copy(world = state.world.copy(knowledge = state.world.knowledge.filterNot {
            it.factionId == PLAYER_FACTION } + updated))
    }

    /** Reject malformed domain data during save validation instead of silently resetting it. */
    fun validate(state: GameState) {
        val factionIds = state.world.factions.map { it.id }.toSet()
        val diplomacy = state.diplomacy
        require(diplomacy.nextId > 0 && diplomacy.lastTickDay in 0..state.day && diplomacy.lastAiDecisionDay in 0..state.day)
        require(diplomacy.relations.map { listOf(it.firstFactionId, it.secondFactionId).sorted() }.distinct().size == diplomacy.relations.size)
        diplomacy.relations.forEach {
            require(it.firstFactionId in factionIds && it.secondFactionId in factionIds && it.firstFactionId != it.secondFactionId)
            require(it.relation in -100..100 && it.trust in 0..100 && it.fear in 0..100 && it.respect in 0..100)
            require(it.warStartedDay == null || it.warStartedDay in 1..state.day)
            require(it.history.size <= 40 && it.history.all { memory -> memory.day in 1..state.day })
            require(it.warGoalRegionId == null || state.world.place(it.warGoalRegionId) != null)
            require(it.warGoalAllyId == null || it.warGoalAllyId in factionIds)
            require(it.warLosses.keys.all { id -> id in listOf(it.firstFactionId, it.secondFactionId) } && it.warLosses.values.all { losses -> losses >= 0 })
            require(it.playerWarGoal == null || PLAYER_FACTION in listOf(it.firstFactionId, it.secondFactionId))
        }
        require(diplomacy.treaties.map { it.id }.distinct().size == diplomacy.treaties.size)
        diplomacy.treaties.forEach {
            require(it.id.isNotBlank() && it.firstFactionId in factionIds && it.secondFactionId in factionIds && it.firstFactionId != it.secondFactionId)
            require(it.signedDay in 1..state.day && it.expiresDay > it.signedDay && it.tributeGold >= 0 && it.lastPaymentDay in 0..state.day)
            require(it.payerFactionId == null || it.payerFactionId in listOf(it.firstFactionId, it.secondFactionId))
        }
        require(diplomacy.proposals.map { it.id }.distinct().size == diplomacy.proposals.size)
        diplomacy.proposals.forEach {
            require(it.targetFactionId in factionIds && it.targetFactionId != PLAYER_FACTION && it.createdDay in 1..state.day)
            require(it.expiresDay > it.createdDay && it.durationDays in 7..365 && it.tributeGold in 0..10000)
            require(ResourceKind.entries.all { kind -> kind.value(it.offered) >= 0 && kind.value(it.requested) >= 0 })
        }
        require(diplomacy.politics.map { it.factionId }.distinct().size == diplomacy.politics.size)
        diplomacy.politics.forEach {
            require(it.factionId in factionIds && it.factionId !in listOf(PLAYER_FACTION, NEUTRAL_FACTION))
            require(it.stability in 0..100 && it.warExhaustion in 0..100 && it.hungerDays >= 0)
            require(it.lastAgedDay in 1..state.day && it.lastCrisisDay in 0..state.day &&
                it.cooldownUntilDay >= 0 && it.disputedSuccessionUntilDay >= 0)
            require(it.history.size <= 30 && it.history.all { memory -> memory.day in 1..state.day })
            val ruler = state.world.faction(it.factionId)!!
            require(ruler.ruler.isNotBlank() && ruler.rulerAge in 18..150 && ruler.successionCount >= 0)
        }
        val espionage = state.espionage
        require(espionage.nextId > 0 && espionage.lastTickDay in 0..state.day && espionage.counterintelligenceUntilDay >= 0)
        require(espionage.agents.size <= 8 && espionage.agents.map { it.id }.distinct().size == espionage.agents.size)
        espionage.agents.forEach {
            require(it.id.isNotBlank() && it.name.isNotBlank() && it.skill in 0..100 && it.experience >= 0)
            require(it.capturedByFactionId == null || it.capturedByFactionId in factionIds)
        }
        require(espionage.missions.map { it.id }.distinct().size == espionage.missions.size)
        require(espionage.missions.map { it.agentId }.distinct().size == espionage.missions.size)
        espionage.missions.forEach {
            require(espionage.agents.any { agent -> agent.id == it.agentId && agent.capturedByFactionId == null })
            require(it.targetFactionId in factionIds && it.startedDay in 1..state.day && it.completesDay > it.startedDay && it.difficulty in 0..100)
            require(it.targetProvinceId == null || state.world.place(it.targetProvinceId) != null)
        }
        require(espionage.reports.size <= 40 && espionage.reports.all { it.day in 1..state.day && it.targetFactionId in factionIds })
        val society = state.society
        require(listOf(society.crime, society.inequality, society.disease, society.hunger, society.culturalTension,
            society.politicalLoyalty, society.warExhaustion).all { it in 0..100 })
        require(society.totalImmigrants >= 0 && society.totalEmigrants >= 0 && society.lastTickDay in 0..state.day)
        require(society.groups.map { it.kind }.distinct().size == society.groups.size)
        require(society.groups.all { it.loyalty in 0..100 && it.influence in 0..100 && it.lastDemandDay in 0..state.day })
        require(society.demands.map { it.id }.distinct().size == society.demands.size)
        require(society.demands.all { it.createdDay in 1..state.day && it.deadlineDay > it.createdDay &&
            society.groups.any { group -> group.kind == it.group } })
        require(society.policyUntilDay.values.all { it >= 0 })
        val story = society.story
        require(story.nextId > 0 && story.lastTickDay in 0..state.day && story.cooldownUntilDay >= 0)
        require(story.familyCooldowns.values.all { it >= 0 })
        require(story.resolved.size <= 60 && story.resolved.distinct().size == story.resolved.size)
        require(story.scheduled.all { it.dueDay >= 1 && it.chainId.isNotBlank() })
        story.pending?.let { require(it.id.isNotBlank() && it.createdDay in 1..state.day && it.expiresDay > it.createdDay && it.id !in story.resolved) }
    }
}
