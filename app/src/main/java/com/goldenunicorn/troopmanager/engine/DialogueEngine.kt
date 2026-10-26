package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.abs

/**
 * Interprets [DialogueCatalog]. Rules are deterministic for a given day, time and person, so
 * reloading does not reroll an answer, and outcomes follow personality, relationship, skill and
 * sobriety instead of a blind dice roll. Consent topics use an explicit, documented decision.
 */
object DialogueEngine {
    enum class Outcome { SUCCESS, FAILURE, DECLINED, UNAVAILABLE }

    data class Option(val topic: DialogueTopic, val label: String, val enabled: Boolean, val reason: String? = null)

    data class Reply(
        val state: GameState,
        val npcLine: String,
        val outcome: Outcome,
        val playerLine: String = "",
        /** Intimacy is narrated atmospherically and then faded to black; nothing explicit is shown. */
        val fadeToBlack: String? = null,
        val brawl: Boolean = false,
        val summary: String = "",
    )

    data class Consent(val given: Boolean, val reason: String)

    // ------------------------------------------------------------ conversation start

    /** Opens a conversation: counts the meeting, remembers the first one, returns a greeting. */
    fun open(state: GameState, npcId: String): Reply {
        val npc = state.life.npc(npcId) ?: return Reply(state, "", Outcome.UNAVAILABLE)
        val firstEver = npc.relation.timesMet == 0
        val newToday = npc.relation.lastTalkDay != state.day
        var updated = npc.copy(
            metDay = npc.metDay ?: state.day,
            relation = npc.relation.copy(
                timesMet = npc.relation.timesMet + if (newToday) 1 else 0,
                lastTalkDay = state.day,
                lastTalkSlot = state.life.timeSlot,
                topicsThisSlot = if (npc.relation.lastTalkSlot == state.life.timeSlot && !newToday) npc.relation.topicsThisSlot else emptyList(),
            ),
        )
        if (firstEver) updated = NpcEngine.remember(updated, "met", state.day,
            "wir haben uns in ${LifeEngine.locationName(state)} kennengelernt", 1)
        updated = updated.copy(relation = updated.relation.copy(stage = NpcEngine.stageFor(updated.relation)))
        val next = LifeEngine.updateNpc(state, updated)
        return Reply(next, greeting(next, updated, firstEver), Outcome.SUCCESS)
    }

    fun greeting(state: GameState, npc: NpcIdentity, firstEver: Boolean): String {
        val player = state.player.name.substringBefore(' ')
        val r = npc.relation
        if (npc.intoxication >= 50) return "„${player}! Mein besht… bester… Freund!“ ${npc.firstName} hat deutlich zu viel getrunken."
        if (r.hurtByAffairDay != null && state.day - r.hurtByAffairDay <= 20)
            return "${npc.firstName} sieht dich nicht an. „Ich weiß von ${npc.memories.lastOrNull { it.key.startsWith("affair_") }?.key?.removePrefix("affair_") ?: "der anderen"}. Sag jetzt nichts Falsches.“"
        if (firstEver) return firstImpression(state, npc)
        val recall = npc.memories.filter { it.key != "met" }.maxByOrNull { abs(it.weight) * 10 + it.day }
        val warm = npc.personality.warmth >= 45 || r.affinity >= 30
        val opener = when {
            r.stage == SocialStage.MARRIED -> "„Da bist du ja.“ ${npc.firstName} küsst dich zur Begrüßung."
            r.stage.romantic -> "„${player}.“ Ein Lächeln, das nur dir gilt."
            r.affinity <= -20 -> "„Du schon wieder.“"
            warm -> "„${player}! Schön, dich wiederzusehen.“"
            else -> "„${player}.“ Ein knappes Nicken."
        }
        val war = if (LifeEngine.threatened(state, state.life.provinceId)) " „Hast du gehört? Die Lage wird ernst.“" else ""
        val memory = recall?.let { m ->
            when {
                m.weight < 0 -> " „Ich habe nicht vergessen, dass ${m.text}.“"
                state.day - m.day <= 1 -> " „Gestern… ${m.text}. Das war schön.“"
                else -> " „Weißt du noch? ${m.text.replaceFirstChar { it.uppercase() }}.“"
            }
        } ?: ""
        return opener + memory + war
    }

