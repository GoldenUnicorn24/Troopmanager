package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** Persistent people and court simulation. All choices and daily effects are offline and deterministic. */
object CharacterEngine {
    fun initialize(state: GameState): GameState {
        val existing = state.court.characters.associateBy { it.commanderId }
        val characters = state.commanders.mapIndexed { index, c ->
            existing[c.id]?.let { if (c.id == COMPANION_COMMANDER_ID) it.copy(age = state.companion.age, diplomacy = state.companion.diplomacy) else it } ?: CharacterDetails(
                commanderId = c.id,
                age = if (c.id == COMPANION_COMMANDER_ID) state.companion.age else 27 + index % 18,
                origin = c.culture.label,
                family = listOf("Haus ${c.name.substringBefore(' ')}"),
                personality = when (c.trait) {
                    "Mutig" -> CharacterPersonality.AMBITIOUS
                    "Bedacht" -> CharacterPersonality.CAUTIOUS
                    "Diszipliniert" -> CharacterPersonality.DISCIPLINED
                    else -> if (c.id == COMPANION_COMMANDER_ID) CharacterPersonality.COMPASSIONATE else CharacterPersonality.HONORABLE
                },
                ambition = if (c.trait == "Mutig") 75 else 45,
                courage = c.sword,
                diplomacy = if (c.id == COMPANION_COMMANDER_ID) state.companion.diplomacy else (c.bow + c.loyalty) / 3,
                stewardship = (c.tactics + c.loyalty) / 3,
                intrigue = (c.tactics + c.bow) / 3,
                medicine = if (c.id == COMPANION_COMMANDER_ID) 55 else 25 + index % 4 * 10,
                rank = when (c.rank) {
                    "General" -> CommanderRank.GENERAL
                    "Marschall" -> CommanderRank.MARSHAL
                    "Oberbefehlshaber" -> CommanderRank.SUPREME
                    else -> CommanderRank.CAPTAIN
                },
                serviceHistory = listOf(CareerEntry(state.day, "joined", "${c.name} tritt den Dienst an.")),
                playerOpinion = c.loyalty,
                lastAgedDay = state.day,
            )
        }
        val retained = state.court.characters.filter { detail -> state.commanders.none { it.id == detail.commanderId } && !detail.alive }
        return state.copy(court = state.court.copy(characters = characters + retained))
    }

    fun hasPerk(state: GameState, perk: PlayerPerk): Boolean = perk in state.court.perks

    fun commanderMorale(state: GameState, commanderId: Long): Int {
        val c = state.commanders.firstOrNull { it.id == commanderId } ?: return 0
        val d = state.court.characters.firstOrNull { it.commanderId == commanderId } ?: return 0
        val fellowship = d.friends.count { id -> state.commanders.any { it.id == id } && !state.commanderAway(id) && !state.war.unavailableCommander(id) }.coerceAtMost(2)
        val rivalry = d.rivals.count { id -> state.commanders.any { it.id == id } && !state.commanderAway(id) }.coerceAtMost(2)
        return ((c.loyalty - 50) / 15 + (d.courage - 50) / 15 + d.rank.ordinal + fellowship - rivalry).coerceIn(-6, 9)
    }

