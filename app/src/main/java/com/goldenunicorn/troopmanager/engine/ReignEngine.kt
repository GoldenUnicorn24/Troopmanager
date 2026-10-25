package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*

/** v2.0 daily systems: decision inbox, great works, feasts, bribes, weekly rhythm and achievements. */
object ReignEngine {
    fun tick(state: GameState): GameState {
        val fighting = state.battleSession?.isActive == true
        var next = InboxEngine.tick(state)
        next = tickWonders(next)
        if (!fighting) next = tickWeekly(next)
        next = provinceWorkshops(next)
        next = trackRomance(next)
        next = AchievementEngine.check(next)
        if (has(next, WonderKind.STAR_FORGE)) next = next.copy(militaryStock = next.militaryStock.copy(
            swords = add(next.militaryStock.swords, 400), armor = add(next.militaryStock.armor, 400),
            bows = add(next.militaryStock.bows, 250), shields = add(next.militaryStock.shields, 250)))
        if (has(next, WonderKind.CATHEDRAL)) next = next.copy(armyPools = next.armyPools.map { if (it.morale < 55) it.copy(morale = 55) else it })
        return next
    }

    // ------------------------------------------------------------------ province workshops

    /** Every crown province forges equipment for the army, scaled by its development. */
    fun workshopOutput(state: GameState): MilitaryStock {
        val owned = state.crown.ownedProvinces
        if (owned.isEmpty()) return MilitaryStock(0, 0, 0, 0, 0, 0, 0, 0, 0)
        var swords = 0; var spears = 0; var bows = 0; var armor = 0; var shields = 0; var horses = 0
        owned.forEach { p ->
            val f = 1 + p.development
            when (p.culture) {
                Culture.HUMAN -> { swords += 30 * f; armor += 30 * f; shields += 30 * f; horses += 4 * f }
                Culture.WOOD_ELF -> { bows += 30 * f; armor += 15 * f; swords += 15 * f }
                Culture.GOLD_ELF -> { spears += 25 * f; armor += 25 * f; bows += 15 * f; shields += 15 * f }
                Culture.WALL -> { spears += 25 * f; armor += 20 * f; shields += 20 * f; bows += 10 * f; swords += 10 * f }
            }
        }
        return MilitaryStock(swords, spears, bows, bows * 25, armor, shields, horses, owned.size * 2, owned.size * 5)
    }

    private fun provinceWorkshops(state: GameState): GameState {
        val out = workshopOutput(state)
        if (out.swords + out.armor + out.bows == 0) return state
        val s = state.militaryStock
        return state.copy(militaryStock = s.copy(
            swords = add(s.swords, out.swords), spears = add(s.spears, out.spears), bows = add(s.bows, out.bows),
            arrows = add(s.arrows, out.arrows), armor = add(s.armor, out.armor), shields = add(s.shields, out.shields),
            horses = add(s.horses, out.horses), siegeParts = add(s.siegeParts, out.siegeParts), medicine = add(s.medicine, out.medicine)))
    }

    private fun trackRomance(state: GameState): GameState =
        if (state.reign.firstRomanceDay == null && state.relationship.romanceStage.ordinal >= RomanceStage.ROMANCE.ordinal)
            state.copy(reign = state.reign.copy(firstRomanceDay = state.day)) else state

    // ------------------------------------------------------------------ great works

    fun has(state: GameState, kind: WonderKind): Boolean = state.reign.wonders.any { it.kind == kind && it.finished }
    fun building(state: GameState): WonderProject? = state.reign.wonders.firstOrNull { !it.finished }

    fun wonderCost(kind: WonderKind): Resources = Resources(kind.gold, 0, kind.wood, kind.stone, kind.iron)

    fun wonderBlocker(state: GameState, kind: WonderKind): String? = when {
        state.reign.wonders.any { it.kind == kind } -> if (has(state, kind)) "Bereits errichtet." else "Wird gerade gebaut."
        building(state) != null -> "Die Baumeister arbeiten schon an ${building(state)!!.kind.label}."
        state.battleSession?.isActive == true -> "Nicht während einer Schlacht."
        !EconomyEngine.canPay(state.resources, wonderCost(kind)) -> "Es fehlen Rohstoffe: ${missing(state.resources, wonderCost(kind))}."
        else -> null
    }