    private fun firstImpression(state: GameState, npc: NpcIdentity): String {
        val look = npc.appearance
        val hair = listOf("schwarzem", "dunkelbraunem", "braunem", "waldgrünem", "kupferrotem", "platinblondem", "grauem", "goldblondem")[look.hairColor.coerceIn(0, 7)]
        val eyes = listOf("braunen", "dunklen", "grünen", "blauen", "bernsteinfarbenen", "grauen")[look.eyeColor.coerceIn(0, 5)]
        val she = npc.gender == Gender.FEMALE
        val who = when (npc.species) {
            Species.ELF -> if (she) "Eine Elbin" else "Ein Elb"
            Species.HALF_ELF -> if (she) "Eine Halbelbin" else "Ein Halbelb"
            Species.HUMAN -> if (she) "Eine Frau" else "Ein Mann"
        }
        val desc = if (look.hairStyle == 7) "$who mit kahlem Kopf und $eyes Augen. ${npc.profession}, dem Aussehen nach."
            else "$who mit $hair Haar und $eyes Augen. ${npc.profession}, dem Aussehen nach."
        val line = when (npc.role) {
            NpcRole.INNKEEPER -> "„Willkommen in „${Towns.byProvince(npc.homeProvinceId)?.tavernName ?: "meiner Schänke"}“! Bier, Eintopf, ein Bett – was darf's sein?“"
            NpcRole.MERCENARY_CAPTAIN -> "„Du hast die Hände eines Kämpfers. Suchst du Arbeit? Ich zahle pünktlich.“"
            NpcRole.BARD -> "„Ein neues Gesicht! Erzähl mir deine Geschichte – vielleicht wird ein Lied daraus.“"
            NpcRole.NOBLE -> "Ein zu feiner Mantel für diese Schänke. „Ich bin nur ein Reisender. Wirklich.“"
            NpcRole.MERCHANT -> "„Wenn du etwas brauchst – ich kenne jemanden, der jemanden kennt.“"
            NpcRole.COMPANION -> "${if (she) "Sie" else "Er"} sitzt allein, das Schwert griffbereit, und mustert dich ohne Scheu. „Setz dich ruhig. Solange du nicht schnarchst.“"
            else -> if (npc.personality.warmth >= 55) "„Na, auch auf der Suche nach einem warmen Platz?“" else "Ein prüfender Blick. „Hm?“"
        }
        return "$desc $line"
    }

    // ------------------------------------------------------------ options

    fun options(state: GameState, npcId: String): List<Option> {
        val npc = state.life.npc(npcId) ?: return emptyList()
        val gifts = state.life.gifts.filter { it.value > 0 }.keys.map(DialogueCatalog::giftTopic)
        return (DialogueCatalog.topics + gifts).mapNotNull { topic ->
            val structural = structuralBlock(state, npc, topic)
            if (structural) return@mapNotNull null
            val reason = softBlock(state, npc, topic)
            Option(topic, fill(topic.label, state, npc), reason == null, reason)
        }
    }

    private fun romanceOpen(state: GameState, npc: NpcIdentity): Boolean =
        npc.romanceable && state.life.gender in npc.attractedTo && !npc.relation.romanceBoundary && npc.age >= NpcEngine.MIN_ADULT_AGE &&
            state.player.age >= NpcEngine.MIN_ADULT_AGE

