package com.goldenunicorn.troopmanager.ui

import com.goldenunicorn.troopmanager.engine.*
import com.goldenunicorn.troopmanager.model.*

/** A deterministic mid-game realm for screenshot tours: ~40 days played, partner met, several provinces. */
object TourFixture {
    fun richState(days: Int = 40): GameState {
        var s = HeroineSagaEngine.setPartner(GameEngine.newGame("Leon", 27, Species.HALF_ELF, null),
            PartnerProfile("Mira", 25, Species.HALF_ELF, "alina", null))
        val picks = mapOf(Culture.HUMAN to UnitType.HUMAN_SWORD, Culture.WOOD_ELF to UnitType.WOOD_RANGER,
            Culture.WALL to UnitType.TIGER_CORPS, Culture.GOLD_ELF to UnitType.GOLD_SPEAR)
        repeat(days) {
            var guard = 0
            while ((s.crown.saga.pendingScene != null || s.crown.celebrations.isNotEmpty()) && guard++ < 6) {
                if (s.crown.celebrations.isNotEmpty()) { s = CelebrationEngine.dismiss(s); continue }
                val scene = s.crown.saga.pendingScene!!
                s = HeroineSagaEngine.choose(s, scene.options.first().id).state
            }
            var inbox = 0
            while (inbox++ < 8 && s.day < days - 2) {
                val item = InboxEngine.items(s).firstOrNull { it.options.isNotEmpty() } ?: break
                val r = InboxEngine.choose(s, item.key, item.options.first().id)
                if (r.state === s) break
                s = r.state
            }
            s.crown.provinces.filter { it.owner == ProvinceOwner.FREE && CrownEngine.reachable(s, it.land) }
                .forEach { p -> if (s.resources.gold > CrownEngine.landPrice(s, p) + 4_000) s = CrownEngine.buy(s, p.id).state }
            val targets = s.crown.provinces.filter { it.owner != ProvinceOwner.PLAYER && CrownEngine.reachable(s, it.land) }
                .sortedBy { it.garrison.toLong() * it.garrisonQuality }
            for (t in targets.take(1)) {
                val preview = CrownEngine.preview(s, t.id, 100) ?: continue
                if (preview.ratio > 1.5 && s.crown.pendingMarch == null && s.crown.saga.pendingScene == null) s = CrownEngine.conquer(s, t.id, 100).state
            }
            if (CrownEngine.levyCooldown(s) == 0 && s.crown.ownedProvinces.isNotEmpty()) s = CrownEngine.levy(s).state
            if (EconomyEngine.production(s).net.food > 0) s = CrownEngine.trainAll(s, picks).state
            s.crown.saga.heroines.forEach { h -> if (h.stage == HeroineStage.TRIAL && HeroineSagaEngine.trialProgress(s, h.id).done) s = HeroineSagaEngine.recruit(s, h.id, true).state }
            if (s.reign.wonders.none { !it.finished }) WonderKind.entries.firstOrNull { ReignEngine.wonderBlocker(s, it) == null }?.let { s = ReignEngine.startWonder(s, it).state }
            if (s.battleSession != null) s = ActEngine.reconcile(CrownWarEngine.reconcile(TacticalBattleEngine.autoResolve(s))).copy(battleSession = null)
            if (s.crown.saga.pendingScene == null) s = GameEngine.advanceDay(s).state
        }
        // Leave a calm screen: no overlay, but some open decisions for the inbox.
        return s.copy(crown = s.crown.copy(celebrations = emptyList(), saga = s.crown.saga.copy(pendingScene = null), duel = null, pendingMarch = null),
            battleSession = null, tutorialSeen = true)
    }

    fun battle(state: GameState): GameState =
        TacticalBattleEngine.start(state, TacticalBattleEngine.Kind.STORM, "Ork-Sturm vor der Mauer", EnemyType.ORC, (state.homeArmySize * 0.7).toInt().coerceAtLeast(500)).state
}
