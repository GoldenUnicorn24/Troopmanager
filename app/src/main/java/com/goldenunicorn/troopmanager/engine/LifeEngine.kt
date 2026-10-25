package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.random.Random

/**
 * The personal life of the player character: game start in four modes, time of day, purse,
 * travel, small jobs, the landless day and a lightweight world pulse that keeps the crown
 * provinces moving while the player is "just" a mercenary in a tavern.
 *
 * Landless characters keep a dormant border keep in the save (all realm fields exist, but they
 * are neither simulated nor shown). This avoids a second, divergent world model: the provinces
 * the strategic map shows are the very same provinces the tavern rumors talk about.
 */
object LifeEngine {
    data class Result(val state: GameState, val message: String)

    const val ROOM_PRICE = 5
    const val KEEP_RENOWN = 150
    const val KEEP_GOLD = 800

    /** Everything the new-game wizard collects. */
    data class StartConfig(
        val mode: GameMode,
        val name: String,
        val age: Int,
        val gender: Gender,
        val species: Species,
        val culture: Culture,
        val background: Background,
        val profession: String,
        val wealth: WealthLevel,
        val homeProvinceId: String,
        val origin: String = "",
        val backstory: String = "",
        val appearance: CharacterAppearance = CharacterAppearance(),
        val attributes: GameEngine.StartingAttributes? = null,
        val startingCultures: Set<Culture>? = null,
        val weapon: String = "Langschwert",
        val armorStyle: String = "Grenzwächter",
        val ironman: Boolean = false,
        val storyActs: Boolean = true,
        val portraitUri: String? = null,
        val companionChoice: CompanionChoice = CompanionChoice.NONE,
        val companionDraft: NpcEngine.CompanionDraft? = null,
        /** Heroine path for landed games: alina, seraphine, liang or ithiliel. */
        val companionPath: String = "alina",
    )

    // ------------------------------------------------------------ new game

    fun newGame(config: StartConfig): GameState {
        require(config.age >= NpcEngine.MIN_ADULT_AGE) { "Der Spielercharakter muss erwachsen sein." }
        val bonus = backgroundBonus(config.background, config.attributes)
        val cultures = config.startingCultures?.takeIf { it.isNotEmpty() }
        val base = GameEngine.newGame(
            config.name, config.age, config.species, config.portraitUri, bonus,
            startingCultures = if (config.mode.landed) cultures else (cultures ?: setOf(config.culture)),
        )
        val appearance = config.appearance.normalized()
        val home = if (Towns.byProvince(config.homeProvinceId) != null) config.homeProvinceId else Towns.all.first().provinceId
        val townsfolk = Towns.all.flatMap { NpcEngine.populateTown(it) }
        val designed = config.companionDraft?.takeIf { config.companionChoice != CompanionChoice.NONE }?.let {
            NpcEngine.designCompanion(config.name.ifBlank { "held" }, it, home, config.gender)
        }
        val background = if (config.mode.landed) Background.BORDER_LORD else config.background.takeUnless { it.landed } ?: Background.VILLAGER
        val life = LifeState(
            mode = config.mode,
            landed = config.mode.landed,
            storyActs = when (config.mode) { GameMode.STORY -> true; GameMode.BORDER_LORD -> config.storyActs; else -> false },
            background = background,
            profession = config.profession.trim().ifBlank { background.profession }.take(28),
            gender = config.gender,
            culture = config.culture,
            origin = config.origin.trim().ifBlank { CrownCatalog.info(home).name }.take(40),
            backstory = config.backstory.trim().take(600),
            homeProvinceId = home,
            appearance = appearance,
            purse = if (config.mode.landed) 0 else config.wealth.gold,
            renown = if (config.mode.landed) 0 else background.renown,
            career = initialCareer(background),
            provinceId = home,
            place = LifePlace.TOWN,
            timeSlot = if (config.mode.landed) TimeSlot.MORNING else TimeSlot.AFTERNOON,
            npcs = townsfolk + listOfNotNull(designed?.let { if (config.mode.landed) it.copy(departed = true) else it }),
            companionChoice = if (designed == null) CompanionChoice.NONE else config.companionChoice,
            companionNpcId = designed?.id,
            lastPulseDay = 1,
        )
        var state = base.copy(
            life = life,
            settings = base.settings.copy(ironman = config.ironman),
            player = base.player.copy(weapon = config.weapon, armorStyle = config.armorStyle),
            crown = base.crown.copy(playerLook = appearance.toPortraitSpec()),
        )
        if (config.mode.landed) {
            // The designed companion follows the existing heroine saga: met in her own place after
            // about a week, then friendship first. Nothing romantic is pre-set.
            if (designed != null) state = HeroineSagaEngine.setPartner(state, PartnerProfile(
                designed.name, designed.age, designed.species, config.companionPath, null,
                look = designed.appearance.toPortraitSpec(),
            ))
        } else {
            state = state.copy(
                title = background.profession,
                rank = background.label,
                chronicle = listOf(ChronicleEntry(1, "Ein neues Leben",
                    "${state.player.name} erreicht ${CrownCatalog.info(home).name}. Kein Titel, kein Heer – nur ${life.purse} Gold, ein Bündel und ein offener Weg.")),
            )
            state = refreshJobs(state)
        }
        return state
    }