    /** Topics hidden entirely: wrong stage, role or romance closed. */
    private fun structuralBlock(state: GameState, npc: NpcIdentity, topic: DialogueTopic): Boolean {
        val q = topic.req
        val r = npc.relation
        if (q.romance && !romanceOpen(state, npc)) return true
        if (q.minStage != null && r.stage.ordinal < q.minStage.ordinal && !(r.stage.romantic && !q.minStage.romantic)) return true
        if (q.stages != null && r.stage !in q.stages) return true
        if (q.roles != null && npc.role !in q.roles) return true
        if (q.needsMemory != null && !npc.remembers(q.needsMemory)) return true
        if (q.lacksMemory != null && npc.remembers(q.lacksMemory)) return true
        if (q.needsGrievance && !grievance(npc)) return true
        if (topic.id == "flirt" && r.stage.ordinal >= SocialStage.PARTNER.ordinal) return true
        if (topic.id == "talk_interest" && npc.interests.isEmpty()) return true
        if (topic.id == "invite" && (!PartyEngine.canAsk(state, npc) || state.life.landed)) return true
        if (topic.id == "dismiss" && !state.life.inParty(npc.id)) return true
        if (topic.id == "hire" && state.life.landed) return true
        if (topic.special == "gift" && npc.role == NpcRole.INNKEEPER && npc.relation.timesMet < 2) return true
        return false
    }

    private fun grievance(npc: NpcIdentity): Boolean =
        npc.relation.rivalry >= 15 || npc.relation.affinity <= -10 || npc.relation.hurtByAffairDay != null

    /** Visible but disabled, with an honest reason. */
    private fun softBlock(state: GameState, npc: NpcIdentity, topic: DialogueTopic): String? {
        val q = topic.req
        val r = npc.relation
        val life = state.life
        if (topic.oncePerSlot && topic.id in r.topicsThisSlot && r.lastTalkDay == state.day) return "Schon besprochen – später wieder."
        if (q.slots != null && life.timeSlot !in q.slots) return "Passt nicht zur Tageszeit (${q.slots.joinToString { it.label }})."
        if (q.maxPlayerIntox != null && life.intoxication > q.maxPlayerIntox) return "Du bist zu betrunken dafür."
        if (q.maxNpcIntox != null && npc.intoxication > q.maxNpcIntox)
            return if (topic.consent) "${npc.firstName} hat zu viel getrunken – nicht heute." else "${npc.firstName} hat genug getrunken."
        if (q.minTrust != null && r.trust < q.minTrust) return "Dafür vertraut ${npc.firstName} dir noch nicht genug."
        if (q.minAttraction != null && r.attraction < q.minAttraction) return "Dafür ist die Anziehung noch nicht groß genug."
        if (q.minAffinity != null && r.affinity < q.minAffinity) return "Dafür mag ${npc.firstName} dich noch nicht genug."
        if (q.minTimesMet != null && r.timesMet < q.minTimesMet) return "Ihr kennt euch noch nicht lange genug."
        if (q.cost > 0 && !LifeEngine.canPay(state, LifeEngine.priced(state, q.cost))) return "Nicht genug Gold."
        if (q.needsRoom && !LifeEngine.hasRoom(state)) return "Du brauchst erst ein Zimmer (beim Wirt)."
        if (q.noOtherPartner && life.partner != null && life.partner?.id != npc.id) return "Du bist bereits in einer festen Beziehung."
        return null
    }

    // ------------------------------------------------------------ choosing

