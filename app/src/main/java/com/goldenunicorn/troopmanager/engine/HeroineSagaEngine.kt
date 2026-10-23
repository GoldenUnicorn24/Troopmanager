package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/**
 * Heldinnen-Saga: rumors -> journey -> first meeting -> trial -> recruitment as Feldherrin.
 * The first heroine you recruit as your own Feldherrin becomes the companion; her story chapters
 * lead through shared campaigns and her personal quest to (optional, consent-gated) romance,
 * a royal wedding and the coronation as queen. Romantic steps use [RelationshipEngine.action]
 * so every existing consent rule, adult check and threshold still applies.
 */
object HeroineSagaEngine {
    const val FRIENDSHIP_COMPLETE = 99
    const val FINAL_CHAPTER = 9
    private const val GENERAL_ID_BASE = -100L

    private fun ok(state: GameState, text: String) = GameEngine.ActionResult(state, text)
    private fun saga(state: GameState) = state.crown.saga
    private fun withSaga(state: GameState, saga: HeroineSagaState) = state.copy(crown = state.crown.copy(saga = saga))
    private fun withHeroine(state: GameState, heroine: HeroineState) =
        withSaga(state, saga(state).copy(heroines = saga(state).heroines.map { if (it.id == heroine.id) heroine else it }))

    fun generalId(heroineId: String): Long =
        GENERAL_ID_BASE - CrownCatalog.heroines.indexOfFirst { it.id == heroineId }.coerceAtLeast(0)

    fun companionDef(state: GameState): CrownCatalog.HeroineDef? = saga(state).companionId?.let { def(state, it) }

    const val PARTNER_MEET_DAY = 7

    fun isPartner(state: GameState, heroineId: String): Boolean = saga(state).partnerId == heroineId

    /** Replaces the template heroine's names with the player's own partner. */
    fun personalize(state: GameState, heroineId: String, text: String): String {
        val partner = saga(state).partner ?: return text
        if (partner.background != heroineId) return text
        val template = CrownCatalog.heroine(heroineId) ?: return text
        val first = partner.name.substringBefore(' ')
        return text.replace(template.name, partner.name)
            .replace(template.name.substringBefore(' ') + " Mei", first)
            .replace(template.name.substringBefore(' '), first)
            .replace("eine Halbelbin", speciesNoun(partner.species))
    }

    private fun speciesNoun(species: Species): String = when (species) {
        Species.HUMAN -> "eine junge Frau"
        Species.ELF -> "eine Elbin"
        Species.HALF_ELF -> "eine Halbelbin"
    }

    /** Heroine definition; for the custom partner the template is personalized. */
    fun def(state: GameState, heroineId: String): CrownCatalog.HeroineDef? {
        val template = CrownCatalog.heroine(heroineId) ?: return null
        val partner = saga(state).partner?.takeIf { it.background == heroineId } ?: return template
        fun p(text: String) = personalize(state, heroineId, text)
        return template.copy(
            name = partner.name, species = partner.species, age = partner.age,
            weapon = partner.weapon ?: template.weapon, armor = partner.armorStyle ?: template.armor,
            epithet = "Deine zukünftige Partnerin · ${template.epithet}",
            rumor = p(template.rumor), trialText = p(template.trialText), personalQuestText = p(template.personalQuestText),
        )
    }

    /** Called right after character creation: the partner arrives at her place in about a week. */
    fun setPartner(state: GameState, partner: PartnerProfile?): GameState {
        if (partner == null || CrownCatalog.heroine(partner.background) == null) return state
        val ready = initialize(CrownEngine.initialize(state))
        val safe = partner.copy(name = partner.name.trim().ifBlank { "Alina" }.take(24), age = partner.age.coerceIn(18, 120))
        val withPartner = withSaga(ready, saga(ready).copy(partner = safe))
        val h = withPartner.crown.heroine(safe.background) ?: return withPartner
        return withHeroine(withPartner, h.copy(stage = HeroineStage.TRAVELING, arrivalDay = maxOf(PARTNER_MEET_DAY, ready.day + 1), favor = 30))
    }

    private fun victoryCount(state: GameState) = state.victories + state.crown.campaignVictories

    // ------------------------------------------------------------ setup

    fun initialize(state: GameState): GameState {
        if (saga(state).heroines.isNotEmpty()) return state
        var heroines = CrownCatalog.heroines.map { HeroineState(it.id) }
        var companionId: String? = null
        if (state.companion.met) {
            if (state.companion.name.startsWith("Alina")) {
                companionId = "alina"
                heroines = heroines.map { if (it.id == "alina") it.copy(stage = HeroineStage.COMPANION, recruitedDay = state.day, metDay = state.day, favor = 80) else it }
            } else companionId = "legacy"
        }
        return withSaga(state, HeroineSagaState(
            enabled = !state.companion.met,
            heroines = heroines,
            companionId = companionId,
            chapter = chapterFor(state.relationship.romanceStage),
            chapterStartDay = state.day,
            chapterStartVictories = victoryCount(state),
        ))
    }

    private fun chapterFor(stage: RomanceStage): Int = when (stage) {
        RomanceStage.NONE -> 0
        RomanceStage.INTEREST -> 4
        RomanceStage.ROMANCE -> 5
        RomanceStage.PARTNERSHIP -> 6
        RomanceStage.ENGAGED -> 7
        RomanceStage.MARRIED -> 8
        RomanceStage.CO_RULERS -> FINAL_CHAPTER
    }