    private fun initialCareer(background: Background): CareerRank = when (background) {
        Background.RECRUIT -> CareerRank.RECRUIT
        Background.MERCENARY -> CareerRank.MERCENARY
        Background.TOWN_GUARD -> CareerRank.SOLDIER
        Background.BORDER_LORD -> CareerRank.LORD
        else -> CareerRank.COMMONER
    }

    /** Origin nudges two existing attributes by +5 (capped); the 100-point budget stays the player's. */
    fun backgroundBonus(background: Background, attrs: GameEngine.StartingAttributes?): GameEngine.StartingAttributes? {
        val a = attrs ?: return null
        fun up(v: Int) = (v + 5).coerceAtMost(100)
        return when (background) {
            Background.VILLAGER -> a.copy(riding = up(a.riding), diplomacy = up(a.diplomacy))
            Background.RECRUIT, Background.TOWN_GUARD -> a.copy(sword = up(a.sword), leadership = up(a.leadership))
            Background.MERCENARY -> a.copy(sword = up(a.sword), tactics = up(a.tactics))
            Background.ADVENTURER, Background.HUNTER -> a.copy(bow = up(a.bow), riding = up(a.riding))
            Background.MERCHANT -> a.copy(diplomacy = up(a.diplomacy), tactics = up(a.tactics))
            Background.POOR_NOBLE -> a.copy(diplomacy = up(a.diplomacy), leadership = up(a.leadership))
            Background.NOBLE_HEIR -> a.copy(diplomacy = up(a.diplomacy), riding = up(a.riding))
            Background.BORDER_LORD -> a
        }
    }

    // ------------------------------------------------------------ queries

    fun town(state: GameState): TownDef? = Towns.byProvince(state.life.provinceId)

    fun locationName(state: GameState): String = CrownCatalog.info(state.life.provinceId).name

    /** Charisma-like social value: diplomacy, with a small boost when tipsy and a penalty when drunk. */
    fun socialSkill(state: GameState): Int {
        val i = state.life.intoxication
        val drink = when { i in 10..35 -> 6; i in 36..60 -> -4; i > 60 -> -18; else -> 0 }
        val hangover = if (state.life.hangover) -6 else 0
        return (state.player.diplomacy + drink + hangover).coerceIn(0, 100)
    }

    /** Dexterity-like value for dice, archery and brawls. Alcohol hurts it quickly. */
    fun dexterity(state: GameState): Int {
        val i = state.life.intoxication
        return ((state.player.bow + state.player.sword) / 2 - i / 3 - (if (state.life.hangover) 5 else 0)).coerceIn(0, 100)
    }

    fun intoxicationLabel(value: Int): String = when {
        value >= 75 -> "Sturzbetrunken"
        value >= 50 -> "Betrunken"
        value >= 25 -> "Angeheitert"
        value > 0 -> "Leicht beschwingt"
        else -> "Nüchtern"
    }

    /** True when news of fighting near this province is fresh, or a crown threat targets it. */
    fun threatened(state: GameState, provinceId: String): Boolean =
        state.life.news.any { it.provinceId == provinceId && it.severity >= 2 && state.day - it.day <= 3 } ||
            state.crown.threats.any { it.targetId == provinceId }

    fun hasRoom(state: GameState): Boolean = state.life.roomPaidDay == state.day

