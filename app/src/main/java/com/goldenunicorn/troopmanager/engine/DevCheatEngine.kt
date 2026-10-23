package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * Hidden developer menu, unlocked by tapping the build number seven times in the options.
 * Every cheat is counted so the campaign keeps an honest record that it was used.
 */
object DevCheatEngine {
    enum class Cheat(val label: String, val description: String) {
        RICHES("Schatzkammer", "+250.000 von jeder Ressource, Lager wird vergrößert"),
        ARMOURY("Arsenal füllen", "+50.000 Waffen, Rüstungen, Pferde, 500.000 Pfeile"),
        ARMY("Heer herbeirufen", "+10.000 kampfbereite Soldaten verschiedener Völker"),
        MEGA_ARMY("Kaiserheer", "+100.000 kampfbereite Soldaten"),
        RECRUITS("Rekrutenflut", "+5.000 Rekruten pro Volk"),
        TRAINING("Ausbildung sofort", "Alle laufenden Ausbildungen sind sofort fertig"),
        RENOWN("Legende", "+1.000 Ruhm"),
        SKILLS("Meisterschaft", "+30 Fertigkeitspunkte, alle Kampfkünste +3 Stufen"),
        HEROINES("Alle Heldinnen", "Alle Heldinnen sofort anwerben (erste wird Gefährtin)"),
        DEVOTION("Tiefe Zuneigung", "Gefährtin: Vertrauen, Respekt, Zuneigung 100, keine Konflikte"),
        CONQUER_NEAR("Nachbarländer erobern", "Alle erreichbaren Provinzen gehören dir"),
        CONQUER_ALL("Alle Kronlande", "Alle 42 Provinzen gehören dir"),
        PARTNERS("Handelsimperium", "Alle Partner freigeschaltet, verbündet, 3 Routen"),
        PEACE("Ruhe an der Grenze", "Bedrohung 0, Mauern vollständig repariert"),
        GOD_MODE("Gottmodus Feldzüge", "Feldzüge immer überlegen, unbegrenzt pro Tag, Reisen sofort"),
    }

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)
    private fun safe(value: Long): Int = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun unlock(state: GameState, enabled: Boolean): GameState =
        state.copy(crown = state.crown.copy(devMode = enabled))

    fun apply(state: GameState, cheat: Cheat): GameEngine.ActionResult {
        if (!state.crown.devMode) return ok(state, "Entwicklermodus ist nicht aktiv.")
        val base = CrownEngine.initialize(state)
        val result = when (cheat) {
            Cheat.RICHES -> {
                var storage = base.city.storageCapacity
                var resources = base.resources
                ResourceKind.entries.forEach { kind ->
                    val value = safe(kind.value(resources).toLong() + 250_000)
                    resources = kind.withValue(resources, value)
                    storage = kind.withValue(storage, maxOf(kind.value(storage), value))
                }
                ok(base.copy(resources = resources, city = base.city.copy(storageCapacity = storage)), "Die Schatzkammer quillt über.")
            }
            Cheat.ARMOURY -> {
                var stock = base.militaryStock
                MilitaryGood.entries.forEach { good ->
                    stock = MilitaryEconomyEngine.add(stock, good, if (good == MilitaryGood.ARROWS) 500_000 else 50_000)
                }
                ok(base.copy(militaryStock = stock), "Das Arsenal ist randvoll.")
            }
            Cheat.ARMY -> ok(spawnArmy(base, 10_000), "10.000 Soldaten marschieren in deine Feste.")
            Cheat.MEGA_ARMY -> ok(spawnArmy(base, 100_000), "100.000 Soldaten – ein Kaiserheer steht bereit.")
            Cheat.RECRUITS -> {
                var p = base.population
                Culture.entries.forEach {
                    p = ArmyEngine.adjustPopulation(p, it, 5_000)
                    p = ArmyEngine.adjustRecruits(p, it, 5_000)
                }
                ok(base.copy(population = p, city = base.city.copy(housingCapacity = safe(base.city.housingCapacity + 20_000L))), "+5.000 Rekruten pro Volk.")
            }
            Cheat.TRAINING -> {
                var next = base
                base.trainingQueue.forEach { next = ArmyEngine.add(next, it.type, it.amount, 10, 80) }
                ok(next.copy(trainingQueue = emptyList()), "Alle Ausbildungen sind abgeschlossen.")
            }
            Cheat.RENOWN -> ok(base.copy(renown = base.renown + 1000), "+1.000 Ruhm.")
            Cheat.SKILLS -> {
                var next = base.copy(player = base.player.copy(skillPoints = base.player.skillPoints + 30))
                CombatArt.entries.forEach { art ->
                    repeat(3) { next = CombatArtsEngine.addXp(next, art, CombatArtsEngine.xpNeeded(next.crown.arts.level(art))) }
                }
                ok(next, "+30 Fertigkeitspunkte, alle Kampfkünste +3.")
            }
            Cheat.HEROINES -> {
                var next = base
                CrownCatalog.heroines.forEach { def ->
                    val stage = next.crown.heroine(def.id)?.stage
                    if (stage != HeroineStage.COMPANION && stage != HeroineStage.GENERAL)
                        next = HeroineSagaEngine.recruitImmediately(next, def.id)
                }
                ok(next, "Alle Heldinnen dienen deinem Reich.")
            }
            Cheat.DEVOTION -> {
                if (!base.companion.met) return ok(base, "Du hast noch keine Gefährtin.")
                ok(base.copy(
                    companion = base.companion.copy(trust = 100, respect = 100, affection = 100, diplomacy = maxOf(base.companion.diplomacy, 70)),
                    relationship = base.relationship.copy(conflict = 0, jealousy = 0, attraction = 100, loyalty = 100),
                ), "${base.companion.name} vertraut dir bedingungslos.")
            }
            Cheat.CONQUER_NEAR -> {
                var next = base
                val targets = base.crown.provinces.filter { it.owner != ProvinceOwner.PLAYER && CrownEngine.reachable(base, it.land) }
                targets.forEach { next = CrownEngine.acquire(next, it.id, "Eroberung") }
                ok(next, "${targets.size} Provinzen gehören jetzt dir.")
            }
            Cheat.CONQUER_ALL -> {
                var next = base
                val targets = base.crown.provinces.filter { it.owner != ProvinceOwner.PLAYER }
                targets.forEach { next = CrownEngine.acquire(next, it.id, "Eroberung") }
                ok(next, "Alle Kronlande huldigen dir.")
            }
            Cheat.PARTNERS -> ok(base.copy(crown = base.crown.copy(partners = base.crown.partners.map {
                it.copy(unlocked = true, relation = 100, agreement = 2, routes = maxOf(it.routes, 3))
            })), "Alle Handelshäuser sind deine Verbündeten.")
            Cheat.PEACE -> ok(base.copy(realm = base.realm.copy(threat = 0, wallIntegrity = 100)), "Die Grenze ist ruhig.")
            Cheat.GOD_MODE -> {
                val on = !base.crown.godMode
                ok(base.copy(crown = base.crown.copy(godMode = on)), if (on) "Gottmodus aktiv." else "Gottmodus aus.")
            }
        }
        val counted = result.state.copy(crown = result.state.crown.copy(devCheatsUsed = result.state.crown.devCheatsUsed + 1))
        return ok(ProgressionEngine.update(counted), result.message)
    }

    private fun spawnArmy(state: GameState, total: Int): GameState {
        val mix = listOf(
            UnitType.HUMAN_SWORD to 0.30, UnitType.HUMAN_ARCHER to 0.20, UnitType.KNIGHT to 0.08,
            UnitType.WOOD_RANGER to 0.10, UnitType.GOLD_SPEAR to 0.08, UnitType.CRANE_GUARD to 0.10,
            UnitType.TIGER_CORPS to 0.08, UnitType.DRAGON_ARTILLERY to 0.06,
        )
        var next = state
        mix.forEach { (type, share) -> next = CrownEngine.addSoldiers(next, type, (total * share).toInt(), 40, 85) }
        return next
    }
}
