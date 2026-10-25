package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * Events on the march: now and then a campaign meets an ambush, a broken bridge, a deserter
 * or a bold idea from the companion. 16 events, at most one every 6 days, none twice within 30 days.
 */
object MarchEngine {
    private const val CHANCE = 0.22
    private const val COOLDOWN = 6
    private const val NO_REPEAT = 30
    private val keys = listOf("march_ambush", "march_bridge", "march_deserter", "march_village", "march_storm", "march_night",
        "march_merchant", "march_fog", "march_shrine", "march_prisoners", "march_wolves", "march_spy",
        "march_rival_scouts", "march_supply", "march_veteran", "march_omen")

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)

    /** Returns a result with a pending scene when an event happens, or null to fight right away. */
    fun maybeStart(state: GameState, provinceId: String, share: Int): GameEngine.ActionResult? {
        if (state.crown.godMode || state.crown.saga.pendingScene != null) return null
        if (state.day < 8 || state.crown.campaignVictories < 2) return null
        val p = CrownEngine.province(state, provinceId) ?: return null
        if (p.garrison <= 0) return null
        if (state.day - state.reign.lastMarchDay < COOLDOWN) return null
        val rng = Random(state.day * 4421 + provinceId.hashCode() + state.crown.campaignVictories * 7)
        if (rng.nextDouble() >= CHANCE) return null
        val companion = CrownEngine.companionAbility(state) != null
        val pool = keys.indices.filter { i ->
            (i != 5 || companion) && state.day - (state.reign.marchSeen[keys[i]] ?: -999) >= NO_REPEAT
        }
        if (pool.isEmpty()) return null
        val kind = pool[rng.nextInt(pool.size)]
        val scene = scene(state, p, kind)
        val next = state.copy(
            crown = state.crown.copy(pendingMarch = PendingMarch(provinceId, share), saga = state.crown.saga.copy(pendingScene = scene)),
            reign = state.reign.copy(lastMarchDay = state.day, marchSeen = state.reign.marchSeen + (keys[kind] to state.day)),
        )
        return ok(next, "Auf dem Weg nach ${p.name} geschieht etwas. Du musst entscheiden.")
    }

    private fun scene(state: GameState, p: CrownProvince, kind: Int): SagaScene {
        val n = state.companion.name
        val where = "Marsch nach ${p.name}"
        return when (kind) {
            0 -> SagaScene("march_ambush", "march", "Hinterhalt im Engpass", where,
                "Deine Vorhut meldet Bewegung auf den Felsen über dem Pass. ${p.ownerLabel} lauern dort – wer hindurchzieht, gerät in einen Pfeilhagel.",
                listOf(
                    SagaOption("break", "Mit voller Wucht durchbrechen", "Mehr eigene Verluste"),
                    SagaOption("scouts", "Späher vorschicken (400 Gold)", "Kein Nachteil"),
                ) + if (CrownEngine.companionAbility(state) != null) listOf(SagaOption("flank", "$n umgeht sie mit Reitern", "Stärker angreifen · sie geht ein Risiko ein")) else emptyList(),
                speaker = "Vorhut", portraitKey = "knight")
            1 -> SagaScene("march_bridge", "march", "Die zerstörte Brücke", where,
                "Die einzige Brücke über den Fluss ist eingestürzt – oder wurde eingerissen. Das Wasser steht hoch.",
                listOf(
                    SagaOption("build", "Pioniere bauen eine neue Brücke (900 Holz)", "Kein Nachteil"),
                    SagaOption("ford", "Durch die Furt waten", "Mehr Verluste, schwächerer Angriff"),
                ), speaker = "Pioniere", portraitKey = "wall_guard")
            2 -> SagaScene("march_deserter", "march", "Ein Überläufer", where,
                "Ein Soldat der Verteidiger stolpert ins Lager. Er kennt die Schwachstellen von ${p.name} – und verlangt Gold.",
                listOf(
                    SagaOption("pay", "Ihn bezahlen (${goldFor(p)} Gold)", "Deutlich stärkerer Angriff"),
                    SagaOption("question", "Ihn verhören lassen", "Etwas stärkerer Angriff"),
                    SagaOption("send", "Ihn fortschicken", "Kein Effekt"),
                ), speaker = "Überläufer", portraitKey = "orc")
            3 -> SagaScene("march_village", "march", "Ein Dorf in Not", where,
                "Rauch über einem Dorf am Weg. Plünderer haben es überfallen. Die Bauern flehen dein Heer um Hilfe an.",
                listOf(
                    SagaOption("help", "Das Dorf befreien", "+30 Ruhm, etwas mehr Verluste"),
                    SagaOption("onward", "Weitermarschieren", "Kein Effekt"),
                ), speaker = "Dorfälteste", portraitKey = "knight")
            4 -> SagaScene("march_storm", "march", "Unwetter", where,
                "Ein Sturm verwandelt die Straße in Schlamm. Pferde versinken, Wagen bleiben stecken.",
                listOf(
                    SagaOption("wait", "Den Sturm abwarten (${foodFor(state)} Nahrung)", "Kein Nachteil"),
                    SagaOption("push", "Weitermarschieren", "Erschöpft und schwächer im Kampf"),
                ), speaker = "Quartiermeister", portraitKey = "knight")
            6 -> SagaScene("march_merchant", "march", "Ein Händlerzug", where,
                "Ein Händler mit schwer beladenen Wagen kreuzt euren Weg. Er bietet Proviant und Pfeile an – zu einem stolzen Preis.",
                listOf(
                    SagaOption("buy", "Vorräte kaufen (${goldFor(p)} Gold)", "Stärker im Kampf, weniger Verluste"),
                    SagaOption("pass", "Weiterziehen", "Kein Effekt"),
                ), speaker = "Händler", portraitKey = "knight")
            7 -> SagaScene("march_fog", "march", "Nebel im Tal", where,
                "Dichter Nebel legt sich über das Tal. Man sieht keine zehn Schritte weit.",
                listOf(
                    SagaOption("sneak", "Im Nebel heranschleichen", "Überraschung: stärkerer Angriff"),
                    SagaOption("wait", "Warten, bis er sich lichtet", "Kein Effekt"),
                ), speaker = "Vorhut", portraitKey = "knight")
            8 -> SagaScene("march_shrine", "march", "Ein alter Schrein", where,
                "Am Wegesrand steht ein Schrein der alten Mauerwächter. Die Soldaten wollen beten.",
                listOf(
                    SagaOption("pray", "Eine Rast für Gebete (200 Nahrung)", "Höhere Moral, weniger Verluste"),
                    SagaOption("onward", "Keine Zeit", "Kein Effekt"),
                ), speaker = "Feldkaplan", portraitKey = "wall_guard")
            9 -> SagaScene("march_prisoners", "march", "Gefangene Kundschafter", where,
                "Deine Reiter haben drei Kundschafter von ${p.ownerLabel} gefangen. Sie schweigen trotzig.",
                listOf(
                    SagaOption("release", "Freilassen als Zeichen der Gnade", "+20 Ruhm"),
                    SagaOption("question", "Verhören", "Etwas stärkerer Angriff"),
                ), speaker = "Reiterhauptmann", portraitKey = "knight")
            10 -> SagaScene("march_wolves", "march", "Wölfe in der Nacht", where,
                "Ein Rudel riesiger Grauwölfe umkreist das Lager. Die Pferde scheuen.",
                listOf(
                    SagaOption("hunt", "Die Wölfe jagen", "Etwas mehr Verluste, +15 Ruhm"),
                    SagaOption("fires", "Feuer entfachen und warten", "Kein Effekt"),
                ), speaker = "Wache", portraitKey = "knight")
            11 -> SagaScene("march_spy", "march", "Ein Spion im Lager", where,
                "Ein Koch wurde dabei erwischt, wie er Botschaften an ${p.ownerLabel} schickte.",
                listOf(
                    SagaOption("turn", "Ihn falsche Pläne schicken lassen", "Deutlich stärkerer Angriff"),
                    SagaOption("hang", "Ihn vor dem Heer bestrafen", "Moral steigt, kein weiterer Effekt"),
                ), speaker = "Wache", portraitKey = "knight")
            12 -> SagaScene("march_rival_scouts", "march", "Fremde Banner", where,
                "Auf einem Hügel stehen Reiter unter fremdem Banner und beobachten dein Heer. Ein Rivalenkönig schaut genau hin.",
                listOf(
                    SagaOption("salute", "Grüßen lassen", "Kein Effekt"),
                    SagaOption("chase", "Verjagen", "Etwas mehr Verluste, +10 Ruhm"),
                ), speaker = "Vorhut", portraitKey = "knight")
            13 -> SagaScene("march_supply", "march", "Nachschub bleibt aus", where,
                "Der Versorgungszug steckt fest. Die Rationen werden knapp.",
                listOf(
                    SagaOption("forage", "Das Land plündern", "Kein Nachteil im Kampf, Provinz verliert Loyalität"),
                    SagaOption("ration", "Halbe Rationen", "Schwächer im Kampf"),
                ), speaker = "Quartiermeister", portraitKey = "knight")
            14 -> SagaScene("march_veteran", "march", "Ein alter Veteran", where,
                "Ein einarmiger Veteran bittet, dem Heer den Weg zu zeigen. Er hat ${p.name} schon einmal belagert.",
                listOf(
                    SagaOption("accept", "Ihn führen lassen", "Stärkerer Angriff"),
                    SagaOption("decline", "Höflich ablehnen", "Kein Effekt"),
                ), speaker = "Veteran", portraitKey = "wall_guard")
            15 -> SagaScene("march_omen", "march", "Ein Zeichen am Himmel", where,
                "Ein roter Komet zieht über den Nachthimmel. Die Soldaten flüstern von Unglück.",
                listOf(
                    SagaOption("speech", "Eine Rede halten: Der Komet zeigt unseren Sieg", "Höhere Moral"),
                    SagaOption("ignore", "Schweigen", "Etwas schwächer im Kampf"),
                ), speaker = "Feldkaplan", portraitKey = "wall_guard")
            else -> SagaScene("march_night", state.crown.saga.companionId ?: "march", "$n hat einen Plan", where,
                "$n breitet eine Karte im Zelt aus. „Sie erwarten uns am Tag. Wenn wir heute Nacht angreifen, sind sie halb im Schlaf. Ich führe die Vorhut.“",
                listOf(
                    SagaOption("night", "Nachtangriff mit $n an der Spitze", "Viel stärker · sie riskiert eine Verwundung"),
                    SagaOption("day", "Zu gefährlich – wir greifen bei Tag an", "Kein Effekt"),
                ))
        }
    }

    private fun goldFor(p: CrownProvince): Int = (300 + p.wealth * 120)
    private fun foodFor(state: GameState): Int = (state.homeArmySize / 3).coerceIn(200, 20_000)

    fun choose(state: GameState, scene: SagaScene, option: String): GameEngine.ActionResult {
        val march = state.crown.pendingMarch ?: return ok(state, "Der Feldzug ist bereits vorbei.")
        val p = CrownEngine.province(state, march.provinceId) ?: return ok(state, "Provinz nicht gefunden.")
        var next = state
        var m = march
        var note: String
        when (scene.key to option) {
            "march_ambush" to "break" -> { m = m.copy(lossFactor = 1.35); note = "Ihr brecht durch den Hinterhalt." }
            "march_ambush" to "scouts" -> {
                if (next.resources.gold >= 400) { next = gold(next, -400); note = "Die Späher räumen den Pass." }
                else { m = m.copy(lossFactor = 1.35); note = "Kein Gold für Späher – ihr müsst durchbrechen." }
            }
            "march_ambush" to "flank" -> { m = m.copy(ratioFactor = 1.15, companionRisk = true); note = "${next.companion.name} fällt den Lauernden in den Rücken." }
            "march_bridge" to "build" -> {
                if (next.resources.wood >= 900) { next = next.copy(resources = next.resources.copy(wood = next.resources.wood - 900)); note = "Die neue Brücke steht in einer Nacht." }
                else { m = m.copy(lossFactor = 1.2, ratioFactor = 0.95); note = "Zu wenig Holz – ihr watet durch die Furt." }
            }
            "march_bridge" to "ford" -> { m = m.copy(lossFactor = 1.2, ratioFactor = 0.95); note = "Durchnässt erreicht ihr das andere Ufer." }
            "march_deserter" to "pay" -> {
                val cost = goldFor(p)
                if (next.resources.gold >= cost) { next = gold(next, -cost); m = m.copy(ratioFactor = 1.30); note = "Der Überläufer zeigt euch ein unbewachtes Tor." }
                else note = "Du kannst ihn nicht bezahlen. Er verschwindet in der Nacht."
            }
            "march_deserter" to "question" -> { m = m.copy(ratioFactor = 1.10); note = "Das Verhör bringt ein paar nützliche Hinweise." }
            "march_village" to "help" -> { m = m.copy(lossFactor = 1.1); next = next.copy(renown = next.renown + 30); note = "Die Bauern jubeln deinem Heer zu. Ruhm +30." }
            "march_storm" to "wait" -> {
                val food = foodFor(next)
                if (next.resources.food >= food) { next = next.copy(resources = next.resources.copy(food = next.resources.food - food)); note = "Ihr wartet den Sturm im Lager ab." }
                else { m = m.copy(ratioFactor = 0.9); note = "Die Vorräte reichen nicht – ihr müsst weiter." }
            }
            "march_storm" to "push" -> { m = m.copy(ratioFactor = 0.9); note = "Erschöpft, aber pünktlich." }
            "march_night" to "night" -> { m = m.copy(ratioFactor = 1.25, companionRisk = true); note = "Im Mondlicht führt ${next.companion.name} die Vorhut." }
            "march_merchant" to "buy" -> {
                val cost = goldFor(p)
                if (next.resources.gold >= cost) { next = gold(next, -cost); m = m.copy(ratioFactor = 1.10, lossFactor = 0.9); note = "Volle Köcher und frisches Brot." }
                else note = "Du kannst den Preis nicht zahlen."
            }
            "march_fog" to "sneak" -> { m = m.copy(ratioFactor = 1.2); note = "Aus dem Nebel trifft dein Heer die Verteidiger unvorbereitet." }
            "march_shrine" to "pray" -> {
                if (next.resources.food >= 200) { next = next.copy(resources = next.resources.copy(food = next.resources.food - 200)); m = m.copy(lossFactor = 0.85); note = "Gestärkt zieht das Heer weiter." }
                else note = "Kein Proviant für eine Rast."
            }
            "march_prisoners" to "release" -> { next = next.copy(renown = next.renown + 20); note = "Die Kundschafter erzählen von deiner Gnade. Ruhm +20." }
            "march_prisoners" to "question" -> { m = m.copy(ratioFactor = 1.08); note = "Sie verraten die Wachwechsel." }
            "march_wolves" to "hunt" -> { m = m.copy(lossFactor = 1.08); next = next.copy(renown = next.renown + 15); note = "Wolfspelze schmücken die Ritter. Ruhm +15." }
            "march_spy" to "turn" -> { m = m.copy(ratioFactor = 1.25); note = "Der Feind erwartet dich am falschen Tor." }
            "march_spy" to "hang" -> { m = m.copy(lossFactor = 0.95); note = "Das Heer rückt enger zusammen." }
            "march_rival_scouts" to "chase" -> { m = m.copy(lossFactor = 1.05); next = next.copy(renown = next.renown + 10); note = "Die fremden Reiter fliehen." }
            "march_supply" to "forage" -> { next = next.copy(crown = next.crown.withProvinces(next.crown.provinces.map { if (it.id == p.id) it.copy(loyalty = (it.loyalty - 15).coerceAtLeast(0)) else it })); note = "Das Heer nimmt sich, was es braucht." }
            "march_supply" to "ration" -> { m = m.copy(ratioFactor = 0.92); note = "Hungrig, aber diszipliniert." }
            "march_veteran" to "accept" -> { m = m.copy(ratioFactor = 1.15); note = "Der Veteran kennt jeden Stein." }
            "march_omen" to "speech" -> { m = m.copy(ratioFactor = 1.05, lossFactor = 0.95); note = "Jubel statt Angst." }
            "march_omen" to "ignore" -> { m = m.copy(ratioFactor = 0.95); note = "Die Unruhe bleibt." }
            else -> note = "Ihr marschiert weiter."
        }
        val result = CrownEngine.resolveConquest(next.copy(crown = next.crown.copy(pendingMarch = null)), m.provinceId, m.share, m)
        return ok(result.state, "$note ${result.message}")
    }

    private fun gold(state: GameState, delta: Int) = state.copy(resources = state.resources.copy(gold = (state.resources.gold + delta).coerceAtLeast(0)))
}