    // ------------------------------------------------------------ time

    /** Lets time pass inside the day. Alcohol fades, conversations of the last slot are reset. */
    fun passTime(state: GameState, slots: Int = 1): Result {
        var life = state.life
        var steps = 0
        while (steps < slots) {
            val next = life.timeSlot.next ?: break
            life = life.copy(
                timeSlot = next,
                intoxication = (life.intoxication - if (life.fed) 14 else 9).coerceAtLeast(0),
                hangover = if (next >= TimeSlot.NOON) false else life.hangover,
                fatigue = (life.fatigue + 4).coerceAtMost(100),
                fed = if (next == TimeSlot.EVENING) false else life.fed,
            )
            steps++
        }
        if (steps == 0) return Result(state, "Es ist tiefe Nacht. Zeit zu schlafen.")
        life = life.copy(npcs = life.npcs.map { it.copy(intoxication = (it.intoxication - 12 * steps).coerceAtLeast(0), relation = it.relation.copy(topicsThisSlot = emptyList())) })
        return Result(state.copy(life = life), "${life.timeSlot.label} (${life.timeSlot.clock})")
    }

    /** Slots left before the night. */
    fun slotsLeft(state: GameState): Int = TimeSlot.NIGHT.ordinal - state.life.timeSlot.ordinal

    // ------------------------------------------------------------ money

    fun canPay(state: GameState, gold: Int): Boolean =
        if (state.life.landed) state.resources.gold >= gold else state.life.purse >= gold

    /** Personal spending: landless characters pay from their purse, rulers from the treasury. */
    fun pay(state: GameState, gold: Int): GameState =
        if (state.life.landed) state.copy(resources = state.resources.copy(gold = (state.resources.gold - gold).coerceAtLeast(0)))
        else state.copy(life = state.life.copy(purse = (state.life.purse - gold).coerceAtLeast(0)))

    fun earn(state: GameState, gold: Int): GameState =
        if (state.life.landed) state.copy(resources = state.resources.copy(gold = state.resources.gold + gold))
        else state.copy(life = state.life.copy(purse = state.life.purse + gold))

    fun money(state: GameState): Int = if (state.life.landed) state.resources.gold else state.life.purse

    fun addRenown(state: GameState, amount: Int): GameState {
        if (amount == 0) return state
        val life = state.life.copy(renown = (state.life.renown + amount).coerceAtLeast(0))
        val career = careerFor(life)
        return state.copy(life = life.copy(career = career), renown = if (state.life.landed) (state.renown + amount).coerceAtLeast(0) else state.renown)
    }

    private fun careerFor(life: LifeState): CareerRank {
        if (life.landed) return CareerRank.LORD
        val promoted = when (life.career) {
            CareerRank.RECRUIT -> if (life.renown >= CareerRank.SOLDIER.renown) CareerRank.SOLDIER else life.career
            CareerRank.SOLDIER, CareerRank.MERCENARY -> if (life.renown >= CareerRank.CAPTAIN.renown) CareerRank.CAPTAIN else life.career
            CareerRank.CAPTAIN -> if (life.renown >= CareerRank.COMMANDER.renown) CareerRank.COMMANDER else life.career
            else -> life.career
        }
        return promoted
    }

    fun log(state: GameState, line: String): GameState =
        state.copy(life = state.life.copy(log = (state.life.log + "Tag ${state.day}, ${state.life.timeSlot.label}: $line").takeLast(40)))

    // ------------------------------------------------------------ eating, drinking, sleeping

    fun drink(state: GameState, drink: Drink): Result {
        val price = priced(state, drink.price)
        if (!canPay(state, price)) return Result(state, "Dafür reicht dein Geld nicht.")
        val life = state.life
        val strength = if (life.fed) (drink.strength * 0.75).toInt() else drink.strength
        val intox = if (drink.strength == 0) (life.intoxication - 8).coerceAtLeast(0) else (life.intoxication + strength).coerceAtMost(100)
        var next = pay(state, price).let { it.copy(life = it.life.copy(intoxication = intox)) }
        val text = when {
            drink.strength == 0 -> "${drink.label}. Der Kopf wird klarer."
            intox >= 75 -> "${drink.label}. Der Raum dreht sich. Ein weiterer Becher wäre keine gute Idee."
            intox >= 50 -> "${drink.label}. Du lachst lauter als sonst und deine Hände werden ungenau."
            intox >= 25 -> "${drink.label}. Eine angenehme Wärme breitet sich aus – du fühlst dich gesprächig."
            else -> "${drink.label}. Schmeckt."
        }
        next = log(next, text)
        return Result(next, text)
    }