    fun bonuses(state: GameState): CourtBonuses {
        fun stat(office: CourtOffice, measure: (Commander, CharacterDetails) -> Int): Int {
            val id = state.court.offices[office] ?: return 0
            val c = state.commanders.firstOrNull { it.id == id } ?: return 0
            val d = state.court.characters.firstOrNull { it.commanderId == id && it.alive } ?: return 0
            if (c.loyalty < 30 || state.commanderAway(id) || state.war.unavailableCommander(id)) return 0
            return measure(c, d).coerceIn(0, 100)
        }
        val marshal = stat(CourtOffice.MARSHAL) { c, _ -> c.leadership }
        val treasurer = stat(CourtOffice.TREASURER) { _, d -> d.stewardship }
        val spy = stat(CourtOffice.SPYMASTER) { _, d -> d.intrigue }
        val ambassador = stat(CourtOffice.AMBASSADOR) { _, d -> d.diplomacy }
        val builder = stat(CourtOffice.MASTER_BUILDER) { c, _ -> c.siege }
        val physician = stat(CourtOffice.PHYSICIAN) { _, d -> d.medicine }
        val steward = stat(CourtOffice.STEWARD) { _, d -> d.stewardship }
        val legend = state.court.legends.filter { entry ->
            entry.commanderId?.let { id -> state.court.characters.any { it.commanderId == id && it.alive } } == true
        }.sumOf { it.moraleBonus }.coerceAtMost(6)
        return CourtBonuses(
            morale = marshal / 15 + legend + (if (hasPerk(state, PlayerPerk.LEADERSHIP_INSPIRING)) 3 else 0) +
                (if (hasPerk(state, PlayerPerk.WARFARE_RALLY)) 2 else 0),
            gold = treasurer * 2 + if (hasPerk(state, PlayerPerk.ECONOMY_STEWARDSHIP)) 40 else 0,
            diplomacy = ambassador / 3 + if (hasPerk(state, PlayerPerk.DIPLOMACY_ENVOY)) 10 else 0,
            counterintelligence = spy / 2 + if (hasPerk(state, PlayerPerk.INTRIGUE_COUNTERINTELLIGENCE)) 15 else 0,
            construction = builder / 25,
            healing = physician / 15,
            security = steward / 25 + if (hasPerk(state, PlayerPerk.INTRIGUE_WATCH)) 1 else 0,
        )
    }