    fun choose(state: GameState, npcId: String, topicId: String): Reply {
        val npc = state.life.npc(npcId) ?: return Reply(state, "", Outcome.UNAVAILABLE)
        val topic = DialogueCatalog.topic(topicId) ?: return Reply(state, "", Outcome.UNAVAILABLE)
        if (structuralBlock(state, npc, topic)) return Reply(state, "", Outcome.UNAVAILABLE, summary = "Das passt gerade nicht.")
        softBlock(state, npc, topic)?.let { return Reply(state, "", Outcome.UNAVAILABLE, summary = it) }
        topic.special?.let { return special(state, npc, topic) }
        var next = state
        if (topic.req.cost > 0) next = LifeEngine.pay(next, LifeEngine.priced(next, topic.req.cost))
        val playerLine = fill(topic.playerLine, next, npc)

        val outcome: Outcome
        val line: String
        if (topic.consent) {
            val consent = consent(next, npc, topic)
            outcome = if (consent.given) Outcome.SUCCESS else Outcome.DECLINED
            line = if (consent.given) pickLine(topic.successLines, next, npc, topic) else consent.reason
        } else {
            val ok = topic.difficulty == 0 || score(next, npc, topic) >= topic.difficulty
            outcome = if (ok) Outcome.SUCCESS else Outcome.FAILURE
            line = if (ok) pickLine(if (npc.personality.warmth < 35 && topic.coolSuccessLines.isNotEmpty()) topic.coolSuccessLines else topic.successLines, next, npc, topic)
                else pickLine(topic.failLines.ifEmpty { listOf("„Hm.“") }, next, npc, topic)
        }
        val effect = if (outcome == Outcome.SUCCESS) topic.success else topic.failure
        var updated = next.life.npc(npcId)!!
        updated = applyEffect(next, updated, topic, effect)
        if (outcome == Outcome.DECLINED) {
            val declined = updated.relation.declinedRomance + 1
            updated = updated.copy(relation = updated.relation.copy(
                declinedRomance = declined,
                // Asking again and again after a "no" closes the door; she sets a boundary.
                romanceBoundary = updated.relation.romanceBoundary || declined >= 3,
                affinity = updated.relation.affinity - if (declined >= 3) 6 else 0,
            ))
        }
        updated = updated.copy(relation = updated.relation.copy(
            topicsThisSlot = updated.relation.topicsThisSlot + topic.id,
            stage = if (effect.stage != null && outcome == Outcome.SUCCESS) newStage(updated.relation.stage, effect.stage, topic) else NpcEngine.stageFor(updated.relation),
        ))
        next = LifeEngine.updateNpc(next, updated)
        // Those close to this person feel a little of it too.
        next = NpcSocialEngine.ripple(next, npcId, updated.relation.affinity - npc.relation.affinity)
        effect.teaches?.takeIf { outcome == Outcome.SUCCESS }?.let { skill -> next = next.copy(player = BarracksEngine.raise(next.player, skill, 1)) }
        if (effect.renown != 0) next = LifeEngine.addRenown(next, effect.renown)
        if (effect.passSlots > 0) next = LifeEngine.passTime(next, effect.passSlots).state
        var fade: String? = null
        var extra = ""
        if (outcome == Outcome.SUCCESS && (topic.id == "flirt" || topic.id == "kiss")) {
            val j = NpcSocialEngine.jealousy(next, npcId, if (topic.id == "kiss") "geküsst" else "geflirtet")
            next = j.first
            extra += j.second
        }
        if (outcome == Outcome.SUCCESS && (topic.id == "kiss" || effect.intimate)) {
            val affair = affair(next, updated)
            next = affair.first
            extra = affair.second
        }
        if (outcome == Outcome.SUCCESS && effect.intimate) {
            fade = fadeScene(next, updated)
            next = next.copy(life = next.life.copy(timeSlot = TimeSlot.NIGHT))
            next = LifeEngine.sleep(next).state
            next = LifeEngine.updateNpc(next, next.life.npc(npcId)!!.let { it.copy(relation = it.relation.copy(intimateNights = it.relation.intimateNights + 1, lastIntimateDay = state.day)) })
        }
        if (outcome == Outcome.DECLINED && updated.relation.romanceBoundary && !npc.relation.romanceBoundary)
            extra += " ${npc.firstName} hat dir eine klare Grenze gesetzt. Romantische Annäherungen sind jetzt tabu – Freundschaft bleibt möglich."
        val verdict = when (outcome) { Outcome.SUCCESS -> "gut aufgenommen"; Outcome.FAILURE -> "misslungen"; Outcome.DECLINED -> "abgelehnt"; Outcome.UNAVAILABLE -> "nicht möglich" }
        next = LifeEngine.log(next, "${fill(topic.label, next, npc)} – ${npc.name}: $verdict")
        return Reply(next, line, outcome, playerLine, fade, brawl = outcome == Outcome.FAILURE && topic.failure.brawl, summary = (summary(npc, updated) + extra).trim())
    }