    fun available(state: GameState, def: CrownCatalog.HeroineDef): Boolean = when (def.id) {
        "alina" -> true
        "seraphine" -> state.renown >= 50 || state.day >= 6
        "liang" -> state.day >= 10 || state.armySize >= 1500
        "ithiliel" -> state.renown >= 300 || CrownEngine.ownsIn(state, CrownLand.WALDHAIN) || CrownEngine.ownsIn(state, CrownLand.SONNENHAIN)
        else -> true
    }

    fun travelCost(def: CrownCatalog.HeroineDef): Resources =
        Resources(gold = 120 * def.travelDays, food = 80 * def.travelDays, wood = 0, stone = 0, iron = 0)

    // ------------------------------------------------------------ daily

    fun tick(state: GameState): GameState {
        var next = initialize(state)
        // Keep the story in step with romance progress made on the ruling-pair screen.
        val synced = chapterFor(next.relationship.romanceStage)
        val current = saga(next)
        if (current.companionId != null && current.chapter != FRIENDSHIP_COMPLETE && synced > current.chapter && synced >= 4)
            next = withSaga(next, current.copy(chapter = synced, chapterStartDay = next.day))
        if (saga(next).pendingScene != null) return next

        val arrived = saga(next).heroines.firstOrNull { it.stage == HeroineStage.TRAVELING && (it.arrivalDay ?: Int.MAX_VALUE) <= next.day }
        if (arrived != null) {
            val def = def(next, arrived.id) ?: return next
            next = withSaga(next, saga(next).copy(pendingScene = meetingScene(next, def)))
            return if (isPartner(next, def.id)) CrownEngine.chronicle(next, "Eine schicksalhafte Begegnung", "Eine Woche nach deinem Amtsantritt führt dich der Weg nach ${def.location}. Dort begegnest du ${def.name}.")
                else CrownEngine.chronicle(next, "Ankunft: ${def.location}", "Du erreichst ${def.location}. Dort wartet eine Begegnung, die alles verändern kann.")
        }
        val s = saga(next)
        if (s.companionId != null && next.companion.met && s.chapter < FINAL_CHAPTER && chapterReady(next).first) {
            next = withSaga(next, s.copy(pendingScene = chapterScene(next, s.chapter)))
        }
        return RoyalCourtEngine.tick(next)
    }

    // ------------------------------------------------------------ meeting

    fun travel(state: GameState, heroineId: String): GameEngine.ActionResult {
        val def = def(state, heroineId) ?: return ok(state, "Unbekannt.")
        val h = state.crown.heroine(heroineId) ?: return ok(state, "Unbekannt.")
        if (h.stage != HeroineStage.RUMOR) return ok(state, "Ihr seid euch bereits begegnet oder du bist unterwegs.")
        if (!available(state, def)) return ok(state, def.unlockHint)
        val cost = travelCost(def)
        if (state.resources.gold < cost.gold || state.resources.food < cost.food)
            return ok(state, "Die Reise kostet ${cost.gold} Gold und ${cost.food} Nahrung für dein Gefolge.")
        val days = if (state.crown.godMode) 0 else def.travelDays
        var next = state.copy(resources = state.resources.copy(gold = state.resources.gold - cost.gold, food = state.resources.food - cost.food))
        next = withHeroine(next, h.copy(stage = HeroineStage.TRAVELING, arrivalDay = state.day + days))
        next = CrownEngine.chronicle(next, "Aufbruch nach ${def.location}", "Mit kleinem Gefolge brichst du auf. Ankunft in $days Tagen.")
        if (days == 0) next = tick(next)
        return ok(next, if (days == 0) "Du erreichst ${def.location}." else "Aufbruch nach ${def.location}. Ankunft in $days Tagen.")
    }

    private fun meetingScene(state: GameState, def: CrownCatalog.HeroineDef): SagaScene {
        val scene = templateMeetingScene(state, def)
        if (!isPartner(state, def.id)) return scene
        fun p(text: String) = personalize(state, def.id, text)
        return scene.copy(
            text = "Eine Woche nach deinem Amtsantritt führt dich eine Grenzreise nach ${def.location}. " + p(scene.text),
            options = scene.options.map { it.copy(label = p(it.label), hint = p(it.hint)) },
        )
    }

