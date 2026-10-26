package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * People among people. Non-player characters have friendships, rivalries and couples of their
 * own; these change over time, are talked about in taverns and carry the player's deeds further
 * (help someone and their friends warm to you, fight someone and their friends turn cold, flirt
 * with someone who is spoken for and their partner notices). Travellers move between towns, and
 * the companion the player designed lives her own life when nobody seeks her out.
 */
object NpcSocialEngine {

    // ------------------------------------------------------------ the web

    /** Bonds are created once per save, deterministically, from who lives where. */
    fun ensureBonds(state: GameState): GameState {
        if (state.life.bonds.isNotEmpty() || state.life.npcs.isEmpty()) return state
        val bonds = mutableListOf<NpcBond>()
        Towns.all.forEach { town ->
            val locals = state.life.npcs.filter { it.homeProvinceId == town.provinceId && !it.designedByPlayer && it.role != NpcRole.SELLSWORD && !it.departed }
                .sortedBy { it.id }
            if (locals.size < 4) return@forEach
            val r = Random(town.provinceId.hashCode().toLong())
            val pool = locals.shuffled(r).toMutableList()
            fun take() = pool.removeAt(0)
            // A couple, if two people here are open to each other.
            val couple = locals.flatMap { a -> locals.filter { b -> a.id < b.id && mutual(a, b) }.map { a to it } }.firstOrNull()
            if (couple != null) {
                bonds += NpcBond(couple.first.id, couple.second.id, BondKind.COUPLE, 60 + r.nextInt(30))
                pool.remove(couple.first); pool.remove(couple.second)
            }
            if (pool.size >= 2) bonds += NpcBond(take().id, take().id, BondKind.FRIENDS, 55 + r.nextInt(35))
            if (pool.size >= 2) bonds += NpcBond(take().id, take().id, BondKind.RIVALS, 40 + r.nextInt(40))
            if (pool.size >= 2 && r.nextBoolean()) bonds += NpcBond(take().id, take().id, if (r.nextBoolean()) BondKind.SIBLINGS else BondKind.FRIENDS, 60 + r.nextInt(30))
        }
        return state.copy(life = state.life.copy(bonds = bonds))
    }

    fun mutual(a: NpcIdentity, b: NpcIdentity): Boolean =
        a.romanceable && b.romanceable && b.gender in a.attractedTo && a.gender in b.attractedTo

    fun bondsOf(state: GameState, id: String): List<NpcBond> = state.life.bonds.filter { it.involves(id) }

    fun partnerOf(state: GameState, id: String): NpcIdentity? =
        state.life.bonds.firstOrNull { it.kind == BondKind.COUPLE && it.involves(id) }?.let { state.life.npc(it.other(id)) }

    // ------------------------------------------------------------ ripples

    /**
     * When the player changes how someone feels about them, those close to that person follow a
     * little: friends and siblings share the feeling, a partner even more, rivals the opposite.
     */
    fun ripple(state: GameState, npcId: String, delta: Int): GameState {
        if (delta in -3..3) return state
        var next = state
        bondsOf(state, npcId).forEach { bond ->
            val other = next.life.npc(bond.other(npcId)) ?: return@forEach
            val share = when (bond.kind) {
                BondKind.COUPLE -> delta / 2
                BondKind.FRIENDS, BondKind.SIBLINGS -> delta / 3
                BondKind.RIVALS -> -delta / 4
            }
            if (share == 0) return@forEach
            next = LifeEngine.updateNpc(next, other.copy(relation = other.relation.copy(affinity = (other.relation.affinity + share).coerceIn(-100, 100))))
        }
        return next
    }

