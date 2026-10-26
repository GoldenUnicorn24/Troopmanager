package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * The player's personal following. Friends join because they want to (their own decision from
 * liking, trust and character); sellswords join for coin and leave when unpaid. Followers travel
 * with the player, sit with them in taverns, fight beside them and grow closer on the road.
 */
object PartyEngine {
    const val MAX_PARTY = 5
    const val HIRE_FEE = 20
    const val SELLSWORD_WAGE = 2

    private val neverJoin = setOf(NpcRole.INNKEEPER, NpcRole.SMITH, NpcRole.HEALER, NpcRole.MERCHANT, NpcRole.NOBLE)

    fun members(state: GameState): List<NpcIdentity> = state.life.party.mapNotNull { state.life.npc(it.npcId) }

    fun sellswords(state: GameState): Int = state.life.party.count { it.wage > 0 }

    fun canAsk(state: GameState, npc: NpcIdentity): Boolean =
        npc.role !in neverJoin && !state.life.inParty(npc.id) && !npc.departed

    /** Why a friend agrees or not: liking, trust, wanderlust and whether the road fits their goal. */
    fun willingness(state: GameState, npc: NpcIdentity): Int {
        val r = npc.relation
        val p = npc.personality
        val fits = npc.role in setOf(NpcRole.TRAVELER, NpcRole.COMPANION, NpcRole.MERCENARY_CAPTAIN, NpcRole.BARD) ||
            npc.goal.contains("Ruhm") || npc.goal.contains("Welt") || npc.goal.contains("Mörder")
        return r.affinity + r.trust + p.boldness / 3 + p.curiosity / 4 + (if (fits) 20 else 0) +
            (if (r.stage.romantic) 15 else 0) - (if (npc.role == NpcRole.REGULAR) 25 else 0)
    }

    fun invite(state: GameState, npcId: String): LifeEngine.Result {
        val npc = state.life.npc(npcId) ?: return LifeEngine.Result(state, "")
        if (!canAsk(state, npc)) return LifeEngine.Result(state, "„Mein Platz ist hier.“")
        if (state.life.party.size >= MAX_PARTY) return LifeEngine.Result(state, "Dein Gefolge ist voll ($MAX_PARTY).")
        if (willingness(state, npc) < 120) {
            val updated = NpcEngine.remember(npc, "invite_declined_${state.day}", state.day, "du hast mich gefragt, ob ich mit dir ziehe", 1)
            return LifeEngine.Result(LifeEngine.updateNpc(state, updated),
                "${npc.firstName} schüttelt den Kopf. „Noch nicht. Ich kenne dich kaum – und ich habe hier noch etwas zu erledigen.“")
        }
        val joined = NpcEngine.remember(npc.copy(relation = npc.relation.copy(trust = (npc.relation.trust + 5).coerceAtMost(100))),
            "joined_party", state.day, "ich habe mich entschieden, mit dir zu ziehen", 3)
        var next = LifeEngine.updateNpc(state, joined)
        next = next.copy(life = next.life.copy(party = next.life.party + PartyMember(npcId, state.day)))
        return LifeEngine.Result(LifeEngine.log(next, "${npc.name} schließt sich dir an."),
            "${npc.firstName} steht auf und schultert das Bündel. „Also gut. Wohin geht's?“")
    }

    fun hire(state: GameState, captainId: String): LifeEngine.Result {
        val captain = state.life.npc(captainId) ?: return LifeEngine.Result(state, "")
        if (state.life.party.size >= MAX_PARTY) return LifeEngine.Result(state, "Mehr Leute kannst du nicht führen ($MAX_PARTY).")
        if (!LifeEngine.canPay(state, HIRE_FEE)) return LifeEngine.Result(state, "„$HIRE_FEE Gold Handgeld, im Voraus. Komm wieder, wenn du sie hast.“")
        val id = "${captain.homeProvinceId}:sellsword:${state.life.nextId}"
        val recruit = NpcEngine.generate(id, NpcRole.SELLSWORD, captain.homeProvinceId).let {
            it.copy(profession = if (it.gender == Gender.FEMALE) "Söldnerin" else "Söldner", romanceable = it.romanceable && Random(id.hashCode()).nextBoolean(),
                relation = NpcRelation(stage = SocialStage.ACQUAINTANCE, affinity = 10, trust = 10, timesMet = 1, lastTalkDay = state.day),
                metDay = state.day, combatStyle = listOf("Schwert und Schild", "Speerkämpfer", "Armbrust", "Streitaxt")[Random(id.hashCode()).nextInt(4)])
        }
        var next = LifeEngine.pay(state, HIRE_FEE)
        next = next.copy(life = next.life.copy(
            npcs = next.life.npcs + recruit,
            party = next.life.party + PartyMember(id, state.day, wage = SELLSWORD_WAGE),
            nextId = next.life.nextId + 1,
            career = if (next.life.career == CareerRank.COMMONER) CareerRank.MERCENARY else next.life.career,
        ))
        next = LifeEngine.updateNpc(next, NpcEngine.remember(captain, "hired_${recruit.id}", state.day, "du hast eine Klinge aus meiner Kompanie angeworben", 1))
        next = promote(next)
        return LifeEngine.Result(LifeEngine.log(next, "${recruit.name} angeworben."),
            "${captain.firstName} pfeift. ${recruit.name} tritt vor, nickt knapp. „$SELLSWORD_WAGE Gold am Tag, und ich folge dir überallhin.“")
    }