    private fun templateMeetingScene(state: GameState, def: CrownCatalog.HeroineDef): SagaScene {
        val ruler = state.player.name
        return when (def.id) {
            "alina" -> SagaScene("meet_alina", def.id, "Pfeile im Nebel", def.location,
                "Nebel liegt über der Furt. Fünf Orkspäher waten durch das Wasser – und einer nach dem anderen fällt, getroffen von Pfeilen aus dem Schilf. Als der letzte flieht, tritt eine Halbelbin hervor, den Bogen noch gespannt. „Noch ein Grundherr, der sein Land erst entdeckt, wenn es brennt?“ fragt sie und mustert dich.",
                listOf(
                    SagaOption("fight", "Gemeinsam den fliehenden Späher stellen", "Mut beeindruckt sie"),
                    SagaOption("honest", "Zugeben, dass du zu spät gekommen bist", "Ehrlichkeit schafft Vertrauen"),
                    SagaOption("offer", "Ihr sofort Sold anbieten", "Sie ist keine Söldnerin"),
                ))
            "seraphine" -> SagaScene("meet_seraphine", def.id, "Die schwarze Ritterin", def.location,
                "Die Tribünen von Aldenburg toben. Eine Ritterin in geschwärzter Platte hebt ihr Visier, nachdem sie den dritten Lehnsherrn in den Staub geworfen hat. Kein Wappen, kein Haus – nur ein Blick, der dich über den Platz hinweg findet. „Ihr seid $ruler? Man sagt, Ihr baut ein Reich. Wisst Ihr auch ein Schwert zu führen?“",
                listOf(
                    SagaOption("challenge", "Sie vor allen Rittern herausfordern", "Startet ein Duell · Sieg erfüllt ihre Bewährungsprobe"),
                    SagaOption("watch", "Auf sie wetten und ihren Stil studieren", "+400 Gold, sie bemerkt deinen Blick"),
                    SagaOption("talk", "Sie nach dem Turnier am Brunnen ansprechen", "Sie erzählt von ihrem verlorenen Haus"),
                ))
            "liang" -> SagaScene("meet_liang", def.id, "Der Rat am Kranichtor", def.location,
                "Im Kriegsrat am Kranichtor streiten Generäle über eine Horde, die in drei Tagen den Wall erreicht. Eine Frau in blauer Lamellenrüstung schweigt, bis der Ratsherr dich spöttisch fragt, was ein Grenzherr wohl raten würde. Liang Mei hebt eine Augenbraue – sie wartet auf deine Antwort.",
                listOf(
                    SagaOption("ranged", "Türme besetzen und den Feind auf Distanz zermürben", "Solide, aber erwartbar"),
                    SagaOption("reserve", "Den Wall dünn halten und eine starke Reserve bilden", "Genau ihr eigener Plan"),
                    SagaOption("sortie", "Nachts die Belagerungsmaschinen angreifen", "Riskant, aber kühn"),
                ))
            "ithiliel" -> SagaScene("meet_ithiliel", def.id, "Tanz der Klingen", def.location,
                "Im Licht tausender Kristalle tanzt Ithiliel für die Königin der Goldelben. Ihre Zwillingsklingen schneiden Lichtbahnen in die Luft. Als der Tanz endet, ruht ihr Blick auf dir, dem einzigen Sterblichen im Saal. „Ein Menschenfürst am Hof des Lichts. Was suchst du hier?“",
                listOf(
                    SagaOption("humble", "Dich verneigen und von deinem Volk erzählen", "Demut öffnet Elbenherzen"),
                    SagaOption("gift", "Ein Geschenk überreichen (1.000 Gold)", "Schöne Geste, aber Elben kauft man nicht"),
                    SagaOption("blades", "Eine Klingenprobe mit ihr verlangen", "Startet ein Duell gegen eine Meisterin"),
                ))
            else -> SagaScene("meet_${def.id}", def.id, "Begegnung", def.location, def.rumor, listOf(SagaOption("greet", "Grüßen")))
        }
    }

    // ------------------------------------------------------------ trial & recruitment

    data class TrialProgress(val current: Int, val target: Int, val done: Boolean, val text: String)

    fun trialProgress(state: GameState, heroineId: String): TrialProgress {
        val def = def(state, heroineId) ?: return TrialProgress(0, 1, false, "")
        val h = state.crown.heroine(heroineId) ?: return TrialProgress(0, 1, false, "")
        val (current, target) = when (def.trial) {
            CrownCatalog.TrialKind.OWN_PROVINCES -> state.crown.ownedProvinces.size to def.trialAmount
            CrownCatalog.TrialKind.DUEL -> (if (h.trialDuelWon) 1 else 0) to 1
            CrownCatalog.TrialKind.ARMY_SIZE -> state.armySize to def.trialAmount
            CrownCatalog.TrialKind.RENOWN -> state.renown to def.trialAmount
            CrownCatalog.TrialKind.WIN_BATTLES -> (victoryCount(state) - h.trialStartVictories).coerceAtLeast(0) to def.trialAmount
        }
        return TrialProgress(current.coerceAtMost(target), target, current >= target, def.trialText)
    }

    fun startTrial(state: GameState, heroineId: String): GameEngine.ActionResult {
        val h = state.crown.heroine(heroineId) ?: return ok(state, "Unbekannt.")
        val def = def(state, heroineId) ?: return ok(state, "Unbekannt.")
        if (h.stage != HeroineStage.MET) return ok(state, "Dafür müsst ihr euch zuerst begegnen.")
        val next = withHeroine(state, h.copy(stage = HeroineStage.TRIAL, trialStartDay = state.day, trialStartVictories = victoryCount(state)))
        return ok(next, "${def.name}: ${def.trialText}")
    }

    fun challenge(state: GameState, heroineId: String): GameEngine.ActionResult {
        val def = def(state, heroineId) ?: return ok(state, "Unbekannt.")
        if (state.crown.duel != null) return ok(state, "Ein Duell läuft bereits.")
        val next = CombatArtsEngine.startDuel(state, "trial:${def.id}", def.name, def.epithet, def.duelSkill)
        return ok(next, "${def.name} zieht ihre Waffe. Das Duell beginnt!")
    }

    fun recruit(state: GameState, heroineId: String, asCompanion: Boolean, force: Boolean = false): GameEngine.ActionResult {
        val def = def(state, heroineId) ?: return ok(state, "Unbekannt.")
        val h = state.crown.heroine(heroineId) ?: return ok(state, "Unbekannt.")
        if (h.stage == HeroineStage.COMPANION || h.stage == HeroineStage.GENERAL) return ok(state, "${def.name} dient dir bereits.")
        if (!force && (h.stage != HeroineStage.TRIAL || !trialProgress(state, heroineId).done))
            return ok(state, "Erfülle zuerst ihre Bewährungsprobe.")
        val companionFree = canBecomeCompanion(state, heroineId)
        return if (asCompanion && companionFree) becomeCompanion(state, def, h) else becomeGeneral(state, def, h)
    }

