package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * Five rival rulers with their own personality. Every few days one of them acts: demands tribute,
 * offers an alliance or a marriage pact, challenges your champion or breaks a treaty.
 * Taking a ruler's capital defeats their realm; allies never march against you.
 */
object RivalEngine {
    private const val INTERVAL = 8

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)

    fun initialize(state: GameState): GameState {
        if (state.crown.rivals.isNotEmpty()) return state
        val rivals = ActCatalog.rivals.map { def ->
            val defeated = CrownEngine.province(state, def.capitalId)?.owner == ProvinceOwner.PLAYER
            RivalState(def.id, def.startAttitude, if (defeated) RivalStatus.DEFEATED else RivalStatus.NEUTRAL)
        }
        return state.copy(crown = state.crown.copy(rivals = rivals))
    }

    fun rival(state: GameState, id: String): RivalState? = state.crown.rivals.firstOrNull { it.id == id }

    fun statusForLand(state: GameState, land: CrownLand): RivalStatus? =
        ActCatalog.rivalForLand(land)?.let { rival(state, it.id)?.status }

    private fun update(state: GameState, r: RivalState): GameState =
        state.copy(crown = state.crown.copy(rivals = state.crown.rivals.map { if (it.id == r.id) r else it }))

    private fun attitude(r: RivalState, delta: Int): RivalState {
        val value = (r.attitude + delta).coerceIn(-100, 100)
        val status = when {
            r.status == RivalStatus.DEFEATED -> RivalStatus.DEFEATED
            r.status == RivalStatus.ALLIED && value >= -10 -> RivalStatus.ALLIED
            value <= -30 -> RivalStatus.HOSTILE
            else -> RivalStatus.NEUTRAL
        }
        return r.copy(attitude = value, status = status)
    }

    // ------------------------------------------------------------ daily

    fun tick(state: GameState): GameState {
        val next = initialize(state)
        if (next.day < 12 || next.crown.saga.pendingScene != null || next.crown.godMode) return next
        val last = next.crown.rivals.maxOfOrNull { it.lastEventDay } ?: 0
        if (next.day - last < INTERVAL) return next
        val candidates = next.crown.rivals.filter { it.status != RivalStatus.DEFEATED }
        if (candidates.isEmpty()) return next
        // Rulers whose lands border yours act first.
        val rng = Random(next.day * 977 + candidates.size)
        val near = candidates.filter { r -> ActCatalog.rival(r.id)?.let { CrownEngine.reachable(next, it.land) } == true }
        val pick = (near.ifEmpty { candidates }).let { it[rng.nextInt(it.size)] }
        val def = ActCatalog.rival(pick.id) ?: return next
        val scene = scene(next, def, pick, rng) ?: return update(next, pick.copy(lastEventDay = next.day))
        return update(next.copy(crown = next.crown.copy(saga = next.crown.saga.copy(pendingScene = scene))), pick.copy(lastEventDay = next.day, events = pick.events + 1))
    }

    private fun tribute(state: GameState): Int = (800 + state.crown.ownedProvinces.size * 350 + state.day * 15).coerceAtMost(25_000)

    private fun scene(state: GameState, def: ActCatalog.RivalDef, r: RivalState, rng: Random): SagaScene? {
        val strong = state.crown.ownedProvinces.size >= 10
        val kind = when {
            r.status == RivalStatus.ALLIED && strong && def.personality in setOf(ActCatalog.Personality.SUSPICIOUS, ActCatalog.Personality.AMBITIOUS) && rng.nextInt(100) < 35 -> "betrayal"
            r.status == RivalStatus.ALLIED -> if (rng.nextBoolean()) "gift" else "pact"
            r.attitude >= 20 -> "alliance"
            def.personality == ActCatalog.Personality.PROUD && rng.nextBoolean() -> "challenge"
            def.personality == ActCatalog.Personality.ARROGANT && r.attitude > -20 && rng.nextBoolean() -> "pact"
            r.attitude < 10 -> "tribute"
            else -> "alliance"
        }
        val who = "${def.name}, ${def.title}"
        fun scene(key: String, title: String, text: String, options: List<SagaOption>) =
            SagaScene("rival_${key}_${def.id}", "rival:${def.id}", title, def.land.label, text, options, speaker = def.name, portraitKey = def.portraitKey, look = def.look)
        return when (kind) {
            "tribute" -> scene("tribute", "Forderung aus ${def.land.label}",
                "Ein Herold trägt das Siegel von $who. ${def.motto} Er verlangt ${tribute(state)} Gold Tribut – sonst marschiert sein Heer.",
                listOf(
                    SagaOption("pay", "Tribut zahlen (${tribute(state)} Gold)", "Haltung +20"),
                    SagaOption("negotiate", "Verhandeln", "Diplomatie entscheidet: halber Tribut oder Zorn"),
                    SagaOption("refuse", "Den Herold fortjagen", "Haltung −25 · ein Heer marschiert"),
                ))
            "alliance" -> scene("alliance", "Ein Bündnisangebot",
                "$who lässt dir ausrichten, dass ein Bündnis beiden Reichen nützen würde. Ihr Heer würde dich nie angreifen – solange der Vertrag hält.",
                listOf(
                    SagaOption("accept", "Bündnis schließen (2.000 Gold Geschenk)", "Verbündet · keine Angriffe aus ${def.land.label}"),
                    SagaOption("decline", "Höflich ablehnen", "Haltung −5"),
                ))
            "pact" -> scene("pact", "Eine Hochzeit der Häuser",
                "$who schlägt vor, einen deiner Generäle mit einem Mitglied des eigenen Hauses zu vermählen. Ein Band aus Blut statt aus Papier.",
                listOf(
                    SagaOption("accept", "Die Verbindung annehmen", "Verbündet · Legitimität +8"),
                    SagaOption("decline", "Ablehnen", "Haltung −10"),
                ))
            "challenge" -> scene("challenge", "Eine Herausforderung",
                "$who verkündet vor allen Höfen, kein Grenzherr könne gegen den Champion von ${def.land.label} bestehen. Wenn du nicht antrittst, lacht das ganze Land.",
                listOf(
                    SagaOption("duel", "Den Champion selbst fordern", "Duell · Sieg: Haltung +30, Ruhm"),
                    SagaOption("ignore", "Die Prahlerei ignorieren", "Haltung −10, Zufriedenheit −3"),
                ))
            "betrayal" -> scene("betrayal", "Verrat!",
                "Boten bringen die Nachricht: $who hat das Bündnis gebrochen. Deine Macht ist zu groß geworden. Ihr Heer sammelt sich an der Grenze.",
                listOf(
                    SagaOption("envoy", "Gesandte schicken (3.000 Gold)", "Vielleicht lässt sich der Bruch heilen"),
                    SagaOption("war", "Dann ist es Krieg", "Feindselig · ein Heer marschiert"),
                ))
            "gift" -> scene("gift", "Ein Geschenk unter Verbündeten",
                "$who sendet Wagen voller Waffen und Gold als Zeichen der Freundschaft.",
                listOf(SagaOption("thanks", "Dankend annehmen", "+Gold, +Waffen")))
            else -> null
        }
    }

    // ------------------------------------------------------------ choices

    fun choose(state: GameState, scene: SagaScene, option: String): GameEngine.ActionResult {
        val id = scene.heroineId.removePrefix("rival:")
        val def = ActCatalog.rival(id) ?: return ok(state, "")
        var r = rival(state, id) ?: return ok(state, "")
        var next = state
        val text: String
        val kind = scene.key.removePrefix("rival_").substringBefore('_')
        when (kind to option) {
            "tribute" to "pay" -> {
                val cost = tribute(next)
                if (next.resources.gold >= cost) {
                    next = gold(next, -cost); r = attitude(r, 20)
                    text = "Das Gold wandert nach ${def.land.label}. ${def.name} ist fürs Erste zufrieden."
                } else {
                    r = attitude(r, -25); next = march(next, def)
                    text = "Du kannst nicht zahlen. ${def.name} nimmt das als Beleidigung – ein Heer marschiert."
                }
            }
            "tribute" to "negotiate" -> {
                val skill = next.player.diplomacy + (if (next.companion.met) next.companion.diplomacy / 2 else 0) + next.realm.level(BuildingType.EMBASSY) * 8
                if (skill >= 60 + Random(next.day).nextInt(30)) {
                    val cost = tribute(next) / 2
                    next = gold(next, -minOf(cost, next.resources.gold)); r = attitude(r, 12)
                    text = "Geschickt verhandelt: ${def.name} begnügt sich mit $cost Gold."
                } else {
                    r = attitude(r, -15); next = march(next, def)
                    text = "Die Verhandlung scheitert. ${def.name} schickt ein Heer."
                }
            }
            "tribute" to "refuse" -> { r = attitude(r, -25); next = march(next, def); text = "Der Herold reitet davon. Bald werden Banner aus ${def.land.label} an deiner Grenze wehen." }
            "alliance" to "accept" -> {
                if (next.resources.gold >= 2000) {
                    next = gold(next, -2000); r = r.copy(status = RivalStatus.ALLIED, attitude = maxOf(r.attitude, 30))
                    next = cancelThreats(next, def.land)
                    text = "Das Bündnis mit ${def.name} ist besiegelt. Aus ${def.land.label} droht keine Gefahr mehr."
                } else text = "Dir fehlt das Gold für ein angemessenes Geschenk. Das Angebot verfällt."
            }
            "alliance" to "decline" -> { r = attitude(r, -5); text = "${def.name} nimmt die Absage kühl zur Kenntnis." }
            "pact" to "accept" -> {
                r = r.copy(status = RivalStatus.ALLIED, attitude = maxOf(r.attitude, 35))
                next = cancelThreats(next, def.land)
                next = next.copy(dynasty = next.dynasty.copy(legitimacy = (next.dynasty.legitimacy + 8).coerceAtMost(100)))
                text = "Die Hochzeit der Häuser wird prächtig gefeiert. ${def.land.label} ist nun durch Blut mit dir verbunden."
            }
            "pact" to "decline" -> { r = attitude(r, -10); text = "${def.name} fühlt sich gekränkt." }
            "challenge" to "duel" -> {
                val skill = 60 + next.crown.ownedProvinces.size * 2
                next = CombatArtsEngine.startDuel(next, "rival:${def.id}", "Champion von ${def.land.label}", def.name, skill)
                text = "Der Champion von ${def.land.label} betritt den Kampfplatz."
            }
            "challenge" to "ignore" -> {
                r = attitude(r, -10)
                next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction - 3).coerceAtLeast(0)))
                text = "Man spottet über dich in den Schenken von ${def.land.label}."
            }
            "betrayal" to "envoy" -> {
                if (next.resources.gold >= 3000 && Random(next.day * 3).nextBoolean()) {
                    next = gold(next, -3000); r = r.copy(status = RivalStatus.NEUTRAL, attitude = 5)
                    text = "Deine Gesandten verhindern den Krieg – vorerst. Das Bündnis aber ist zerbrochen."
                } else {
                    if (next.resources.gold >= 3000) next = gold(next, -3000)
                    r = r.copy(status = RivalStatus.HOSTILE, attitude = -50); next = march(next, def)
                    text = "Die Gesandten kehren mit leeren Händen zurück. ${def.name} marschiert."
                }
            }
            "betrayal" to "war" -> { r = r.copy(status = RivalStatus.HOSTILE, attitude = -60); next = march(next, def); text = "Krieg mit ${def.land.label}." }
            "gift" to "thanks" -> {
                val amount = 1500 + next.crown.ownedProvinces.size * 150
                next = gold(next, amount)
                var stock = next.militaryStock
                listOf(MilitaryGood.SWORDS, MilitaryGood.ARMOR, MilitaryGood.SHIELDS).forEach { stock = MilitaryEconomyEngine.add(stock, it, 300) }
                next = next.copy(militaryStock = stock); r = attitude(r, 5)
                text = "$amount Gold und Waffen für 300 Soldaten erreichen deine Festung."
            }
            else -> text = "${def.name} wartet ab."
        }
        next = update(next, r)
        return ok(CrownEngine.chronicle(next, scene.title, text), text)
    }

    /** Duel against a rival's champion finished. */
    fun duelFinished(state: GameState, rivalId: String, won: Boolean): GameState {
        val def = ActCatalog.rival(rivalId) ?: return state
        val r = rival(state, rivalId) ?: return state
        val next = update(state, attitude(r, if (won) 30 else -10))
        return if (won) CrownEngine.chronicle(next.copy(renown = next.renown + 40), "Champion besiegt", "Ganz ${def.land.label} spricht von deinem Sieg über seinen Champion.")
        else next
    }

    /** Taking a ruler's capital defeats their realm; their remaining provinces become free land. */
    fun onProvinceAcquired(state: GameState, province: CrownProvince): GameState {
        val ready = initialize(state)
        val def = ActCatalog.rivals.firstOrNull { it.capitalId == province.id } ?: return ready
        val r = rival(ready, def.id) ?: return ready
        if (r.status == RivalStatus.DEFEATED) return ready
        var next = update(ready, r.copy(status = RivalStatus.DEFEATED))
        val freed = next.crown.provinces.filter { it.land == def.land && it.owner == ProvinceOwner.RIVAL }
        next = next.copy(crown = next.crown.withProvinces(next.crown.provinces.map {
            if (it in freed) it.copy(owner = ProvinceOwner.FREE, favor = maxOf(it.favor, 60), garrison = it.garrison / 2) else it
        }))
        next = cancelThreats(next, def.land)
        next = CelebrationEngine.add(next, CelebrationKind.KING, "${def.name} ist besiegt",
            "Mit ${province.name} fällt die Krone von ${def.land.label}. ${freed.size} Provinzen sind jetzt freies Land und schauen auf dich.")
        return CrownEngine.chronicle(next, "${def.title} besiegt", "${def.name} verliert die Hauptstadt ${province.name}. Das Reich ${def.land.label} zerfällt.")
    }

    fun giftCost(state: GameState): Int = 800 + state.crown.ownedProvinces.size * 60

    /** Sends gifts to improve a ruler's attitude; at least three days apart per ruler. */
    fun gift(state: GameState, id: String): GameEngine.ActionResult {
        val ready = initialize(state)
        val def = ActCatalog.rival(id) ?: return ok(state, "Unbekannt.")
        val r = rival(ready, id) ?: return ok(state, "Unbekannt.")
        if (r.status == RivalStatus.DEFEATED) return ok(state, "${def.name} herrscht nicht mehr.")
        val cost = giftCost(ready)
        if (ready.resources.gold < cost) return ok(state, "Ein angemessenes Geschenk kostet $cost Gold.")
        val bonus = when (def.personality) {
            ActCatalog.Personality.GREEDY -> 16
            ActCatalog.Personality.ARROGANT, ActCatalog.Personality.PROUD -> 7
            else -> 10
        } + ready.player.diplomacy / 20
        var next = gold(ready, -cost)
        next = update(next, attitude(r, bonus))
        val status = rival(next, id)!!.status
        return ok(next, "${def.name}: Haltung +$bonus (${rival(next, id)!!.attitude}) · ${status.label}.")
    }

    /** Offers an alliance directly once a ruler is friendly enough. */
    fun proposeAlliance(state: GameState, id: String): GameEngine.ActionResult {
        val def = ActCatalog.rival(id) ?: return ok(state, "Unbekannt.")
        val r = rival(state, id) ?: return ok(state, "Unbekannt.")
        if (r.status == RivalStatus.ALLIED || r.status == RivalStatus.DEFEATED) return ok(state, "Nicht möglich.")
        if (r.attitude < 40) return ok(state, "${def.name} verlangt Haltung 40 für ein Bündnis (aktuell ${r.attitude}).")
        if (state.resources.gold < 2000) return ok(state, "Das Bündnisgeschenk kostet 2.000 Gold.")
        var next = update(gold(state, -2000), r.copy(status = RivalStatus.ALLIED))
        next = cancelThreats(next, def.land)
        return ok(CrownEngine.chronicle(next, "Bündnis mit ${def.land.label}", "${def.name} schließt ein Bündnis mit dir."), "Bündnis mit ${def.name} geschlossen.")
    }

    fun subdued(state: GameState): Int = state.crown.rivals.count { it.status == RivalStatus.DEFEATED || it.status == RivalStatus.ALLIED }

    private fun march(state: GameState, def: ActCatalog.RivalDef): GameState {
        val owned = state.crown.ownedProvinces
        if (owned.isEmpty()) return state
        val source = state.crown.provinces.filter { it.land == def.land && it.owner == ProvinceOwner.RIVAL && it.garrison >= 100 }.maxByOrNull { it.garrison } ?: return state
        val target = owned.minByOrNull { if (it.land == def.land || (CrownCatalog.neighbors[def.land] ?: emptySet()).contains(it.land)) CrownWarEngine.defensePower(it) else Long.MAX_VALUE } ?: return state
        val soldiers = (source.garrison * 0.5).toInt().coerceAtLeast(100)
        val threat = CrownThreat("threat_${state.crown.nextId}", target.id, source.id, ProvinceOwner.RIVAL, soldiers, source.garrisonQuality,
            state.day, state.day + 5, attackerName = def.name)
        val provinces = state.crown.provinces.map { if (it.id == source.id) it.copy(garrison = it.garrison - soldiers) else it }
        return state.copy(crown = state.crown.withProvinces(provinces).copy(threats = state.crown.threats + threat, nextId = state.crown.nextId + 1))
    }

    private fun cancelThreats(state: GameState, land: CrownLand): GameState {
        val sources = state.crown.provinces.filter { it.land == land }.map { it.id }.toSet()
        return state.copy(crown = state.crown.copy(threats = state.crown.threats.filterNot { it.sourceId in sources && it.attacker == ProvinceOwner.RIVAL }))
    }

    private fun gold(state: GameState, delta: Int) = state.copy(resources = state.resources.copy(gold = (state.resources.gold + delta).coerceAtLeast(0)))
}