    /** Topics that start something bigger than a line of dialogue. */
    private fun special(state: GameState, npc: NpcIdentity, topic: DialogueTopic): Reply {
        val playerLine = fill(topic.playerLine, state, npc)
        fun mark(s: GameState) = s.life.npc(npc.id)?.let { n -> LifeEngine.updateNpc(s, n.copy(relation = n.relation.copy(topicsThisSlot = n.relation.topicsThisSlot + topic.id))) } ?: s
        return when (topic.special) {
            "invite" -> {
                val r = PartyEngine.invite(state, npc.id)
                Reply(mark(r.state), r.message, if (r.state.life.inParty(npc.id)) Outcome.SUCCESS else Outcome.DECLINED, playerLine,
                    summary = if (r.state.life.inParty(npc.id)) "${npc.firstName} gehört jetzt zu deinem Gefolge." else "")
            }
            "dismiss" -> PartyEngine.dismiss(state, npc.id).let { Reply(it.state, it.message, Outcome.SUCCESS, playerLine) }
            "hire" -> PartyEngine.hire(state, npc.id).let { Reply(it.state, it.message, if (it.state.life.party.size > state.life.party.size) Outcome.SUCCESS else Outcome.FAILURE, playerLine) }
            "duel" -> SkirmishEngine.duel(state, npc.id).let { Reply(mark(it.state), it.message, if (it.state.life.skirmish != null) Outcome.SUCCESS else Outcome.FAILURE, playerLine) }
            "gift" -> {
                val kind = GiftKind.entries.first { "gift_${it.name}" == topic.id }
                MarketEngine.give(state, npc.id, kind)
            }
            else -> Reply(state, "", Outcome.UNAVAILABLE)
        }
    }

    private fun newStage(current: SocialStage, target: SocialStage, topic: DialogueTopic): SocialStage =
        if (topic.id == "breakup") target else if (target.ordinal > current.ordinal) target else current

    /** Social check: skill, personality fit, liking and sobriety. Small deterministic variance. */
    fun score(state: GameState, npc: NpcIdentity, topic: DialogueTopic): Int {
        val p = npc.personality
        val traitTerm = topic.traits.entries.sumOf { (trait, weight) ->
            val v = when (trait) {
                Trait.WARMTH -> p.warmth; Trait.BOLDNESS -> p.boldness; Trait.HONOR -> p.honor
                Trait.HUMOR -> p.humor; Trait.CURIOSITY -> p.curiosity; Trait.TEMPER -> p.temper
            }
            (v - 50) * weight / 50
        }
        val r = npc.relation
        val romance = if (topic.kind == TopicKind.ROMANCE) r.attraction / 3 else 0
        val skill = if (topic.id == "armwrestle" || topic.id == "provoke") (state.player.sword - 50) / 2 else (LifeEngine.socialSkill(state) - 50) / 2
        val liking = r.affinity / 5 + r.trust / 10
        val dislike = if (npc.dislikes.contains("Trunkenbolde") && state.life.intoxication >= 40) -12 else 0
        val variance = Math.floorMod(npc.id.hashCode() * 7 + state.day * 13 + state.life.timeSlot.ordinal * 5 + topic.id.hashCode(), 17) - 8
        return 50 + skill + traitTerm + liking + romance + dislike + variance
    }