    /** With a custom partner, only she becomes the companion; the other heroines lead their own armies. */
    fun canBecomeCompanion(state: GameState, heroineId: String): Boolean =
        saga(state).companionId == null && !state.companion.met &&
            (saga(state).partner == null || isPartner(state, heroineId))

    private fun becomeCompanion(state: GameState, def: CrownCatalog.HeroineDef, h: HeroineState): GameEngine.ActionResult {
        val favor = h.favor.coerceIn(20, 100)
        val companion = CompanionProfile(
            met = true, name = if (isPartner(state, def.id)) def.name else def.name.substringBefore(' '), age = def.age, species = def.species,
            portraitUri = saga(state).partner?.takeIf { it.background == def.id }?.portraitUri ?: state.companion.portraitUri, armorStyle = def.armor, weapon = def.weapon, level = 3,
            sword = def.sword, bow = def.bow, riding = def.riding, leadership = def.leadership,
            tactics = def.tactics, diplomacy = def.diplomacy,
            trust = (20 + favor / 3).coerceAtMost(100), respect = (25 + favor / 3).coerceAtMost(100),
            affection = (10 + favor / 5).coerceAtMost(100), role = "Feldherrin",
        )
        var next = state.copy(companion = companion)
        next = withHeroine(next, h.copy(stage = HeroineStage.COMPANION, recruitedDay = state.day))
        next = withSaga(next, saga(next).copy(companionId = def.id, chapter = 0, chapterStartDay = state.day,
            chapterStartVictories = victoryCount(state), lastOutcome = "${def.name} reitet an deiner Seite."))
        next = RelationshipEngine.remember(next, "first_meeting", "${def.name} schwört dir in ${def.location} die Treue als Feldherrin.", 4, setOf("relationship", "milestone"))
        next = CrownEngine.chronicle(next, "${def.name} wird deine Feldherrin", "${def.name}, ${def.epithet}, übernimmt das Kommando über eigene Truppen und reitet von nun an an deiner Seite.")
        next = RelationshipEngine.syncCommander(next)
        return ok(next, "${def.name} ist jetzt deine Feldherrin. Weise ihr im Heer Truppen zu.")
    }

    private fun becomeGeneral(state: GameState, def: CrownCatalog.HeroineDef, h: HeroineState): GameEngine.ActionResult {
        val id = generalId(def.id)
        val commander = Commander(
            id = id, name = def.name, culture = def.culture, portraitKey = def.portraitKey, level = 5,
            sword = def.sword, bow = def.bow, leadership = def.leadership, tactics = def.tactics,
            siege = (def.tactics + def.leadership) / 2, loyalty = 85, trait = "Heldin", rank = "General",
            diplomacy = def.diplomacy,
        )
        var next = state.copy(commanders = state.commanders.filterNot { it.id == id } + commander)
        next = withHeroine(next, h.copy(stage = HeroineStage.GENERAL, recruitedDay = state.day))
        next = CharacterEngine.initialize(next)
        next = CrownEngine.chronicle(next, "${def.name} führt ein Heer", "${def.name}, ${def.epithet}, übernimmt als Feldherrin ein eigenes Heer deines Reiches.")
        return ok(next, "${def.name} führt nun als Feldherrin ein Heer. Weise ihr im Heer Truppen zu.")
    }

    // ------------------------------------------------------------ chapters

    private fun romanceBonus(chapter: Int): Triple<Int, Int, Int> = when (chapter) {
        3 -> Triple(10, 6, 10)
        4 -> Triple(8, 6, 8)
        5 -> Triple(10, 8, 10)
        6 -> Triple(9, 8, 9)
        7 -> Triple(8, 8, 8)
        8 -> Triple(6, 6, 6)
        else -> Triple(0, 0, 0)
    }

    private fun romanceThreshold(chapter: Int): Triple<Int, Int, Int> = when (chapter) {
        3 -> Triple(60, 45, 50)
        4 -> Triple(65, 50, 55)
        5 -> Triple(75, 60, 65)
        6 -> Triple(80, 65, 70)
        else -> Triple(85, 70, 75)
    }

    private fun personalQuestDone(state: GameState): Boolean = when (saga(state).companionId) {
        "alina" -> state.crown.ownedProvinces.size >= 3
        "seraphine" -> CrownEngine.province(state, "al_aldenstein")?.owner == ProvinceOwner.PLAYER
        "liang" -> state.armySize >= 15_000
        "ithiliel" -> state.renown >= 2_000
        else -> state.crown.ownedProvinces.size >= 4
    }

    fun personalQuest(state: GameState): Pair<String, String> {
        val def = companionDef(state)
        return if (def != null) def.personalQuestTitle to def.personalQuestText
        else "Ein eigenes Land" to "Deine Gefährtin träumt von einem sicheren Reich. Besitze vier Kronprovinzen."
    }

    private val royalTitles = setOf("König", "Hochkönig", "Großkönig", "Kaiser der Kronlande")
    private val noble = setOf("Fürst") + royalTitles