    fun eat(state: GameState, meal: Meal): Result {
        val price = priced(state, meal.price)
        if (!canPay(state, price)) return Result(state, "Dafür reicht dein Geld nicht.")
        var next = pay(state, price)
        next = next.copy(life = next.life.copy(fed = true, intoxication = (next.life.intoxication - 10).coerceAtLeast(0), fatigue = (next.life.fatigue - 8).coerceAtLeast(0)))
        return Result(log(next, "${meal.label} gegessen."), "${meal.label}. ${meal.description}")
    }

    /** War nearby makes everything dearer. */
    fun priced(state: GameState, base: Int): Int = if (base == 0) 0 else if (threatened(state, state.life.provinceId)) base + (base + 1) / 2 else base

    fun rentRoom(state: GameState): Result {
        if (hasRoom(state)) return Result(state, "Dein Zimmer ist für heute Nacht bezahlt.")
        val price = priced(state, ROOM_PRICE)
        if (!canPay(state, price)) return Result(state, "Ein Zimmer kostet $price Gold – so viel hast du nicht.")
        val next = pay(state, price).let { it.copy(life = it.life.copy(roomPaidDay = it.day)) }
        return Result(log(next, "Zimmer gemietet."), "Ein Zimmer unter dem Dach ist deins. Der Schlüssel ist schwer und kalt.")
    }

    // ------------------------------------------------------------ the landless day

    /** Sleeping ends the day. Landed rulers use [GameEngine.advanceDay], which calls [dailyLife]. */
    fun sleep(state: GameState): Result {
        val room = hasRoom(state)
        var next = state
        val note: String
        if (room) note = "Du schläfst unter dem Dach der Taverne."
        else {
            val r = Random(state.day * 97L + state.life.purse)
            val stolen = if (!state.life.landed && r.nextInt(100) < 25) (state.life.purse * 0.15).toInt().coerceAtMost(30) else 0
            next = if (stolen > 0) pay(next, stolen) else next
            next = next.copy(life = next.life.copy(fatigue = (next.life.fatigue + 15).coerceAtMost(100)))
            note = if (stolen > 0) "Du schläfst unter freiem Himmel. Am Morgen fehlen $stolen Gold." else "Du schläfst unter freiem Himmel. Hart und kalt."
        }
        val advanced = if (state.life.landed) GameEngine.advanceDay(next).let { Result(it.state, it.message) } else advanceLandlessDay(next)
        val woke = advanced.state.let { s -> s.copy(life = s.life.copy(place = if (room) LifePlace.TAVERN else LifePlace.TOWN)) }
        return Result(woke, "$note ${advanced.message}")
    }

    /** One landless day: personal state, people, travel, jobs and the living world. */
    fun advanceLandlessDay(state: GameState): Result {
        val before = state
        var next = state.copy(day = state.day + 1)
        next = dailyLife(next)
        next = arriveIfTraveling(next)
        next = worldPulse(next)
        next = deliverGossip(next)
        next = refreshJobs(next)
        next = next.copy(chronicle = next.chronicle.takeLast(CHRONICLE_LIMIT))
        val headline = next.life.news.lastOrNull { it.day == next.day }?.title
        return Result(next, "Tag ${next.day} beginnt in ${locationName(next)}." + (headline?.let { " Gerücht: $it" } ?: "") +
            if (next.life.purse != before.life.purse) " Börse: ${next.life.purse} Gold." else "")
    }

    /** Morning reset shared by landed and landless days. */
    fun dailyLife(state: GameState): GameState {
        val life = state.life
        val hangover = life.intoxication >= 50
        return state.copy(life = life.copy(
            timeSlot = TimeSlot.MORNING,
            intoxication = 0,
            hangover = hangover,
            fatigue = if (life.roomPaidDay == state.day - 1) 0 else (life.fatigue - 30).coerceIn(0, 100),
            fed = false,
            npcs = life.npcs.map { it.copy(intoxication = 0, relation = it.relation.copy(topicsThisSlot = emptyList())) },
        ))
    }