    fun dismiss(state: GameState, npcId: String): LifeEngine.Result {
        val member = state.life.party.firstOrNull { it.npcId == npcId } ?: return LifeEngine.Result(state, "")
        val npc = state.life.npc(npcId) ?: return LifeEngine.Result(state, "")
        var next = state.copy(life = state.life.copy(party = state.life.party - member))
        next = if (member.wage > 0) LifeEngine.updateNpc(next, npc.copy(departed = true))
        else LifeEngine.updateNpc(next, NpcEngine.remember(npc.copy(homeProvinceId = state.life.provinceId), "left_party", state.day,
            "wir haben uns in ${LifeEngine.locationName(state)} getrennt", 1))
        return LifeEngine.Result(LifeEngine.log(next, "${npc.name} verlässt das Gefolge."), "„Pass auf dich auf.“ ${npc.firstName} geht eigene Wege.")
    }

    /** Daily: wages, healing of wounded followers, desertion when unpaid. */
    fun daily(state: GameState): GameState {
        if (state.life.party.isEmpty()) return state
        var next = state
        val wages = state.life.party.sumOf { it.wage }
        if (wages > 0) {
            if (LifeEngine.canPay(next, wages)) next = LifeEngine.pay(next, wages)
            else {
                val leaving = next.life.party.filter { it.wage > 0 }
                leaving.forEach { m -> next.life.npc(m.npcId)?.let { next = LifeEngine.updateNpc(next, it.copy(departed = true)) } }
                next = next.copy(life = next.life.copy(party = next.life.party.filter { it.wage == 0 }))
                next = LifeEngine.log(next, "Ohne Sold ziehen ${leaving.size} Söldner ab.")
                next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, "Söldner ziehen ab", "Der Sold blieb aus. ${leaving.size} Klingen suchen sich einen anderen Herrn."))
            }
        }
        return next.copy(life = next.life.copy(party = next.life.party.map { it.copy(wounded = (it.wounded - 15).coerceAtLeast(0)) }))
    }

    /** Shared days on the road bring people closer. */
    fun travelledTogether(state: GameState, toId: String, days: Int): GameState {
        var next = state
        members(state).forEach { npc ->
            val r = npc.relation
            next = LifeEngine.updateNpc(next, NpcEngine.remember(npc.copy(relation = r.copy(
                trust = (r.trust + 2 * days).coerceAtMost(100), affinity = (r.affinity + days).coerceAtMost(100),
            )), "travel_$toId", state.day, "wir sind gemeinsam nach ${CrownCatalog.info(toId).name} gezogen", 2))
        }
        return next
    }

    /** Fighting value of one follower, from body, courage and trade. */
    fun power(npc: NpcIdentity, member: PartyMember?): Int {
        val base = 28 + npc.appearance.muscle / 5 + npc.personality.boldness / 5 +
            when (npc.role) { NpcRole.SELLSWORD -> 12; NpcRole.MERCENARY_CAPTAIN -> 16; NpcRole.COMPANION -> 14; else -> 0 } +
            if (npc.combatStyle == "Keine Kämpferin" || npc.combatStyle.isBlank()) -20 else 0
        return (base - (member?.wounded ?: 0) / 2).coerceAtLeast(5)
    }

    /** Hauptmann with three or more sworn blades and enough renown. */
    fun promote(state: GameState): GameState {
        val life = state.life
        val next = when {
            life.career in setOf(CareerRank.MERCENARY, CareerRank.SOLDIER) && sellswords(state) >= 3 && life.renown >= CareerRank.CAPTAIN.renown -> CareerRank.CAPTAIN
            life.career == CareerRank.CAPTAIN && life.party.size >= MAX_PARTY && life.renown >= CareerRank.COMMANDER.renown -> CareerRank.COMMANDER
            else -> life.career
        }
        if (next == life.career) return state
        return state.copy(life = life.copy(career = next), chronicle = state.chronicle + ChronicleEntry(state.day, next.label,
            "${state.player.name} führt jetzt eigene Leute. Man nennt dich ${next.label}."))
    }
}