    /** Whether the next chapter can begin, plus a readable requirement. */
    fun chapterReady(state: GameState): Pair<Boolean, String> {
        val s = saga(state)
        val c = state.companion
        val waited = state.day - s.chapterStartDay
        return when (s.chapter) {
            0 -> (victoryCount(state) - s.chapterStartVictories >= 1) to "Gewinne gemeinsam eine Schlacht oder einen Feldzug."
            1 -> (waited >= 3 && c.trust >= 30) to "Einige Tage gemeinsam unterwegs (Vertrauen 30)."
            2 -> personalQuestDone(state) to personalQuest(state).let { "${it.first}: ${it.second}" }
            in 3..8 -> {
                val (t, r, a) = romanceThreshold(s.chapter)
                val (bt, br, ba) = romanceBonus(s.chapter)
                val need = Triple(t - bt, r - br, a - ba)
                val stats = c.trust >= need.first && c.respect >= need.second && c.affection >= need.third
                val title = when (s.chapter) {
                    7 -> state.title in noble
                    8 -> state.title in royalTitles
                    else -> true
                }
                val extra = when (s.chapter) { 7 -> " · Titel Fürst"; 8 -> " · Titel König"; else -> "" }
                val wait = if (s.chapter <= 4) 4 else 6
                (waited >= wait && stats && title) to "Vertrauen ${need.first} · Respekt ${need.second} · Zuneigung ${need.third}$extra"
            }
            FRIENDSHIP_COMPLETE -> false to "Ihr seid Schwurgeschwister. Eure Geschichte als Waffengefährten ist vollendet."
            else -> false to "Eure gemeinsame Geschichte ist erzählt – ihr regiert als Herrscherpaar."
        }
    }

    fun chapterTitle(chapter: Int): String = when (chapter) {
        0 -> "Feuertaufe"
        1 -> "Am Lagerfeuer"
        2 -> "Ihre Vergangenheit"
        3 -> "Schwur unter Bannern"
        4 -> "Erster Kuss"
        5 -> "Ein Versprechen"
        6 -> "Der Antrag"
        7 -> "Königliche Hochzeit"
        8 -> "Krönung der Königin"
        FRIENDSHIP_COMPLETE -> "Schwurgeschwister"
        else -> "Herrscherpaar"
    }

    private fun flavor(state: GameState): String = personalize(state, saga(state).companionId ?: "", baseFlavor(state))

    private fun baseFlavor(state: GameState): String = when (saga(state).companionId) {
        "alina" -> "Alina streicht über die Befiederung ihrer Pfeile, wie sie es immer tut, wenn sie nachdenkt."
        "seraphine" -> "Seraphine poliert die Scharten aus ihrem Schwert, ohne den Blick von dir zu nehmen."
        "liang" -> "Liang Mei schiebt Holzfiguren über eine Karte, als spiele sie ein Spiel, das nur sie versteht."
        "ithiliel" -> "Ithiliels Klingen ruhen gekreuzt auf ihren Knien; im Feuerschein glänzen sie wie Morgenlicht."
        else -> "${state.companion.name} sitzt neben dir, die Waffe griffbereit."
    }