    fun startWonder(state: GameState, kind: WonderKind): GameEngine.ActionResult {
        wonderBlocker(state, kind)?.let { return GameEngine.ActionResult(state, it) }
        val next = state.copy(
            resources = EconomyEngine.pay(state.resources, wonderCost(kind)),
            reign = state.reign.copy(wonders = state.reign.wonders + WonderProject(kind, state.day, state.day + kind.days)),
            chronicle = state.chronicle + ChronicleEntry(state.day, "Grundstein: ${kind.label}", kind.description),
        )
        return GameEngine.ActionResult(next, "Der Bau von ${kind.label} beginnt. Fertig in ${kind.days} Tagen.")
    }

    private fun tickWonders(state: GameState): GameState {
        val project = building(state) ?: return state
        if (state.day < project.doneDay) return state
        var next = state.copy(reign = state.reign.copy(wonders = state.reign.wonders.map { if (it == project) it.copy(finished = true) else it }))
        if (project.kind == WonderKind.ROYAL_PALACE)
            next = next.copy(crown = next.crown.copy(rivals = next.crown.rivals.map { if (it.status == RivalStatus.DEFEATED) it else it.copy(attitude = (it.attitude + 15).coerceAtMost(100)) }))
        if (project.kind == WonderKind.CATHEDRAL) next = next.copy(city = next.city.copy(satisfaction = (next.city.satisfaction + 10).coerceAtMost(100)))
        next = next.copy(chronicle = next.chronicle + ChronicleEntry(next.day, "${project.kind.label} vollendet", project.kind.effect))
        return CelebrationEngine.add(next, CelebrationKind.WONDER, project.kind.label, "Nach ${project.kind.days} Tagen ist das Werk vollendet. ${project.kind.effect}.")
    }

    // Effects other engines ask for.
    fun wallDamageFactor(state: GameState): Double = if (has(state, WonderKind.GREAT_WALL)) 0.5 else 1.0
    fun tradeFactor(state: GameState): Double = if (has(state, WonderKind.GREAT_HARBOR)) 1.35 else 1.0
    fun bonusFood(state: GameState): Int = if (has(state, WonderKind.GREAT_HARBOR)) 1_500 else 0
    fun trainingCapacityFactor(state: GameState): Double = if (has(state, WonderKind.WAR_ACADEMY)) 2.0 else 1.0
    fun powerFactor(state: GameState): Double = if (has(state, WonderKind.WAR_ACADEMY)) 1.10 else 1.0
    fun ballistaeMorale(state: GameState): Int = if (has(state, WonderKind.DRAGON_BALLISTAE)) 20 else 0
    fun foodStorageFactor(state: GameState): Int = if (has(state, WonderKind.GRANARY)) 3 else 1
    fun provisionFactor(state: GameState): Double = if (has(state, WonderKind.GRANARY)) 0.5 else 1.0

    // ------------------------------------------------------------------ feasts

    fun feastCost(state: GameState): Int {
        val base = 3_000 + (state.crown.subjects / 50).coerceAtMost(60_000).toInt() + state.armySize / 4
        return if (has(state, WonderKind.ROYAL_PALACE)) base / 2 else base
    }

    fun feastCooldown(state: GameState): Int = (state.reign.lastFeastDay + 7 - state.day).coerceAtLeast(0)