    /** Courting someone who is spoken for is noticed. Called after a successful flirt or kiss. */
    fun jealousy(state: GameState, npcId: String, act: String): Pair<GameState, String> {
        val partner = partnerOf(state, npcId) ?: return state to ""
        val target = state.life.npc(npcId) ?: return state to ""
        val here = NpcEngine.placeOf(partner, state.day, state.life.timeSlot, state) == (state.life.provinceId to state.life.place)
        val hurt = if (here) 18 else 8
        val updated = NpcEngine.remember(partner.copy(relation = partner.relation.copy(
            affinity = (partner.relation.affinity - hurt).coerceAtLeast(-100), rivalry = (partner.relation.rivalry + hurt + 5).coerceAtMost(100),
        )), "jealous_${target.id}", state.day, "du hast mit ${target.firstName} $act", -3)
        var next = LifeEngine.updateNpc(state, updated)
        next = next.copy(life = next.life.copy(bonds = next.life.bonds.map {
            if (it.kind == BondKind.COUPLE && it.involves(npcId)) it.copy(strength = (it.strength - if (act == "geküsst") 25 else 10).coerceAtLeast(0)) else it
        }))
        val line = if (here) " ${partner.firstName} hat es gesehen – und starrt dich an." else " ${target.firstName} ist mit ${partner.firstName} zusammen. Das wird sich herumsprechen."
        return next to line
    }

    /** Spoken-for people are less willing; the more honourable, the less. */
    fun loyaltyPenalty(state: GameState, npc: NpcIdentity): Int {
        val bond = state.life.bonds.firstOrNull { it.kind == BondKind.COUPLE && it.involves(npc.id) } ?: return 0
        return bond.strength / 3 + npc.personality.honor / 3
    }

    // ------------------------------------------------------------ days

    fun daily(state: GameState): GameState {
        var next = ensureBonds(state)
        if (next.life.lastSocialDay >= next.day) return next
        val r = Random(next.day * 3571L + 11)
        val news = mutableListOf<WorldNews>()
        // Bonds drift; some couples part, some friendships turn into love, rivals make up rarely.
        val updated = next.life.bonds.mapNotNull { bond ->
            val a = next.life.npc(bond.a) ?: return@mapNotNull null
            val b = next.life.npc(bond.b) ?: return@mapNotNull null
            if (a.departed || b.departed) return@mapNotNull null
            val drift = r.nextInt(-4, 5) + when (bond.kind) {
                BondKind.COUPLE -> (a.personality.warmth + b.personality.warmth) / 50 - 1
                BondKind.RIVALS -> (a.personality.temper + b.personality.temper) / 60 - 1
                else -> 0
            }
            val strength = (bond.strength + drift).coerceIn(0, 100)
            when {
                bond.kind == BondKind.COUPLE && strength < 15 -> {
                    news += gossip(next, a, "${a.firstName} und ${b.firstName} haben sich getrennt", "Man sagt, es habe Tränen und zerbrochenes Geschirr gegeben.")
                    bond.copy(kind = BondKind.FRIENDS, strength = 30, sinceDay = next.day)
                }
                bond.kind == BondKind.FRIENDS && strength > 92 && mutual(a, b) && next.life.bonds.none { it.kind == BondKind.COUPLE && (it.involves(a.id) || it.involves(b.id)) } -> {
                    news += gossip(next, a, "${a.firstName} und ${b.firstName} sind ein Paar", "Lange hat es gedauert – jetzt halten sie Händchen am Kamin.")
                    bond.copy(kind = BondKind.COUPLE, strength = 70, sinceDay = next.day)
                }
                bond.kind == BondKind.RIVALS && strength < 10 -> {
                    news += gossip(next, a, "${a.firstName} und ${b.firstName} haben Frieden geschlossen", "Ein Krug Bier und ein Handschlag – wer hätte das gedacht.")
                    bond.copy(kind = BondKind.FRIENDS, strength = 35, sinceDay = next.day)
                }
                else -> bond.copy(strength = strength)
            }
        }
        next = next.copy(life = next.life.copy(bonds = updated))
        next = moveTravellers(next, r, news)
        next = companionLife(next, news)
        return next.copy(life = next.life.copy(lastSocialDay = next.day, news = (next.life.news + news).takeLast(30)))
    }