    private fun chapterScene(state: GameState, chapter: Int): SagaScene {
        val n = state.companion.name
        val id = saga(state).companionId ?: "legacy"
        val f = flavor(state)
        val romanceOff = state.settings.romance == RomanceMode.OFF ||
            state.relationship.consent.relationshipStyle == RelationshipStyle.FRIENDSHIP || !state.relationship.consent.romanceAllowed
        return when (chapter) {
            0 -> SagaScene("chapter_0", id, chapterTitle(0), "Nach der Schlacht",
                "Rauch steigt über dem Feld auf. $n hat ihre Flanke gehalten, als deine Linie zu brechen drohte. Jetzt steht sie zwischen den Verwundeten, Blut an der Rüstung, und wartet darauf, was ihr Herr sagt.",
                listOf(SagaOption("praise", "Sie vor allen Truppen ehren", "Respekt"),
                    SagaOption("share", "Mit ihr die Verwundeten versorgen", "Vertrauen & Zuneigung"),
                    SagaOption("plan", "Sofort den nächsten Feldzug mit ihr planen", "Respekt & Taktik")))
            1 -> SagaScene("chapter_1", id, chapterTitle(1), "Feldlager",
                "Das Lager schläft. $f „Weißt du, warum ich wirklich mitgekommen bin?“ fragt $n leise.",
                listOf(SagaOption("listen", "Schweigen und zuhören", "Vertrauen"),
                    SagaOption("joke", "Sie mit einer Geschichte zum Lachen bringen", "Zuneigung"),
                    SagaOption("duty", "Über Pflicht, Krone und Verantwortung sprechen", "Respekt")))
            2 -> SagaScene("chapter_2", id, personalQuest(state).first, "Ihr persönlicher Kampf",
                "Es ist geschafft. ${personalQuest(state).second.substringBefore('.')} – und $n weiß, dass sie das ohne dich nie erreicht hätte. Zum ersten Mal siehst du Tränen in ihren Augen.",
                listOf(SagaOption("honor", "Ihr den Augenblick allein überlassen", "Großer Respekt"),
                    SagaOption("together", "An ihrer Seite stehen und sie halten", "Große Zuneigung")))
            3 -> SagaScene("chapter_3", id, chapterTitle(3), "Unter deinem Banner",
                "Am Vorabend eines großen Feldzugs findest du $n allein bei den Bannern. $f „Wenn wir morgen fallen“, sagt sie, „dann will ich, dass du weißt, was du mir bedeutest.“",
                listOfNotNull(
                    SagaOption("oath", "Einen ewigen Schwur als Waffengefährten leisten", "Beendet den romantischen Weg: Schwurgeschwister"),
                    SagaOption("feelings", if (romanceOff) "Romantik aktivieren und deine Gefühle gestehen" else "Ihr deine Gefühle gestehen", "Gegenseitiges Interesse"),
                    SagaOption("wait", "Den Moment verstreichen lassen", "In drei Tagen erneut")))
            4 -> SagaScene("chapter_4", id, chapterTitle(4), "Auf den Zinnen",
                "Der Sieg ist gefeiert, die Fackeln brennen herunter. $n lehnt neben dir an den Zinnen, so nah, dass sich eure Schultern berühren. Sie sieht dich an und fragt: „Darf ich?“",
                listOf(SagaOption("kiss", "„Ja.“ – und den Kuss erwidern", "Romanze"), SagaOption("wait", "Ihr sagen, dass du noch Zeit brauchst", "In drei Tagen erneut")))
            5 -> SagaScene("chapter_5", id, chapterTitle(5), "Im Morgengrauen",
                "Ihr reitet zu zweit durch das erwachende Land. $n zügelt ihr Pferd. „Ich will nicht mehr nur deine Feldherrin sein. Ich will an deiner Seite stehen – offen, vor allen.“",
                listOf(SagaOption("partner", "Euch offen zueinander bekennen", "Feste Partnerschaft"), SagaOption("wait", "Um Bedenkzeit bitten", "In drei Tagen erneut")))
            6 -> SagaScene("chapter_6", id, chapterTitle(6), "Im Thronsaal",
                "Du hast einen Ring schmieden lassen, mit den Farben ihrer Heimat. Der Thronsaal ist leer bis auf euch beide. $n bemerkt deine Nervosität und lächelt.",
                listOf(SagaOption("ring", "Niederknien und um ihre Hand bitten (2.500 Gold)", "Verlobung"), SagaOption("wait", "Den Ring noch einen Tag tragen", "In drei Tagen erneut")))
            7 -> SagaScene("chapter_7", id, chapterTitle(7), "Hochzeit in ${state.realm.settlementName}",
                "Boten aus allen Kronlanden fragen, wann die Hochzeit stattfindet. $n überlässt dir die Wahl – Hauptsache, ihr steht am Ende gemeinsam vor dem Altar.",
                listOf(SagaOption("wedding", "Königliche Hochzeit (8.000 Gold, 5.000 Nahrung)", "+400 Ruhm, Volk jubelt, Legitimität"),
                    SagaOption("small", "Schlichte Trauung im Burghof (1.000 Gold)", "+100 Ruhm"),
                    SagaOption("wait", "Die Hochzeit verschieben", "In drei Tagen erneut")))
            8 -> SagaScene("chapter_8", id, chapterTitle(8), "Krönungssaal",
                "Die Krone der Königin liegt auf rotem Samt. Fürsten aus allen eroberten Ländern sind gekommen. $n trägt ihre Rüstung unter dem Krönungsmantel – sie bleibt, was sie immer war: deine Feldherrin.",
                listOf(SagaOption("crown", "${n} zur Königin krönen (15.000 Gold)", "Herrscherpaar, +600 Ruhm"), SagaOption("wait", "Die Krönung vorbereiten", "In drei Tagen erneut")))
            else -> SagaScene("chapter_end", id, "Epilog", "", "Eure Geschichte ist erzählt.", listOf(SagaOption("ok", "Weiter")))
        }
    }

    // ------------------------------------------------------------ choices

    fun choose(state: GameState, optionId: String): GameEngine.ActionResult {
        val scene = saga(state).pendingScene ?: return ok(state, "Keine offene Szene.")
        val cleared = withSaga(state, saga(state).copy(pendingScene = null))
        return when {
            scene.key.startsWith("meet_") -> meetingChoice(cleared, scene, optionId)
            scene.key.startsWith("royal_") -> RoyalCourtEngine.choose(cleared, scene, optionId)
            else -> chapterChoice(cleared, scene, optionId)
        }
    }