    /**
     * Explicit consent. Hard gates first (adults only, no strong intoxication on either side,
     * no boundary, not freshly hurt). Then willingness from attraction, trust, liking and
     * character. A "no" is final for this moment and is respected without penalty.
     */
    fun consent(state: GameState, npc: NpcIdentity, topic: DialogueTopic): Consent {
        val r = npc.relation
        val name = npc.firstName
        if (npc.age < NpcEngine.MIN_ADULT_AGE || state.player.age < NpcEngine.MIN_ADULT_AGE) return Consent(false, "Nein.")
        if (!romanceOpen(state, npc)) return Consent(false, "„Ich mag dich, ${state.player.name.substringBefore(' ')}. Aber nicht so.“")
        if (topic.id == "night" && (state.life.intoxication > 45 || npc.intoxication > 40))
            return Consent(false, "$name schüttelt den Kopf. „Nicht so. Nicht, wenn einer von uns kaum geradeaus schauen kann.“")
        if (r.hurtByAffairDay != null && state.day - r.hurtByAffairDay <= 20) return Consent(false, "„Nach dem, was du getan hast? Nein.“")
        val p = npc.personality
        val willingness = when (topic.id) {
            "kiss" -> r.attraction + r.trust / 2 + r.affinity / 4 + p.boldness / 10 + p.warmth / 10
            "night" -> r.attraction + r.trust / 2 + r.affinity / 4 + p.boldness / 8 - (if (r.stage.ordinal >= SocialStage.PARTNER.ordinal) 0 else p.honor / 8)
            "commit" -> r.trust + r.affinity / 2 + r.attraction / 3 + p.honor / 10
            "propose", "wedding" -> r.trust + r.affinity / 2 + r.attraction / 3 + p.honor / 8
            else -> r.attraction + r.trust
        }
        val needed = NpcSocialEngine.loyaltyPenalty(state, npc) + when (topic.id) {
            "kiss" -> 72
            "night" -> if (r.stage.ordinal >= SocialStage.PARTNER.ordinal) 80 else 98
            "commit" -> 105
            "propose" -> 125
            "wedding" -> 120
            else -> 100
        }
        if (willingness >= needed) return Consent(true, "")
        val reason = topic.failLines.firstOrNull()?.let { fill(it, state, npc) } ?: "„Nein.“"
        return Consent(false, reason)
    }

    private fun applyEffect(state: GameState, npc: NpcIdentity, topic: DialogueTopic, e: TopicEffect): NpcIdentity {
        val r = npc.relation
        var updated = npc.copy(
            intoxication = (npc.intoxication + e.npcDrink).coerceIn(0, 100),
            relation = r.copy(
                affinity = (r.affinity + e.affinity).coerceIn(-100, 100),
                trust = (r.trust + e.trust).coerceIn(0, 100),
                attraction = (r.attraction + e.attraction).coerceIn(0, 100),
                rivalry = (r.rivalry + e.rivalry).coerceIn(0, 100),
                romanceBoundary = r.romanceBoundary || e.boundary,
                hurtByAffairDay = if (e.clearGrievance) null else r.hurtByAffairDay,
            ),
        )
        if (e.memoryKey != null && e.memoryText != null)
            updated = NpcEngine.remember(updated, e.memoryKey, state.day, e.memoryText, e.memoryWeight)
        // A sustained flirt turns into mutual interest only when attraction really is there.
        if (topic.id == "flirt" && updated.relation.attraction >= 30 && updated.relation.stage.ordinal < SocialStage.FLIRT.ordinal)
            updated = updated.copy(relation = updated.relation.copy(stage = SocialStage.FLIRT))
        return updated
    }