    private fun gossip(state: GameState, about: NpcIdentity, title: String, text: String) =
        WorldNews(state.day, about.homeProvinceId, title, text, severity = 0)

    /** Every few days someone on the road moves on to another town. */
    private fun moveTravellers(state: GameState, r: Random, news: MutableList<WorldNews>): GameState {
        if (state.day % 3 != 0) return state
        val travellers = state.life.npcs.filter { it.role == NpcRole.TRAVELER && !it.departed && !state.life.inParty(it.id) }
        if (travellers.isEmpty()) return state
        val mover = travellers[r.nextInt(travellers.size)]
        val options = Towns.all.map { it.provinceId }.filter { it != mover.homeProvinceId && CrownEngine.province(state, it)?.owner != ProvinceOwner.HORDE }
        if (options.isEmpty()) return state
        val to = options[r.nextInt(options.size)]
        news += WorldNews(state.day, mover.homeProvinceId, "${mover.firstName} ist weitergezogen",
            "${mover.name}, ${mover.profession.lowercase()}, ist nach ${CrownCatalog.info(to).name} aufgebrochen.", severity = 0)
        return LifeEngine.updateNpc(state, NpcEngine.remember(mover.copy(homeProvinceId = to), "moved_$to", state.day,
            "ich bin nach ${CrownCatalog.info(to).name} gezogen", 0))
    }

    /**
     * The designed companion is not waiting for the player. If nobody seeks her out, she moves on
     * (and people talk about where she went). If she is a friend, she sometimes seeks the player.
     */
    private fun companionLife(state: GameState, news: MutableList<WorldNews>): GameState {
        val mira = state.life.companion ?: return state
        if (state.life.landed || mira.departed || state.life.inParty(mira.id)) return state
        val r = mira.relation
        val stranger = r.timesMet == 0 && state.day >= 8
        val drifting = r.timesMet > 0 && r.affinity < 20 && state.day - (r.lastTalkDay.coerceAtLeast(0)) >= 6
        if ((stranger || drifting) && state.day % 4 == 0) {
            val here = mira.homeProvinceId
            val visited = mira.memories.filter { it.key.startsWith("moved_") }.map { it.key.removePrefix("moved_") }.toSet() + state.life.homeProvinceId
            val near = Towns.all.map { it.provinceId }.filter { it != here && it !in visited && CrownEngine.province(state, it)?.owner != ProvinceOwner.HORDE }
                .sortedBy { LifeEngine.travelDays(here, it) * 100 + Math.floorMod((it + state.day).hashCode(), 100) }
            val to = near.firstOrNull() ?: return state
            news += WorldNews(state.day, here, "Eine ${mira.profession} zieht weiter",
                "${mira.name} hat ${CrownCatalog.info(here).name} verlassen. Sie wollte nach ${CrownCatalog.info(to).name}.", severity = 0)
            return LifeEngine.updateNpc(state, NpcEngine.remember(mira.copy(homeProvinceId = to), "moved_$to", state.day,
                "ich bin nach ${CrownCatalog.info(to).name} weitergezogen", 0))
        }
        // A friend comes looking.
        val friend = r.stage.ordinal >= SocialStage.FRIEND.ordinal && mira.homeProvinceId != state.life.provinceId
        if (friend && Math.floorMod(state.day * 31 + mira.id.hashCode(), 100) < 30 && Towns.byProvince(state.life.provinceId) != null) {
            news += WorldNews(state.day, state.life.provinceId, "${mira.firstName} ist in der Stadt",
                "Eine ${mira.profession} hat nach ${state.player.name.substringBefore(' ')} gefragt.", severity = 0)
            return LifeEngine.updateNpc(state, NpcEngine.remember(mira.copy(homeProvinceId = state.life.provinceId), "sought_${state.day}", state.day,
                "ich bin nach ${LifeEngine.locationName(state)} gekommen, um dich zu sehen", 2))
        }
        return state
    }