    fun assignOffice(state: GameState, office: CourtOffice, commanderId: Long?): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Ämter nach der Schlacht besetzen.")
        var next = initialize(state)
        if (commanderId == null) return GameEngine.ActionResult(next.copy(court = next.court.copy(offices = next.court.offices - office)), "Amt freigegeben.")
        val commander = next.commanders.firstOrNull { it.id == commanderId }
            ?: return GameEngine.ActionResult(state, "Charakter nicht gefunden.")
        val details = next.court.characters.first { it.commanderId == commanderId }
        if (!details.alive || commander.loyalty < 30 || details.playerOpinion < 20 || next.war.unavailableCommander(commanderId) || next.commanderAway(commanderId))
            return GameEngine.ActionResult(state, "Ein verfügbarer, loyaler Amtsinhaber wird benötigt.")
        if (details.age < 18) return GameEngine.ActionResult(state, "Minderjährige übernehmen kein Hofamt.")
        val offices = next.court.offices.filterValues { it != commanderId } + (office to commanderId)
        next = next.copy(court = next.court.copy(offices = offices))
        return GameEngine.ActionResult(chronicle(next, "Hofamt besetzt", "${commander.name} übernimmt das Amt: ${office.label}."), "${office.label} ist besetzt; die Amtswerte wirken ab sofort.")
    }

    fun unlockPerk(state: GameState, perk: PlayerPerk): GameEngine.ActionResult {
        if (state.battleSession?.isActive == true) return GameEngine.ActionResult(state, "Fertigkeiten nach der Schlacht verteilen.")
        if (hasPerk(state, perk)) return GameEngine.ActionResult(state, "Fertigkeit bereits gelernt.")
        if (state.player.skillPoints < perk.cost) return GameEngine.ActionResult(state, "${perk.cost} Fertigkeitspunkte benötigt.")
        // v0.61: starting skill points are a true character-build budget and can be spent
        // immediately instead of being blocked behind character-level gates.
        val next = state.copy(player = state.player.copy(skillPoints = state.player.skillPoints - perk.cost), court = state.court.copy(perks = state.court.perks + perk))
        return GameEngine.ActionResult(chronicle(next, "Neuer Entwicklungsweg", "${perk.label}: ${perk.description}"), "${perk.label} gelernt.")
    }

    fun promote(state: GameState, commanderId: Long): GameEngine.ActionResult {
        val next = initialize(state)
        val c = next.commanders.firstOrNull { it.id == commanderId } ?: return GameEngine.ActionResult(state, "Kommandant nicht gefunden.")
        val d = next.court.characters.first { it.commanderId == commanderId }
        if (state.battleSession?.isActive == true || state.commanderAway(commanderId) || state.war.unavailableCommander(commanderId) || !d.alive || d.age < 18)
            return GameEngine.ActionResult(state, "Beförderung benötigt eine verfügbare Führungskraft.")
        val target = CommanderRank.entries.getOrNull(d.rank.ordinal + 1) ?: return GameEngine.ActionResult(state, "Höchster Rang erreicht.")
        if (c.victories < target.victoriesRequired) return GameEngine.ActionResult(state, "${target.victoriesRequired} Siege für ${target.label} benötigt.")
        val cost = 350 * target.ordinal
        if (state.resources.gold < cost) return GameEngine.ActionResult(state, "Beförderung benötigt $cost Gold.")
        val disappointed = next.court.characters.filter { it.commanderId != commanderId && it.ambition >= 65 && it.rank == d.rank &&
            (next.commanders.firstOrNull { c -> c.id == it.commanderId }?.victories ?: 0) >= target.victoriesRequired }
        val ids = disappointed.map { it.commanderId }.toSet()
        val details = next.court.characters.map { person -> when {
            person.commanderId == commanderId -> person.copy(rank = target, lastPromotionDay = state.day, playerOpinion = (person.playerOpinion + 10).coerceAtMost(100), serviceHistory = (person.serviceHistory + CareerEntry(state.day, "promotion", target.label)).takeLast(80))
            person.commanderId in ids -> person.copy(playerOpinion = (person.playerOpinion - 5).coerceAtLeast(0), rivals = person.rivals + commanderId)
            else -> person
        } }
        val commanders = next.commanders.map { person -> when (person.id) {
            commanderId -> person.copy(rank = target.label, leadership = (person.leadership + 3).coerceAtMost(100), loyalty = (person.loyalty + 8).coerceAtMost(100))
            in ids -> person.copy(loyalty = (person.loyalty - 5).coerceAtLeast(0))
            else -> person
        } }
        val companion = if (commanderId == COMPANION_COMMANDER_ID) next.companion.copy(leadership = (next.companion.leadership + 3).coerceAtMost(100)) else next.companion
        val relationship = if (commanderId == COMPANION_COMMANDER_ID) next.relationship.copy(loyalty = (next.relationship.loyalty + 8).coerceAtMost(100)) else next.relationship
        return GameEngine.ActionResult(chronicle(next.copy(resources = next.resources.copy(gold = next.resources.gold - cost), commanders = commanders, companion = companion, relationship = relationship, court = next.court.copy(characters = details)), "Beförderung", "${c.name} wird ${target.label}. Führung und Treue steigen."), "${c.name} befördert.")
    }

    fun recordBattle(state: GameState, commanderIds: List<Long>, won: Boolean, casualties: Int, outnumbered: Boolean = false): GameState {
        var next = initialize(state)
        val ids = commanderIds.toSet()
        val characters = next.court.characters.map { d ->
            if (d.commanderId !in ids) d else d.copy(experience = (d.experience.toLong() + if (won) 120 else 50).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                serviceHistory = (d.serviceHistory + CareerEntry(next.day, if (won) "victory" else "defeat", "${if (won) "Sieg" else "Niederlage"}${if (outnumbered) " in Unterzahl" else ""}; $casualties Verluste des Heeres.")).takeLast(80))
        }
        next = next.copy(court = next.court.copy(characters = characters))
        if (COMPANION_COMMANDER_ID in ids) next = RelationshipEngine.remember(next, if (won) "shared_victory" else "shared_defeat", "Ein gemeinsam ${if (won) "gewonnener" else "verlorener"} Kampf.", if (won) 3 else -3)
        return synchronizeDeaths(recognizeLegends(next, if (outnumbered && won) ids else emptySet()))
    }

    fun recordMission(state: GameState, commanderId: Long, success: Boolean, mission: String): GameState {
        var next = initialize(state)
        val entry = CareerEntry(next.day, "mission", "$mission: ${if (success) "Erfolg" else "Rückschlag"}.")
        next = next.copy(court = next.court.copy(characters = next.court.characters.map { d ->
            if (d.commanderId != commanderId) d else d.copy(experience = (d.experience.toLong() + if (success) 70 else 25).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                serviceHistory = (d.serviceHistory + entry).takeLast(80))
        }))
        return if (commanderId == COMPANION_COMMANDER_ID) RelationshipEngine.remember(next, "mission_return", entry.text, if (success) 2 else -1) else next
    }

    private fun recognizeLegends(state: GameState, heroic: Set<Long> = emptySet()): GameState {
        var next = state
        next.commanders.forEach { c ->
            val d = next.court.characters.firstOrNull { it.commanderId == c.id } ?: return@forEach
            if (d.renowned || (c.victories < 10 && c.id !in heroic)) return@forEach
            val epithet = if (c.id in heroic) "der Standhafte" else "die Grenzlegende"
            val legend = LegendEntry("legend_commander_${c.id}", c.id, "${c.name}, $epithet", next.day, if (c.id in heroic) "Sieg gegen eine Übermacht" else "Zehn siegreiche Schlachten")
            next = chronicle(next.copy(court = next.court.copy(characters = next.court.characters.map { if (it.commanderId == c.id) it.copy(renowned = true, epithet = epithet) else it }, legends = (next.court.legends + legend).takeLast(100))), "Eine Legende entsteht", "${legend.name}: ${legend.deed}.")
        }
        return next
    }

    fun tick(state: GameState): GameState {
        if (state.court.lastTickDay >= state.day) return state
        var next = synchronizeDeaths(recognizeLegends(initialize(state)))
        val effects = bonuses(next)
        val spy = next.court.offices[CourtOffice.SPYMASTER]?.let { id -> next.court.characters.firstOrNull { it.commanderId == id } }
        val mediator = hasPerk(next, PlayerPerk.DIPLOMACY_MEDIATOR)
        next = next.copy(
            armyPools = next.armyPools.map { it.copy(morale = (it.morale + (effects.morale / 3)).coerceAtMost(100)) },
            realm = next.realm.copy(scoutingDays = if (hasPerk(next, PlayerPerk.INTRIGUE_WATCH) || (spy != null && effects.counterintelligence > 0)) maxOf(2, next.realm.scoutingDays) else next.realm.scoutingDays,
                tradeBonusDays = if (effects.diplomacy > 0 && next.day % 7 == 0) maxOf(3, next.realm.tradeBonusDays) else next.realm.tradeBonusDays),
            city = next.city.copy(security = (next.city.security + effects.security).coerceAtMost(100), satisfaction = (next.city.satisfaction + effects.security / 2).coerceAtMost(100),
                constructionQueue = next.city.constructionQueue.map { if (effects.construction > 0 && next.day % (5 - effects.construction.coerceAtMost(3)) == 0) it.copy(daysRemaining = (it.daysRemaining - 1).coerceAtLeast(1)) else it }),
            relationship = next.relationship.copy(conflict = (next.relationship.conflict - (if (mediator) 2 else 0) - effects.healing / 3).coerceAtLeast(0)),
            court = next.court.copy(lastTickDay = next.day),
        )
        if (next.day % 30 == 0) {
            val disgruntled = next.court.characters.filter { d -> d.alive && d.ambition >= 65 && next.day - maxOf(d.lastPromotionDay, d.lastPassedOverDay) >= 30 &&
                CommanderRank.entries.getOrNull(d.rank.ordinal + 1)?.let { target -> (next.commanders.firstOrNull { it.id == d.commanderId }?.victories ?: 0) >= target.victoriesRequired } == true }.map { it.commanderId }.toSet()
            if (disgruntled.isNotEmpty()) next = chronicle(next.copy(commanders = next.commanders.map { if (it.id in disgruntled) it.copy(loyalty = (it.loyalty - 2).coerceAtLeast(0)) else it }, court = next.court.copy(characters = next.court.characters.map { if (it.commanderId in disgruntled) it.copy(lastPassedOverDay = next.day, playerOpinion = (it.playerOpinion - 2).coerceAtLeast(0)) else it })), "Übergangene Führungskräfte", "Verdiente ehrgeizige Kommandanten erwarten eine Beförderung.")
        }
        if (next.day % 14 == 0 && next.day > next.court.lastSocialDay) next = socialTick(next)
        return next
    }

    private fun synchronizeDeaths(state: GameState): GameState {
        val deceased = state.war.commanderConditions.filter { it.status == CombatantStatus.DEAD }.map { it.commanderId }.toSet()
        val companionDied = COMPANION_COMMANDER_ID in deceased && state.court.characters.any { it.commanderId == COMPANION_COMMANDER_ID && it.alive }
        var next = state.copy(court = state.court.copy(characters = state.court.characters.map { d ->
            if (d.commanderId !in deceased || !d.alive) d else d.copy(alive = false, serviceHistory = (d.serviceHistory + CareerEntry(state.day, "death", "Im Dienst gefallen.")).takeLast(80))
        }, offices = state.court.offices.filterValues { it !in deceased }))
        if (companionDied) {
            next = RelationshipEngine.remember(next, "bereavement", "${state.companion.name} kehrt aus der Schlacht nicht zurück. Der Hof trauert.", -10)
            next = chronicle(next.copy(relationship = next.relationship.copy(romanceStage = RomanceStage.NONE, commitment = 0,
                pendingEvent = null, intimacyConsentDay = null, consent = next.relationship.consent.copy(romanceAllowed = false, intimacyAllowed = false, wantsChildren = false)),
                dynasty = next.dynasty.copy(plannedBirthDay = null, planningParents = emptyList(), members = next.dynasty.members.map { if (it.id == "companion") it.copy(alive = false, deathDay = state.day) else it })), "Gefallen im Dienst", "${state.companion.name} bleibt in den Erinnerungen des Reiches.")
        }
        return next
    }

    private fun socialTick(state: GameState): GameState {
        val people = state.court.characters.filter { it.alive && !state.commanderAway(it.commanderId) && !state.war.unavailableCommander(it.commanderId) }
        if (people.size < 2) return state.copy(court = state.court.copy(lastSocialDay = state.day))
        val index = (state.day / 14) % people.size
        val first = people[index]
        val second = people[(index + 1) % people.size]
        val pair = listOf(first.commanderId, second.commanderId).sorted()
        val old = state.court.socialLinks.firstOrNull { it.firstId == pair[0] && it.secondId == pair[1] }
        val rivalry = first.commanderId in second.rivals || second.commanderId in first.rivals
        var kind = if (rivalry) SocialKind.RIVALRY else old?.kind ?: SocialKind.FRIENDSHIP
        val strength = ((old?.strength ?: 15) + if (rivalry) 3 else 8).coerceAtMost(100)
        val unattached = state.court.socialLinks.none { it.kind in listOf(SocialKind.ROMANCE, SocialKind.MARRIAGE) && (it.firstId in pair || it.secondId in pair) }
        val related = first.family.any { it in second.family }
        if (state.settings.romance != RomanceMode.OFF && first.age >= 18 && second.age >= 18 && first.consent.romanceAllowed && second.consent.romanceAllowed && !related &&
            strength >= 75 && kind == SocialKind.FRIENDSHIP && unattached && COMPANION_COMMANDER_ID !in pair && first.personality == second.personality)
            kind = SocialKind.ROMANCE
        if (state.settings.romance != RomanceMode.OFF && strength >= 95 && kind == SocialKind.ROMANCE && first.age >= 18 && second.age >= 18 && first.consent.romanceAllowed && second.consent.romanceAllowed)
            kind = SocialKind.MARRIAGE
        val link = (old ?: NpcSocialLink(pair[0], pair[1], kind, state.day, strength)).copy(
            kind = kind, strength = strength, previousStrength = old?.strength ?: strength,
            lastChangedDay = state.day,
            cause = old?.cause?.takeIf { it.isNotBlank() }
                ?: if (rivalry) "Konkurrierende Karriereziele seit Tag ${state.day}" else "Gemeinsamer Dienst am Hof seit Tag ${state.day}",
        )
        val chars = state.court.characters.map { d -> if (d.commanderId !in pair) d else {
            val other = pair.first { it != d.commanderId }
            if (rivalry) d.copy(rivals = d.rivals + other) else d.copy(friends = d.friends + other)
        } }
        val next = state.copy(court = state.court.copy(characters = chars, socialLinks = (state.court.socialLinks.filterNot { it.firstId == pair[0] && it.secondId == pair[1] } + link).takeLast(200), lastSocialDay = state.day))
        return if (old == null || old.kind != kind) chronicle(next, "Beziehungen am Hof", "${state.commanders.first { it.id == first.commanderId }.name} und ${state.commanders.first { it.id == second.commanderId }.name}: ${kind.label}.") else next
    }

    fun validate(state: GameState) {
        val r = state.relationship
        require(state.player.age in 0..150 && state.companion.age in 0..150) { "Ungültiges Charakteralter." }
        require(state.player.level >= 1 && state.player.skillPoints >= 0 && state.player.experience >= 0) { "Ungültige Charakterentwicklung." }
        require(listOf(r.attraction, r.intimacy, r.loyalty, r.conflict, r.jealousy, r.commitment).all { it in 0..100 }) { "Beziehungswerte sind ungültig." }
        require(r.spentActions in 0..2 && r.actionDay >= 0) { "Beziehungsaktionsbudget ist ungültig." }
        require(r.consent.privacyLevel in 0..100) { "Ungültige Privatsphäre." }
        require(r.memories.map { it.id }.distinct().size == r.memories.size && r.memories.all { it.id.isNotBlank() && it.day >= 0 }) { "Beziehungserinnerungen sind ungültig." }
        if (r.romanceStage != RomanceStage.NONE || r.intimacyConsentDay != null || r.pendingEvent?.adultsOnly == true)
            require(state.player.age >= 18 && state.companion.age >= 18) { "Romanze ist ausschließlich Erwachsenen erlaubt." }
        if (r.intimacyConsentDay != null) require(r.consent.intimacyAllowed && r.romanceStage >= RomanceStage.PARTNERSHIP) { "Intime Zustimmung benötigt eine freiwillige Partnerschaft." }
        if (r.pendingEvent?.key == "intimacy") require(r.pendingEvent.adultsOnly && r.pendingEvent.consentRequired && r.pendingEvent.presentation in setOf("FADE_TO_BLACK", "EXPLICIT") && r.intimacyConsentDay != null && state.player.age >= 18 && state.companion.age >= 18) { "Intime Ereignisse benötigen zwei Erwachsene und ein gemeinsames Ja." }
        require(state.court.characters.map { it.commanderId }.distinct().size == state.court.characters.size) { "Doppelte Hofcharaktere." }
        state.court.characters.forEach { d -> require(d.age in 0..150 && listOf(d.ambition, d.courage, d.diplomacy, d.stewardship, d.intrigue, d.medicine, d.playerOpinion).all { it in 0..100 }) { "Ungültiger Hofcharakter." } }
        require(state.court.offices.values.distinct().size == state.court.offices.size && state.court.offices.values.all { id -> state.commanders.any { it.id == id } }) { "Ungültige Hofämter." }
        require(state.court.legends.map { it.id }.distinct().size == state.court.legends.size) { "Doppelte Legenden." }
        require(state.court.socialLinks.map { it.firstId to it.secondId }.distinct().size == state.court.socialLinks.size) { "Doppelte soziale Beziehungen." }
        state.court.socialLinks.forEach { link ->
            require(link.firstId < link.secondId && link.strength in 0..100 && link.sinceDay >= 0) { "Ungültige soziale Beziehung." }
            if (link.kind in listOf(SocialKind.ROMANCE, SocialKind.MARRIAGE)) require(listOf(link.firstId, link.secondId).all { id -> state.court.characters.any { it.commanderId == id && it.age >= 18 } }) { "NPC-Romanze benötigt erwachsene Charaktere." }
        }
        val dynasty = state.dynasty
        require(dynasty.legitimacy in 0..100 && dynasty.successionCount >= 0) { "Ungültiger Dynastiezustand." }
        require(dynasty.members.map { it.id }.distinct().size == dynasty.members.size) { "Doppelte Familienmitglieder." }
        dynasty.members.forEach { require(it.id.isNotBlank() && it.initialAge in 0..150 && it.bornDay >= 0) { "Ungültiges Familienmitglied." } }
        if (dynasty.members.isNotEmpty()) require(dynasty.members.any { it.id == dynasty.rulerId } && (dynasty.heirId == null || dynasty.members.any { it.id == dynasty.heirId && it.alive })) { "Ungültige Nachfolge." }
        if (dynasty.plannedBirthDay != null) require(dynasty.planningParents.size == 2 && dynasty.planningParents.all { parent -> dynasty.members.any { it.id == parent && it.alive && it.age(state.day) >= 18 } }) { "Familienplanung benötigt erwachsene Eltern." }
    }

    private fun chronicle(state: GameState, title: String, text: String): GameState = state.copy(chronicle = (state.chronicle + ChronicleEntry(state.day, title, text)).takeLast(2000))
}