    /** If the player already has a partner, someone will talk. Discovery is immediate if the partner is here. */
    private fun affair(state: GameState, lover: NpcIdentity): Pair<GameState, String> {
        val partner = state.life.partner ?: return state to ""
        if (partner.id == lover.id) return state to ""
        val here = NpcEngine.placeOf(partner, state.day, state.life.timeSlot, state) == (state.life.provinceId to state.life.place)
        return if (here) {
            LifeEngine.updateNpc(state, LifeEngine.learnsOfAffair(partner, lover.firstName, state.day)) to
                " ${partner.firstName} hat alles gesehen – und verlässt wortlos den Raum."
        } else {
            state.copy(life = state.life.copy(pendingGossip = (state.life.pendingGossip + "${partner.id}|${lover.firstName}").distinct())) to
                " In ${LifeEngine.locationName(state)} reden die Leute gern. ${partner.firstName} könnte davon erfahren."
        }
    }

    private fun fadeScene(state: GameState, npc: NpcIdentity): String {
        val town = Towns.byProvince(state.life.provinceId)?.tavernName ?: "der Taverne"
        return "Die Tür der Kammer unter dem Dach von „$town“ fällt ins Schloss. Draußen verklingt das Lachen der Schankstube, " +
            "drinnen nur noch das Knistern einer Kerze, euer Atem und das Rascheln von Leinen. " +
            "${npc.firstName} lacht leise über etwas, das du flüsterst. Dann erlischt die Kerze.\n\n" +
            "Am Morgen fällt graues Licht durch die Läden."
    }

    private fun summary(before: NpcIdentity, after: NpcIdentity): String {
        val b = before.relation
        val a = after.relation
        val parts = mutableListOf<String>()
        fun d(label: String, x: Int, y: Int) { if (y != x) parts += "$label ${if (y > x) "+" else ""}${y - x}" }
        d("Sympathie", b.affinity, a.affinity)
        d("Vertrauen", b.trust, a.trust)
        d("Anziehung", b.attraction, a.attraction)
        d("Rivalität", b.rivalry, a.rivalry)
        if (a.stage != b.stage) parts += "Jetzt: ${a.stage.label}"
        return parts.joinToString(" · ")
    }

    // ------------------------------------------------------------ text

    private fun pickLine(lines: List<String>, state: GameState, npc: NpcIdentity, topic: DialogueTopic): String {
        if (lines.isEmpty()) return ""
        val i = Math.floorMod(npc.id.hashCode() + state.day * 3 + topic.id.hashCode() + npc.relation.timesMet, lines.size)
        return fill(lines[i], state, npc)
    }

    fun fill(text: String, state: GameState, npc: NpcIdentity): String {
        val rumor = LifeEngine.rumors(state).firstOrNull()?.let { "${it.title}. ${it.text}" }
            ?: "Ruhig ist es. Zu ruhig, sagen die Alten."
        return text
            .replace("{player}", state.player.name.substringBefore(' '))
            .replace("{npc}", npc.firstName)
            .replace("{town}", LifeEngine.locationName(state))
            .replace("{interest}", npc.interests.firstOrNull() ?: "dein Handwerk")
            .replace("{goal}", npc.goal)
            .replace("{origin}", npc.origin)
            .replace("{backstory}", npc.backstory)
            .replace("{rumor}", rumor)
            .replace("{profession}", npc.profession)
            .replace("{like}", npc.likes.firstOrNull() ?: "Ehrlichkeit")
            .replace("{dislike}", npc.dislikes.firstOrNull() ?: "Lügen")
            .replace("{townFlavor}", Towns.byProvince(state.life.provinceId)?.flavor ?: "Ein Ort wie jeder andere.")
            .let { if ("{crownNews}" in it) it.replace("{crownNews}", NpcSocialEngine.crownNews(state)) else it }
            .let { if ("{tradeTip}" in it) it.replace("{tradeTip}", NpcSocialEngine.tradeTip(state)) else it }
            .let { if ("{travelRumor}" in it) it.replace("{travelRumor}", NpcSocialEngine.travelRumor(state, npc)) else it }
            .let { if ("{bondGossip}" in it) it.replace("{bondGossip}", NpcSocialEngine.bondGossip(state, npc)) else it }
    }
}