    // ------------------------------------------------------------ travel

    fun travelDays(fromId: String, toId: String): Int {
        val a = CrownCatalog.info(fromId).land
        val b = CrownCatalog.info(toId).land
        return when {
            fromId == toId -> 0
            a == b -> 1
            b in (CrownCatalog.neighbors[a] ?: emptySet()) -> 2
            else -> 3
        }
    }

    fun travelBlocker(state: GameState, toId: String): String? {
        if (state.life.landed) return "Als Herrscher reist du mit deinem Heer über die Kronlande-Karte."
        if (state.life.travel != null) return "Du bist bereits unterwegs."
        if (toId == state.life.provinceId) return "Du bist bereits hier."
        val province = CrownEngine.province(state, toId) ?: return "Unbekannter Ort."
        if (province.owner == ProvinceOwner.HORDE) return "${province.name} ist in der Hand der ${province.ownerLabel}. Kein Reisender kehrt von dort zurück."
        val cost = travelDays(state.life.provinceId, toId) * 3
        if (state.life.purse < cost) return "Für Proviant und Fährgeld brauchst du $cost Gold."
        return null
    }

    fun travel(state: GameState, toId: String): Result {
        travelBlocker(state, toId)?.let { return Result(state, it) }
        val days = travelDays(state.life.provinceId, toId)
        val cost = days * 3
        var next = pay(state, cost)
        next = next.copy(life = next.life.copy(travel = LifeTravel(state.life.provinceId, toId, state.day + days), place = LifePlace.AWAY))
        next = log(next, "Aufbruch nach ${CrownCatalog.info(toId).name} ($days Tage).")
        // The days on the road pass right away; the world keeps turning meanwhile.
        var message = ""
        repeat(days) { val r = advanceLandlessDay(next); next = r.state; message = r.message }
        return Result(next, "Nach $days Tag${if (days == 1) "" else "en"} auf der Straße erreichst du ${CrownCatalog.info(toId).name}. $message")
    }

    private fun arriveIfTraveling(state: GameState): GameState {
        val t = state.life.travel ?: return state
        if (state.day < t.arrivalDay) return state
        return state.copy(life = state.life.copy(provinceId = t.toProvinceId, place = LifePlace.TOWN, travel = null))
    }

    // ------------------------------------------------------------ the living world (landless)

