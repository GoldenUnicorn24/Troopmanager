package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * Events on the march: about one campaign in three meets an ambush, a broken bridge, a deserter
 * or a bold idea from the companion. The player decides, then the campaign resolves with modifiers.
 */
object MarchEngine {
    private const val CHANCE = 0.35

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)

    /** Returns a result with a pending scene when an event happens, or null to fight right away. */
    fun maybeStart(state: GameState, provinceId: String, share: Int): GameEngine.ActionResult? {
        if (state.crown.godMode || state.crown.saga.pendingScene != null) return null
        if (state.day < 8 || state.crown.campaignVictories < 2) return null
        val p = CrownEngine.province(state, provinceId) ?: return null
        if (p.garrison <= 0) return null
        val rng = Random(state.day * 4421 + provinceId.hashCode() + state.crown.campaignVictories * 7)
        if (rng.nextDouble() >= CHANCE) return null
        val companion = CrownEngine.companionAbility(state) != null
        val pool = buildList {
            add(0); add(1); add(2); add(3); add(4)
            if (companion) { add(5); add(5) }
        }
        val scene = scene(state, p, pool[rng.nextInt(pool.size)])
        val next = state.copy(crown = state.crown.copy(
            pendingMarch = PendingMarch(provinceId, share),
            saga = state.crown.saga.copy(pendingScene = scene),
        ))
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
            else -> note = "Ihr marschiert weiter."
        }
        val result = CrownEngine.resolveConquest(next.copy(crown = next.crown.copy(pendingMarch = null)), m.provinceId, m.share, m)
        return ok(result.state, "$note ${result.message}")
    }

    private fun gold(state: GameState, delta: Int) = state.copy(resources = state.resources.copy(gold = (state.resources.gold + delta).coerceAtLeast(0)))
}
