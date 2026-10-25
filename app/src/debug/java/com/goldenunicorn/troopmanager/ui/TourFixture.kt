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

    /** Project Rebirth: a landless mercenary on day 2, evening, with a designed companion in town. */
    fun lifeState(): GameState {
        var s = LifeEngine.newGame(LifeEngine.StartConfig(
            mode = GameMode.FREE_LIFE, name = "Leon Falkner", age = 24, gender = Gender.MALE, species = Species.HUMAN, culture = Culture.HUMAN,
            background = Background.MERCENARY, profession = "", wealth = WealthLevel.MODEST, homeProvinceId = "gm_rabenfurt",
            appearance = CharacterAppearance(faceShape = 2, jaw = 72, cheekbones = 62, noseShape = 1, eyeColor = 3, browStyle = 2, scar = 2,
                beard = 3, hairStyle = 1, hairLength = 38, hairColor = 1, hairTexture = 1, height = 66, build = 1, muscle = 66,
                clothing = 1, armor = 1, cloak = 1, weapon = 0, primaryColor = 6, accentColor = 3, expression = 3, background = 0),
            attributes = GameEngine.StartingAttributes(sword = 60, bow = 40, riding = 30, leadership = 30, tactics = 40, diplomacy = 60),
            backstory = "Sohn eines Flößers aus Rabenfurt. Mit sechzehn lief er einer Söldnerkompanie hinterher und kam nie wieder ganz zurück.",
            companionChoice = CompanionChoice.FULL,
            companionDraft = NpcEngine.CompanionDraft(
                name = "Mira Taufarn", age = 25, species = Species.HALF_ELF, culture = Culture.WOOD_ELF, profession = "Grenzläuferin",
                appearance = CharacterAppearance(faceShape = 3, jaw = 34, cheekbones = 64, noseSize = 36, noseShape = 2, eyeShape = 0, eyeColor = 2,
                    browStyle = 1, lips = 66, ears = 1, skinTone = 0, hairStyle = 5, hairLength = 80, hairColor = 4, hairTexture = 1,
                    height = 46, build = 1, muscle = 44, clothing = 1, armor = 1, cloak = 3, jewelry = 3, weapon = 1, primaryColor = 1, accentColor = 2, expression = 1, background = 1),
                personality = NpcPersonality(warmth = 64, boldness = 74, honor = 62, humor = 58, curiosity = 66, temper = 28),
                interests = listOf("Bogenschießen", "Sternkunde"),
            ),
        ))
        s = GameEngine.advanceDay(s).state
        s = s.copy(life = s.life.copy(timeSlot = TimeSlot.EVENING))
        // Meet a few regulars so names and memories show.
        TavernEngine.present(s.copy(life = s.life.copy(place = LifePlace.TAVERN))).filter { !it.designedByPlayer }.take(3).forEach { npc ->
            s = DialogueEngine.open(s, npc.id).state
            s = DialogueEngine.choose(s, npc.id, "smalltalk").state
        }
        return s.copy(life = s.life.copy(place = LifePlace.TOWN))
    }
}