    /**
     * Lightweight daily world simulation used while the player owns no realm: raids, bandits,
     * annexations and fairs change the shared crown provinces and become tavern rumors.
     * Player-owned provinces are never touched here; the full strategic engines run once landed.
     */
    fun worldPulse(state: GameState): GameState {
        if (state.life.landed || state.life.lastPulseDay >= state.day) return state
        val r = Random(state.day * 7919L + 17)
        var crown = state.crown
        val provinces = crown.provinces
        val news = mutableListOf<WorldNews>()
        val roll = r.nextInt(100)
        val recentFlip = state.life.news.any { it.severity >= 3 && state.day - it.day < 3 }
        fun pickFrom(list: List<CrownProvince>): CrownProvince? = if (list.isEmpty()) null else list[r.nextInt(list.size)]
        when {
            roll < 30 -> {
                val target = pickFrom(provinces.filter { it.owner != ProvinceOwner.HORDE && it.owner != ProvinceOwner.PLAYER &&
                    (it.land == CrownLand.SONNENHAIN || it.land == CrownLand.KRANICHWALL || it.land == CrownLand.GRENZMARK || it.id == "as_freistatt") })
                if (target != null) {
                    val raider = listOf(EnemyType.ORC, EnemyType.URUK, EnemyType.TAO_TEI)[r.nextInt(3)]
                    val hit = target.copy(garrison = (target.garrison * 0.85).toInt(), population = (target.population * 0.98).toInt(), unrest = (target.unrest + 10).coerceAtMost(100))
                    crown = crown.withProvinces(crown.provinces.map { if (it.id == target.id) hit else it })
                    news += WorldNews(state.day, target.id, "${CrownCatalog.hordeName(raider)} überfallen ${target.name}",
                        "Gehöfte brennen bei ${target.name}. Flüchtlinge berichten von ${CrownCatalog.hordeName(raider)} und suchen Schutz in den Städten.", 2)
                }
            }
            roll < 45 && !recentFlip -> {
                val target = pickFrom(provinces.filter { it.owner == ProvinceOwner.FREE && it.garrison < 1500 && Towns.byProvince(it.id) == null })
                if (target != null) {
                    crown = crown.withProvinces(crown.provinces.map { if (it.id == target.id) it.copy(owner = ProvinceOwner.BANDITS, garrison = it.garrison + 300) else it })
                    news += WorldNews(state.day, target.id, "Räuberbarone nehmen ${target.name}", "Ein Räuberbaron hat sich in ${target.name} festgesetzt und verlangt Wegzoll von jedem Karren.", 3)
                }
            }
            roll < 58 && !recentFlip -> {
                val target = pickFrom(provinces.filter { it.owner == ProvinceOwner.BANDITS })
                if (target != null) {
                    crown = crown.withProvinces(crown.provinces.map { if (it.id == target.id) it.copy(owner = ProvinceOwner.FREE, garrison = (it.garrison * 0.5).toInt()) else it })
                    news += WorldNews(state.day, target.id, "${target.name} ist frei", "Bauern und Söldner haben die Räuber aus ${target.name} vertrieben. Man feiert in den Schänken.", 3)
                }
            }
            roll < 68 && !recentFlip -> {
                val target = pickFrom(provinces.filter { it.owner == ProvinceOwner.FREE && it.land != CrownLand.GRENZMARK && Towns.byProvince(it.id) == null })
                if (target != null) {
                    crown = crown.withProvinces(crown.provinces.map { if (it.id == target.id) it.copy(owner = ProvinceOwner.RIVAL, garrison = it.garrison + 800) else it })
                    news += WorldNews(state.day, target.id, "Eine Krone greift nach ${target.name}", "Banner einer fremden Krone wehen über ${target.name}. Steuereintreiber sind schon unterwegs.", 3)
                }
            }
            roll < 82 -> {
                val target = pickFrom(provinces.filter { it.owner == ProvinceOwner.RIVAL || it.owner == ProvinceOwner.FREE })
                if (target != null) news += WorldNews(state.day, target.id, if (r.nextBoolean()) "Markttag in ${target.name}" else "Turnier in ${target.name}",
                    "In ${target.name} strömen Händler, Ritter und Gaukler zusammen. Wer Arbeit oder Ruhm sucht, findet beides dort.", 1)
            }
        }
        val life = state.life.copy(news = (state.life.news + news).takeLast(30), lastPulseDay = state.day)
        var next = state.copy(crown = crown, life = life)
        news.filter { it.severity >= 3 }.forEach { next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, it.title, it.text)) }
        return next
    }

    /** Rumors heard in this town: local news first, then the rest of the realm. */
    fun rumors(state: GameState, provinceId: String = state.life.provinceId): List<WorldNews> {
        val land = CrownCatalog.info(provinceId).land
        return state.life.news.sortedWith(compareByDescending<WorldNews> { it.provinceId == provinceId }
            .thenByDescending { CrownCatalog.info(it.provinceId).land == land }.thenByDescending { it.day }).take(6)
    }

    // ------------------------------------------------------------ jobs

    fun refreshJobs(state: GameState): GameState {
        if (state.life.landed) return state
        val province = state.life.provinceId
        val open = state.life.jobs.filter { (it.accepted && !it.done && !it.failed) || (!it.accepted && state.day - it.offeredDay <= 2) }
        if (open.count { it.provinceId == province && !it.accepted } >= 3) return state.copy(life = state.life.copy(jobs = open))
        val r = Random(state.day * 131L + province.hashCode())
        val givers = state.life.npcs.filter { it.homeProvinceId == province && !it.designedByPlayer && !it.departed }
        if (givers.isEmpty()) return state.copy(life = state.life.copy(jobs = open))
        val threatened = threatened(state, province)
        val town = CrownCatalog.info(province).name
        val templates = buildList {
            add(Triple("Ratten im Weinkeller", "Der Keller ist voller Ratten. Jemand mit ruhiger Hand und einem Knüppel soll aufräumen.", "sword"))
            add(Triple("Wölfe am Weidezaun", "Wölfe reißen Schafe vor $town. Ein guter Schütze wird gebraucht.", "bow"))
            add(Triple("Brief nach Eschenhof", "Ein versiegelter Brief muss heute noch übergeben werden. Schnell und verschwiegen.", "riding"))
            add(Triple("Schulden eintreiben", "Ein Händler schuldet Geld. Mit Worten oder mit Nachdruck – Hauptsache, er zahlt.", "diplomacy"))
            add(Triple("Wache für die Nacht", "Eine Karawane braucht Wachen bis zum Morgen.", "tactics"))
            if (threatened) add(Triple("Flüchtlinge geleiten", "Flüchtlinge müssen sicher in die Stadt. Die Horden sind nicht weit.", "leadership"))
        }
        val (title, text, skill) = templates[r.nextInt(templates.size)]
        val giver = givers[r.nextInt(givers.size)]
        val difficulty = 35 + r.nextInt(35) + if (threatened) 10 else 0
        val job = LifeJob(
            id = "job${state.life.nextId}", title = title, description = text, giverId = giver.id, provinceId = province,
            skill = skill, difficulty = difficulty, reward = 8 + difficulty / 3 + (if (threatened) 12 else 0), renown = 3 + difficulty / 12,
            slots = if (skill == "tactics") 2 else 1, offeredDay = state.day,
        )
        return state.copy(life = state.life.copy(jobs = open + job, nextId = state.life.nextId + 1))
    }

    fun skillValue(state: GameState, skill: String): Int = when (skill) {
        "sword" -> state.player.sword
        "bow" -> state.player.bow
        "riding" -> state.player.riding
        "leadership" -> state.player.leadership
        "tactics" -> state.player.tactics
        else -> socialSkill(state)
    }

    fun skillLabel(skill: String): String = when (skill) {
        "sword" -> "Schwert"; "bow" -> "Bogen"; "riding" -> "Reiten"; "leadership" -> "Führung"; "tactics" -> "Taktik"; else -> "Diplomatie"
    }

    fun acceptJob(state: GameState, jobId: String): Result {
        val job = state.life.jobs.firstOrNull { it.id == jobId } ?: return Result(state, "Diesen Auftrag gibt es nicht mehr.")
        if (job.accepted) return Result(state, "Du hast den Auftrag bereits angenommen.")
        val next = state.copy(life = state.life.copy(jobs = state.life.jobs.map { if (it.id == jobId) it.copy(accepted = true) else it }))
        return Result(log(next, "Auftrag angenommen: ${job.title}."), "Abgemacht: ${job.title}.")
    }

    /** Doing a job costs time; the result follows skill, sobriety and difficulty, not a blind dice roll. */
    fun doJob(state: GameState, jobId: String): Result {
        val job = state.life.jobs.firstOrNull { it.id == jobId && it.accepted && !it.done && !it.failed } ?: return Result(state, "Kein offener Auftrag.")
        if (slotsLeft(state) < job.slots) return Result(state, "Dafür reicht der Tag nicht mehr. Morgen früh.")
        if (state.life.intoxication >= 50) return Result(state, "So betrunken wirst du diesen Auftrag verpfuschen. Erst nüchtern werden.")
        val variance = Random(state.day * 31L + job.id.hashCode()).nextInt(-12, 13)
        val score = skillValue(state, job.skill) - state.life.intoxication / 2 - state.life.fatigue / 5 + variance
        val success = score >= job.difficulty
        var next = passTime(state, job.slots).state
        val giver = next.life.npc(job.giverId)
        next = next.copy(life = next.life.copy(jobs = next.life.jobs.map { if (it.id == jobId) it.copy(done = success, failed = !success) else it }))
        return if (success) {
            next = addRenown(earn(next, job.reward), job.renown)
            if (giver != null) next = updateNpc(next, NpcEngine.remember(giver.copy(relation = giver.relation.copy(affinity = (giver.relation.affinity + 10).coerceAtMost(100), trust = (giver.relation.trust + 8).coerceAtMost(100))),
                "job_${job.id}", next.day, "du hast „${job.title}“ zuverlässig erledigt", 2))
            Result(log(next, "Auftrag erfüllt: ${job.title} (+${job.reward} Gold)."), "Erledigt: ${job.title}. +${job.reward} Gold, +${job.renown} Ruf.")
        } else {
            next = next.copy(life = next.life.copy(fatigue = (next.life.fatigue + 15).coerceAtMost(100)))
            if (giver != null) next = updateNpc(next, NpcEngine.remember(giver.copy(relation = giver.relation.copy(affinity = (giver.relation.affinity - 6).coerceAtLeast(-100))),
                "job_${job.id}", next.day, "du hast „${job.title}“ vermasselt", -1))
            Result(log(next, "Auftrag gescheitert: ${job.title}."), "Gescheitert: ${job.title}. ${skillLabel(job.skill)} ${skillValue(state, job.skill)} reichte heute nicht.")
        }
    }

    // ------------------------------------------------------------ people

    fun updateNpc(state: GameState, npc: NpcIdentity): GameState =
        state.copy(life = state.life.copy(npcs = state.life.npcs.map { if (it.id == npc.id) npc.copy(relation = npc.relation.normalized()) else it }))

    /** A partner hears about an affair through gossip the day after (unless it was already discovered). */
    private fun deliverGossip(state: GameState): GameState {
        if (state.life.pendingGossip.isEmpty()) return state
        var next = state.copy(life = state.life.copy(pendingGossip = emptyList()))
        state.life.pendingGossip.forEach { entry ->
            val (partnerId, lover) = entry.split('|').let { it[0] to it.getOrElse(1) { "jemandem" } }
            val partner = next.life.npc(partnerId) ?: return@forEach
            val heard = Math.floorMod(partnerId.hashCode() + next.day, 100) < 60
            if (heard) next = updateNpc(next, learnsOfAffair(partner, lover, next.day))
        }
        return next
    }

    fun learnsOfAffair(partner: NpcIdentity, lover: String, day: Int): NpcIdentity {
        val honor = partner.personality.honor
        val r = partner.relation
        return NpcEngine.remember(partner.copy(relation = r.copy(
            trust = (r.trust - 35 - honor / 5).coerceAtLeast(0),
            affinity = (r.affinity - 25).coerceAtLeast(-100),
            hurtByAffairDay = day,
        )), "affair_$lover", day, "du hast mich mit $lover betrogen", -5)
    }

    // ------------------------------------------------------------ claiming a fief

    fun keepBlocker(state: GameState): String? = when {
        state.life.landed -> "Du herrschst bereits."
        state.life.renown < KEEP_RENOWN -> "Die Krone vergibt die Grenzfeste nur an bekannte Leute (Ruf ${state.life.renown}/$KEEP_RENOWN)."
        state.life.purse < KEEP_GOLD -> "Für Lehnseid und Ausrüstung brauchst du $KEEP_GOLD Gold (${state.life.purse})."
        else -> null
    }

    /**
     * The dormant border keep becomes the player's: the classic strategic game starts on the very
     * world state the character has been living in. Pending day-relative threats are cleared so
     * nothing from the frozen realm hits on the first day.
     */
    fun claimKeep(state: GameState): Result {
        keepBlocker(state)?.let { return Result(state, it) }
        val purse = state.life.purse
        var next = state.copy(
            life = state.life.copy(landed = true, purse = 0, career = CareerRank.LORD, place = LifePlace.TOWN),
            resources = state.resources.copy(gold = state.resources.gold / 2 + purse),
            invasion = null,
            crown = state.crown.copy(threats = emptyList(), act = state.crown.act.copy(startedDay = state.day)),
            title = "Grenzherr",
            rank = "Grenzhauptmann",
            renown = state.renown + state.life.renown,
        )
        next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, "Herr der Grenzfeste",
            "${next.player.name} leistet den Lehnseid und übernimmt die Grenzfeste – mit Gebiet, Vorräten und einer stehenden Armee."))
        return Result(next, "Du bist jetzt Grenzherr. Reich, Heer und Stadt stehen dir offen.")
    }

    // ------------------------------------------------------------ validation

    fun validate(state: GameState) {
        val life = state.life
        require(life.purse >= 0 && life.renown >= 0) { "Ungültige Börse." }
        require(life.intoxication in 0..100 && life.fatigue in 0..100) { "Ungültiger Zustand des Charakters." }
        require(life.npcs.map { it.id }.distinct().size == life.npcs.size) { "Doppelte Personen." }
        life.npcs.forEach(NpcEngine::validate)
        require(life.companionNpcId == null || life.npc(life.companionNpcId) != null) { "Gefährtin fehlt." }
    }
}