    fun holdFeast(state: GameState): GameEngine.ActionResult {
        val cost = feastCost(state)
        val foodCost = cost / 2
        if (feastCooldown(state) > 0) return GameEngine.ActionResult(state, "Das nächste große Fest ist in ${feastCooldown(state)} Tagen möglich.")
        if (state.resources.gold < cost || state.resources.food < foodCost) return GameEngine.ActionResult(state, "Ein Königsfest kostet ${fmt(cost)} Gold und ${fmt(foodCost)} Nahrung.")
        var next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - cost, food = state.resources.food - foodCost),
            armyPools = state.armyPools.map { it.copy(morale = (it.morale + 15).coerceAtMost(100)) },
            city = state.city.copy(satisfaction = (state.city.satisfaction + 8).coerceAtMost(100)),
            renown = add(state.renown, 15),
            crown = state.crown.withProvinces(state.crown.provinces.map { if (it.owner == ProvinceOwner.PLAYER) it.copy(loyalty = (it.loyalty + 8).coerceAtMost(100), unrest = (it.unrest - 10).coerceAtLeast(0)) else it }),
            reign = state.reign.copy(feastsHeld = state.reign.feastsHeld + 1, lastFeastDay = state.day),
        )
        if (state.companion.met) next = next.copy(companion = next.companion.copy(affection = (next.companion.affection + 3).coerceAtMost(100)))
        next = next.copy(chronicle = next.chronicle + ChronicleEntry(state.day, "Königsfest", "Ein Fest für Heer und Volk: Moral +15, Loyalität der Provinzen +8."))
        if (state.reign.feastsHeld == 0) next = CelebrationEngine.add(next, CelebrationKind.FEAST, "Königsfest in ${state.realm.settlementName}",
            "Fackeln, Musik und Braten für ${fmt(state.armySize)} Soldaten. Das Heer jubelt seinem Herrscher zu." + if (state.companion.met) " ${state.companion.name} tanzt an deiner Seite." else "")
        return GameEngine.ActionResult(next, "Königsfest gefeiert: Moral +15, Zufriedenheit +8.")
    }

    // ------------------------------------------------------------------ companion gear

    val gearNames = listOf("Grenzrüstung", "Elbenstahl-Klinge", "Mithril-Kettenhemd", "Sternenbogen", "Königinnen-Rüstung")
    const val GEAR_MAX = 5

    fun gearCost(state: GameState): Int = 4_000 * (state.reign.companionGear + 1) * (state.reign.companionGear + 1)

    fun upgradeCompanionGear(state: GameState): GameEngine.ActionResult {
        if (!state.companion.met) return GameEngine.ActionResult(state, "Du hast deine Partnerin noch nicht getroffen.")
        val level = state.reign.companionGear
        if (level >= GEAR_MAX) return GameEngine.ActionResult(state, "${state.companion.name} trägt bereits die beste Ausrüstung der Kronlande.")
        val cost = gearCost(state)
        if (state.resources.gold < cost) return GameEngine.ActionResult(state, "${gearNames[level]} kostet ${fmt(cost)} Gold.")
        val c = state.companion
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - cost),
            companion = c.copy(sword = (c.sword + 5).coerceAtMost(100), bow = (c.bow + 5).coerceAtMost(100),
                leadership = (c.leadership + 4).coerceAtMost(100), tactics = (c.tactics + 4).coerceAtMost(100),
                affection = (c.affection + 2).coerceAtMost(100)),
            reign = state.reign.copy(companionGear = level + 1),
            chronicle = state.chronicle + ChronicleEntry(state.day, "Geschenk für ${c.name}", "${gearNames[level]} – sie kämpft stärker an deiner Seite."),
        )
        return GameEngine.ActionResult(next, "${c.name} erhält ${gearNames[level]}: Schwert, Bogen, Führung und Taktik steigen.")
    }

    // ------------------------------------------------------------------ bribes

    fun bribeCost(state: GameState, rivalId: String): Int {
        val rival = state.crown.rivals.firstOrNull { it.id == rivalId } ?: return Int.MAX_VALUE
        return 8_000 + state.reign.bribes * 2_000 + (if (rival.attitude < 0) -rival.attitude * 150 else 0)
    }

    fun bribe(state: GameState, rivalId: String): GameEngine.ActionResult {
        val rival = state.crown.rivals.firstOrNull { it.id == rivalId } ?: return GameEngine.ActionResult(state, "Unbekannter König.")
        if (rival.status == RivalStatus.DEFEATED) return GameEngine.ActionResult(state, "Dieser König ist bereits besiegt.")
        val cost = bribeCost(state, rivalId)
        if (state.resources.gold < cost) return GameEngine.ActionResult(state, "Eine Bestechung kostet ${fmt(cost)} Gold.")
        val name = ActCatalog.rival(rivalId)?.name ?: rivalId
        val next = state.copy(
            resources = state.resources.copy(gold = state.resources.gold - cost),
            crown = state.crown.copy(rivals = state.crown.rivals.map { if (it.id == rivalId) it.copy(attitude = (it.attitude + 30).coerceAtMost(100), status = if (it.status == RivalStatus.HOSTILE && it.attitude + 30 >= 0) RivalStatus.NEUTRAL else it.status) else it }),
            reign = state.reign.copy(bribes = state.reign.bribes + 1),
            chronicle = state.chronicle + ChronicleEntry(state.day, "Gold für $name", "Truhen voller Gold wechseln den Besitzer. Haltung +30."),
        )
        return GameEngine.ActionResult(next, "$name nimmt das Gold. Haltung +30.")
    }

    // ------------------------------------------------------------------ weekly rhythm

    data class WeeklyOption(val id: String, val label: String, val detail: String)

    fun weeklyOptions(state: GameState, event: WeeklyEvent): List<WeeklyOption> = when (event.kind) {
        WeeklyKind.ORC_STORM -> listOf(
            WeeklyOption("sortie", "Ausfall mit dem Heer", "Taktische Schlacht gegen ${fmt(stormStrength(state))} Orks vor der Mauer. Sieg: +60 Ruhm und Beute."),
            WeeklyOption("hold", "Hinter der Mauer bleiben", "Sicher, aber die Mauer nimmt Schaden (–8 %)."))
        WeeklyKind.HARVEST -> listOf(
            WeeklyOption("feast", "Erntefest feiern", "Ein Königsfest zum halben Preis: ${fmt(feastCost(state) / 2)} Gold."),
            WeeklyOption("store", "Alles einlagern", "+${fmt(harvest(state))} Nahrung."))
        WeeklyKind.TOURNAMENT -> listOf(
            WeeklyOption("fight", "Selbst antreten", "Ein Duell gegen den Champion des Turniers."),
            WeeklyOption("host", "Gastgeber sein", "3.000 Gold, dafür +30 Ruhm und Moral +5."))
        WeeklyKind.MERCHANTS -> listOf(
            WeeklyOption("buy", "Waffen kaufen", "${fmt(merchantPrice(state))} Gold für je 2.000 Schwerter, Bögen und Rüstungen."),
            WeeklyOption("pass", "Weiterziehen lassen", "Nichts geschieht."))
        WeeklyKind.PLAGUE -> listOf(
            WeeklyOption("heal", "Heiler bezahlen", "${fmt(plagueCost(state))} Gold, niemand stirbt."),
            WeeklyOption("ignore", "Abwarten", "3 % des Heeres sterben am Fieber."))
        WeeklyKind.ENVOYS -> listOf(
            WeeklyOption("gift", "Geschenke senden", "5.000 Gold: Haltung des freundlichsten Königs +25."),
            WeeklyOption("decline", "Höflich ablehnen", "Nichts geschieht."))
    }

    fun stormStrength(state: GameState): Int = (state.armySize * 0.55).toInt().coerceIn(600, 80_000)
    private fun harvest(state: GameState): Int = 5_000 + state.crown.ownedProvinces.size * 800
    private fun merchantPrice(state: GameState): Int = 9_000
    private fun plagueCost(state: GameState): Int = (state.armySize / 4).coerceIn(500, 20_000)

    private fun tickWeekly(state: GameState): GameState {
        if (state.day < 7 || state.day % 7 != 0 || state.reign.lastWeeklyDay == state.day) return state
        val pending = state.reign.weekly?.takeIf { !it.resolved }
        var next = state
        if (pending != null) next = chooseWeekly(next, weeklyOptions(next, pending).last().id).state
        val pool = WeeklyKind.entries.filter { it != next.reign.weekly?.kind }
        val kind = pool[((state.day / 7) * 7 + state.crown.ownedProvinces.size) % pool.size]
        return next.copy(reign = next.reign.copy(weekly = WeeklyEvent(kind, state.day), lastWeeklyDay = state.day))
    }

    fun chooseWeekly(state: GameState, optionId: String): GameEngine.ActionResult {
        val event = state.reign.weekly?.takeIf { !it.resolved } ?: return GameEngine.ActionResult(state, "Kein Wochenereignis offen.")
        var next = state
        val text: String = when ("${event.kind}:$optionId") {
            "ORC_STORM:sortie" -> {
                val result = TacticalBattleEngine.start(next, TacticalBattleEngine.Kind.STORM, "Ork-Sturm vor der Mauer", EnemyType.ORC, stormStrength(next))
                if (result.state === next) return result
                next = result.state
                "Das Heer rückt zum Ausfall aus."
            }
            "ORC_STORM:hold" -> { next = next.copy(realm = next.realm.copy(wallIntegrity = (next.realm.wallIntegrity - (8 * wallDamageFactor(next)).toInt()).coerceAtLeast(10))); "Die Orks rennen gegen die Mauer an und ziehen ab." }
            "HARVEST:feast" -> {
                val half = feastCost(next) / 2
                if (next.resources.gold < half) return GameEngine.ActionResult(state, "Dafür fehlen ${fmt(half - next.resources.gold)} Gold.")
                val freed = next.copy(resources = next.resources.copy(gold = next.resources.gold - half + feastCost(next)), reign = next.reign.copy(lastFeastDay = -99))
                val feast = holdFeast(freed)
                if (feast.state === freed) return GameEngine.ActionResult(state, feast.message)
                next = feast.state; "Das Erntefest wird zum Königsfest."
            }
            "HARVEST:store" -> { next = next.copy(resources = EconomyEngine.add(next.resources, Resources(0, harvest(next), 0, 0, 0))); "Die Speicher sind voll." }
            "TOURNAMENT:fight" -> {
                val r = CombatArtsEngine.tournament(next)
                if (r.state === next) return r
                next = r.state; r.message
            }
            "TOURNAMENT:host" -> {
                if (next.resources.gold < 3_000) return GameEngine.ActionResult(state, "3.000 Gold benötigt.")
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 3_000), renown = add(next.renown, 30),
                    armyPools = next.armyPools.map { it.copy(morale = (it.morale + 5).coerceAtMost(100)) }); "Ritter aus allen Reichen feiern dein Turnier."
            }
            "MERCHANTS:buy" -> {
                val price = merchantPrice(next)
                if (next.resources.gold < price) return GameEngine.ActionResult(state, "${fmt(price)} Gold benötigt.")
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold - price), militaryStock = next.militaryStock.copy(
                    swords = add(next.militaryStock.swords, 2_000), bows = add(next.militaryStock.bows, 2_000), armor = add(next.militaryStock.armor, 2_000))); "Die Waffenkammern füllen sich."
            }
            "PLAGUE:heal" -> {
                val cost = plagueCost(next)
                if (next.resources.gold < cost) return GameEngine.ActionResult(state, "${fmt(cost)} Gold benötigt.")
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold - cost)); "Die Heiler bekommen das Fieber in den Griff."
            }
            "PLAGUE:ignore" -> { next = ArmyEngine.applyLosses(next, next.armyPools.map { UnitAllocation(it.type, next.homeSoldiers(it.type) * 3 / 100) }.filter { it.amount > 0 }); "Das Fieber fordert Opfer im Lager." }
            "ENVOYS:gift" -> {
                if (next.resources.gold < 5_000) return GameEngine.ActionResult(state, "5.000 Gold benötigt.")
                val target = next.crown.rivals.filter { it.status != RivalStatus.DEFEATED }.maxByOrNull { it.attitude }
                next = next.copy(resources = next.resources.copy(gold = next.resources.gold - 5_000),
                    crown = next.crown.copy(rivals = next.crown.rivals.map { if (it.id == target?.id) it.copy(attitude = (it.attitude + 25).coerceAtMost(100)) else it }))
                "${target?.let { ActCatalog.rival(it.id)?.name } ?: "Die Gesandten"} nimmt die Geschenke an."
            }
            else -> "Die Woche vergeht ruhig."
        }
        next = next.copy(reign = next.reign.copy(weekly = event.copy(resolved = true, outcome = text)),
            chronicle = next.chronicle + ChronicleEntry(next.day, event.kind.label, text))
        return GameEngine.ActionResult(next, text)
    }

    // ------------------------------------------------------------------ helpers

    private fun add(a: Int, b: Int): Int = (a.toLong() + b).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    private fun missing(have: Resources, need: Resources): String = ResourceKind.entries
        .filter { it.value(have) < it.value(need) }.joinToString(", ") { "${fmt(it.value(need) - it.value(have))} ${it.label}" }
    internal fun fmt(value: Int): String = String.format(java.util.Locale.GERMANY, "%,d", value)
}