    private fun meetingChoice(state: GameState, scene: SagaScene, option: String): GameEngine.ActionResult {
        val def = def(state, scene.heroineId) ?: return ok(state, "Unbekannt.")
        val h = state.crown.heroine(scene.heroineId) ?: return ok(state, "Unbekannt.")
        var next = state
        var favor = h.favor
        val impression: String
        when (scene.key to option) {
            "meet_alina" to "fight" -> { favor += 22; impression = "Ihr habt Seite an Seite den letzten Späher gestellt. Alina grinst: „Für einen Grundherrn nicht schlecht.“" }
            "meet_alina" to "honest" -> { favor += 18; impression = "Deine Ehrlichkeit überrascht sie. „Wenigstens lügst du nicht“, sagt Alina und senkt den Bogen." }
            "meet_alina" to "offer" -> { favor += 6; impression = "Alina lacht. „Gold kauft keine Treue, Grundherr.“ Doch sie geht nicht fort." }
            "meet_seraphine" to "challenge" -> {
                favor += 12
                next = CombatArtsEngine.startDuel(next, "trial:seraphine", def.name, def.epithet, def.duelSkill)
                impression = "Ein Raunen geht durch die Menge, als du Seraphine herausforderst. Sie lächelt zum ersten Mal."
            }
            "meet_seraphine" to "watch" -> {
                favor += 8
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold + 400))
                impression = "Du gewinnst deine Wette. Später sagt Seraphine: „Ihr habt meine Paraden gezählt. Klug.“"
            }
            "meet_seraphine" to "talk" -> { favor += 16; impression = "Am Brunnen erzählt Seraphine von Burg Aldenstein und dem Bruder, der sie verstieß." }
            "meet_liang" to "ranged" -> { favor += 10; impression = "Liang nickt knapp. „Solide. Erwartbar. Aber nicht falsch.“" }
            "meet_liang" to "reserve" -> { favor += 24; impression = "Liang Mei steht auf. „Endlich jemand, der denkt.“ Der Rat verstummt." }
            "meet_liang" to "sortie" -> { favor += 15; impression = "„Wahnsinn“, sagt Liang. „Aber ein Wahnsinn, der funktionieren könnte.“" }
            "meet_ithiliel" to "humble" -> { favor += 18; impression = "Ithiliel neigt den Kopf. „Ein Sterblicher, der zuhört. Selten.“" }
            "meet_ithiliel" to "gift" -> {
                if (next.resources.gold >= 1000) {
                    favor += 10
                    next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 1000))
                    impression = "Ithiliel nimmt das Geschenk an, doch ihr Blick bleibt kühl. Elben kauft man nicht."
                } else { favor += 4; impression = "Du hast nicht genug Gold dabei – ein peinlicher Moment am Elbenhof." }
            }
            "meet_ithiliel" to "blades" -> {
                favor += 12
                next = CombatArtsEngine.startDuel(next, "saga:ithiliel", def.name, def.epithet, def.duelSkill)
                impression = "Der Hof hält den Atem an, als Ithiliel ihre Klingen zieht. „Wie du wünschst.“"
            }
            else -> { favor += 10; impression = "Ihr begegnet euch zum ersten Mal." }
        }
        val personal = personalize(state, def.id, impression)
        next = withHeroine(next, h.copy(stage = HeroineStage.MET, metDay = state.day, favor = favor.coerceIn(0, 100), firstImpression = personal))
        next = withSaga(next, saga(next).copy(lastOutcome = personal))
        next = CrownEngine.chronicle(next, "Begegnung mit ${def.name}", personal)
        return ok(next, personal)
    }

    private fun adjust(state: GameState, trust: Int = 0, respect: Int = 0, affection: Int = 0): GameState {
        val c = state.companion
        return state.copy(companion = c.copy(
            trust = (c.trust + trust).coerceIn(0, 100),
            respect = (c.respect + respect).coerceIn(0, 100),
            affection = (c.affection + affection).coerceIn(0, 100),
        ))
    }

    private fun advance(state: GameState, chapter: Int, outcome: String): GameState =
        withSaga(state, saga(state).copy(chapter = chapter, chapterStartDay = state.day, chapterStartVictories = victoryCount(state), lastOutcome = outcome))

    private fun retry(state: GameState, outcome: String): GameState =
        withSaga(state, saga(state).copy(chapterStartDay = state.day, lastOutcome = outcome))

    private fun chapterChoice(state: GameState, scene: SagaScene, option: String): GameEngine.ActionResult {
        val chapter = saga(state).chapter
        val n = state.companion.name
        if (option == "wait") return ok(retry(state, "Ihr lasst euch Zeit."), "Ihr lasst euch Zeit. In drei Tagen kehrt der Moment zurück.")
        return when (chapter) {
            0 -> {
                val next = when (option) {
                    "praise" -> adjust(state, trust = 4, respect = 9)
                    "share" -> adjust(state, trust = 7, affection = 6)
                    else -> adjust(state, respect = 6).let { it.copy(companion = it.companion.copy(tactics = (it.companion.tactics + 2).coerceAtMost(100))) }
                }
                val text = "$n wird dieses Feld nie vergessen – und wie du dich danach verhalten hast."
                ok(RelationshipEngine.remember(advance(next, 1, text), "battle", text, 3, setOf("battle")), text)
            }
            1 -> {
                val next = when (option) {
                    "listen" -> adjust(state, trust = 10, affection = 5)
                    "joke" -> adjust(state, trust = 4, affection = 10)
                    else -> adjust(state, trust = 5, respect = 9)
                }
                val text = "Bis die Glut verlischt, redet ihr. $n vertraut dir mehr als je zuvor."
                ok(RelationshipEngine.remember(advance(next, 2, text), "campfire", text, 2), text)
            }
            2 -> {
                var next = if (option == "honor") adjust(state, trust = 14, respect = 12, affection = 8)
                    else adjust(state, trust = 12, respect = 8, affection = 13)
                next = next.copy(companion = next.companion.copy(
                    leadership = (next.companion.leadership + 5).coerceAtMost(100),
                    tactics = (next.companion.tactics + 5).coerceAtMost(100), level = next.companion.level + 2,
                    role = if (next.companion.role == "Mitregentin") next.companion.role else "Erste Feldherrin"))
                next = next.copy(renown = next.renown + 150)
                val text = "${personalQuest(state).first}: $n hat ihren Frieden gefunden. Sie ist jetzt die Erste Feldherrin deines Reiches."
                next = CrownEngine.chronicle(next, personalQuest(state).first, text)
                ok(RelationshipEngine.remember(advance(next, 3, text), "personal_arc", text, 5, setOf("milestone")), text)
            }
            3 -> when (option) {
                "oath" -> {
                    var next = adjust(state, trust = 12, respect = 12)
                    next = next.copy(companion = next.companion.copy(role = if (next.companion.role == "Mitregentin") next.companion.role else "Erste Feldherrin"))
                    val text = "Ihr schwört einander unter deinem Banner ewige Waffentreue. Schwurgeschwister bis zum Tod."
                    next = CrownEngine.chronicle(next, "Schwur unter Bannern", text)
                    ok(RelationshipEngine.remember(advance(next, FRIENDSHIP_COMPLETE, text), "oath", text, 5, setOf("milestone")), text)
                }
                else -> romanceStep(state, chapter, "confess")
            }
            4 -> romanceStep(state, chapter, "kiss")
            5 -> romanceStep(state, chapter, "partner")
            6 -> {
                if (state.resources.gold < 2500) return ok(retry(state, "Der Ring ist noch nicht bezahlt."), "Der Ring kostet 2.500 Gold.")
                romanceStep(state.copy(resources = state.resources.copy(gold = state.resources.gold - 2500)), chapter, "propose", refund = 2500)
            }
            7 -> {
                val grand = option == "wedding"
                val gold = if (grand) 8000 else 1000
                val food = if (grand) 5000 else 0
                if (state.resources.gold < gold || state.resources.food < food)
                    return ok(retry(state, "Für die Hochzeit fehlen Vorräte."), "Benötigt: $gold Gold${if (food > 0) " und $food Nahrung" else ""}.")
                val paid = state.copy(resources = state.resources.copy(gold = state.resources.gold - gold, food = state.resources.food - food))
                romanceStep(paid, chapter, "marry", refund = gold, refundFood = food) { s ->
                    s.copy(
                        renown = s.renown + if (grand) 400 else 100,
                        city = s.city.copy(satisfaction = (s.city.satisfaction + if (grand) 12 else 4).coerceAtMost(100)),
                        dynasty = s.dynasty.copy(legitimacy = (s.dynasty.legitimacy + if (grand) 12 else 5).coerceAtMost(100)),
                    )
                }
            }
            8 -> {
                if (state.resources.gold < 15000) return ok(retry(state, "Die Krönung braucht 15.000 Gold."), "Die Krönung kostet 15.000 Gold.")
                val prepared = state.copy(
                    resources = state.resources.copy(gold = state.resources.gold - 15000),
                    companion = state.companion.copy(diplomacy = maxOf(state.companion.diplomacy, 62)),
                )
                romanceStep(prepared, chapter, "co_ruler", refund = 15000) { s ->
                    CrownEngine.chronicle(s.copy(renown = s.renown + 600), "Krönung der Königin", "${s.companion.name} wird zur Königin gekrönt. Gemeinsam regiert ihr die Kronlande.")
                }
            }
            else -> ok(state, "")
        }
    }

    /** Applies the scene's emotional weight, then asks the existing consent-gated relationship engine. */
    private fun romanceStep(
        state: GameState,
        chapter: Int,
        action: String,
        refund: Int = 0,
        refundFood: Int = 0,
        onSuccess: (GameState) -> GameState = { it },
    ): GameEngine.ActionResult {
        var prepared = state
        if (prepared.settings.romance == RomanceMode.OFF) prepared = prepared.copy(settings = prepared.settings.copy(romance = RomanceMode.ROMANCE))
        val (t, r, a) = romanceBonus(chapter)
        prepared = adjust(prepared, trust = t, respect = r, affection = a)
        prepared = prepared.copy(relationship = prepared.relationship.copy(conflict = (prepared.relationship.conflict - 15).coerceAtLeast(0)))
        val before = prepared.relationship.romanceStage
        val result = RelationshipEngine.action(prepared, action)
        return if (result.state.relationship.romanceStage > before) {
            val text = result.message
            ok(onSuccess(advance(result.state, chapter + 1, text)), text)
        } else {
            // Nothing is forced: the scene returns in a few days, costs are refunded.
            val restored = state.copy(resources = state.resources.copy(gold = state.resources.gold + refund, food = state.resources.food + refundFood))
            ok(retry(restored, result.message), result.message)
        }
    }

    // ------------------------------------------------------------ duels

    /** Called by [CombatArtsEngine] when a saga or trial duel ends. */
    fun duelFinished(state: GameState, heroineId: String, won: Boolean): GameState {
        val h = state.crown.heroine(heroineId) ?: return state
        val def = def(state, heroineId) ?: return state
        val trialWin = won && (def.trial == CrownCatalog.TrialKind.DUEL || heroineId == "ithiliel")
        val next = withHeroine(state, h.copy(
            favor = (h.favor + if (won) 20 else 6).coerceIn(0, 100),
            trialDuelWon = h.trialDuelWon || (won && def.trial == CrownCatalog.TrialKind.DUEL),
        ))
        val text = if (won) "${def.name} kniet im Staub – und lacht. „Ihr seid es wert.“" else "${def.name} reicht dir die Hand. „Nicht schlecht. Versucht es wieder.“"
        return withSaga(next, saga(next).copy(lastOutcome = text)).let {
            if (trialWin && def.trial != CrownCatalog.TrialKind.DUEL) it.copy(renown = it.renown + 120) else it
        }
    }

    /** Dev and test helper: meet and recruit immediately. */
    fun recruitImmediately(state: GameState, heroineId: String): GameState {
        val ready = initialize(CrownEngine.initialize(state))
        val h = ready.crown.heroine(heroineId) ?: return ready
        val met = withHeroine(ready, h.copy(stage = HeroineStage.TRIAL, metDay = ready.day, favor = maxOf(h.favor, 70)))
        val asCompanion = canBecomeCompanion(met, heroineId)
        return recruit(met, heroineId, asCompanion, force = true).state
    }
}