    // ------------------------------------------------------------ talk

    fun bondGossip(state: GameState, speaker: NpcIdentity): String {
        val local = state.life.bonds.filter { b ->
            b.a != speaker.id && b.b != speaker.id && listOf(b.a, b.b).all { state.life.npc(it)?.homeProvinceId == state.life.provinceId }
        }
        val bond = local.getOrNull(Math.floorMod(state.day + speaker.id.hashCode(), local.size.coerceAtLeast(1))) ?: return "Hier kennt jeder jeden. Mehr sag ich nicht."
        val a = state.life.npc(bond.a)?.firstName ?: "?"
        val b = state.life.npc(bond.b)?.firstName ?: "?"
        return when (bond.kind) {
            BondKind.COUPLE -> "$a und $b? Die sind zusammen. Also – Finger weg, wenn du keinen Ärger willst."
            BondKind.FRIENDS -> "$a und $b sind wie Pech und Schwefel. Tust du dem einen was Gutes, freut sich der andere mit."
            BondKind.RIVALS -> "$a kann $b nicht ausstehen. Frag lieber nicht, warum."
            BondKind.SIBLINGS -> "$a und $b sind Geschwister, auch wenn man es kaum glaubt."
        }
    }

    fun travelRumor(state: GameState, speaker: NpcIdentity): String {
        val mira = state.life.companion
        if (mira != null && !mira.departed && !state.life.inParty(mira.id) && mira.homeProvinceId != state.life.provinceId && mira.relation.timesMet > 0)
            return "In ${CrownCatalog.info(mira.homeProvinceId).name} bin ich einer ${mira.profession} begegnet – ${mira.firstName} hieß sie. Hat nach dir gefragt? Nein. Aber sie hat von dir gesprochen."
        val moved = state.life.news.lastOrNull { it.severity == 0 && it.title.contains("weitergezogen") }
        if (moved != null) return "${moved.title}. ${moved.text}"
        val other = Towns.all.firstOrNull { it.provinceId != state.life.provinceId && it.provinceId != speaker.homeProvinceId } ?: return "Nur Staub und Schlaglöcher."
        return "In ${CrownCatalog.info(other.provinceId).name} gibt es „${other.tavernName}“. ${other.flavor}"
    }

    fun tradeTip(state: GameState): String {
        val rich = state.crown.provinces.filter { Towns.byProvince(it.id) != null }.maxByOrNull { it.wealth } ?: return "Kauf billig, verkauf teuer."
        val danger = state.life.news.lastOrNull { it.severity >= 2 }
        val stall = listOf(MarketEngine.Stall.SMITH, MarketEngine.Stall.TRADER, MarketEngine.Stall.HEALER)[Math.floorMod(state.day, 3)]
        val cheap = CrownCatalog.info(MarketEngine.cheapestTown(stall)).name
        val what = when (stall) { MarketEngine.Stall.SMITH -> "Klingen und Rüstungen"; MarketEngine.Stall.TRADER -> "Tand und Geschenke"; else -> "Kräuter und Heilkunst" }
        return "$what kaufst du am billigsten in $cheap. In ${rich.name} sitzt das Geld locker." + (danger?.let { " Und wo Krieg ist – ${CrownCatalog.info(it.provinceId).name} –, zahlen sie jeden Preis für Klingen und Brot." } ?: "")
    }

    fun crownNews(state: GameState): String {
        val rivals = state.crown.provinces.count { it.owner == ProvinceOwner.RIVAL }
        val hordes = state.crown.provinces.count { it.owner == ProvinceOwner.HORDE }
        val bandits = state.crown.provinces.count { it.owner == ProvinceOwner.BANDITS }
        return "Fremde Kronen halten $rivals Provinzen, die Horden $hordes, Räuberbarone $bandits. Jeder wartet darauf, dass ein anderer zuerst blutet. Wer jetzt Männer und Gold hat, kann sich ein Stück vom Kuchen nehmen."
    }
}
