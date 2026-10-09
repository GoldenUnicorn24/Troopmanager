package com.goldenunicorn.troopmanager.engine

import com.goldenunicorn.troopmanager.model.*
import kotlin.math.abs

/** Read-only explanations join logistics, society and military state without advancing the world. */
object CampaignInsightsEngine {
    data class Forecast(val days: Int, val expected: Resources, val lower: Resources, val upper: Resources,
        val overflow: Resources)
    data class Situation(val category: String, val title: String, val detail: String,
        val destination: GameDestination, val urgency: Int)

    /** Campaign-backed recommendation, always routed to the relevant playable screen. */
    data class NextAction(
        val title: String,
        val reason: String,
        val button: String,
        val destination: GameDestination,
        val priority: Int,
    )

    fun nextAction(state: GameState): NextAction {
        state.invasion?.takeIf { it.arrivalDay <= state.day + 2 }?.let {
            val remaining = (it.arrivalDay - state.day).coerceAtLeast(0)
            return NextAction(
                "Verteidigung vorbereiten",
                "Die ${it.enemy.label} erreicht deine Festung in $remaining Tagen. Prüfe Mauer, Munition und Garnison.",
                "Stadtverteidigung öffnen", GameDestination.CITY, 110,
            )
        }
        state.frontier.hordes.filter { it.discovered && it.daysToArrival <= 3 }
            .minByOrNull { it.daysToArrival }?.let {
                return NextAction(
                    "Grenze sichern: ${it.name}",
                    "Noch ${it.daysToArrival} Tage bis zum Angriff. Stelle Truppen und Vorräte bereit.",
                    "Grenzlage öffnen", GameDestination.FRONTIER, 105,
                )
            }
        state.campaign.pendingDecision?.let {
            return NextAction(
                it.title,
                "Dein Rat erwartet eine Entscheidung bis Tag ${it.expiresDay}. Die Folgen wirken auf dein Reich.",
                "Entscheidung prüfen", GameDestination.DECISIONS, 95,
            )
        }
        val unsupplied = state.world.playerFieldArmies.count { it.supplyDays < 3 }
        if (unsupplied > 0) return NextAction(
            "Feldheere benötigen Nachschub",
            "$unsupplied Feldheere verfügen über weniger als drei Tage Versorgung.",
            "Weltkarte und Versorgung", GameDestination.WORLD, 92,
        )
        val foodNet = EconomyEngine.production(state).net.food
        if (foodNet < 0 && state.resources.food.toLong() <= -foodNet.toLong() * 7L) {
            return NextAction(
                "Nahrungsengpass verhindern",
                "Vorrat ${state.resources.food}; täglicher Fehlbetrag ${-foodNet.toLong()}. Verbessere die Produktion.",
                "Stadtwirtschaft öffnen", GameDestination.CITY, 90,
            )
        }
        val wounded = state.war.wounded.sumOf { it.soldiers }
        val hospital = WarEngine.hospitalCapacity(state)
        if (wounded > hospital) return NextAction(
            "Lazarett überlastet",
            "$wounded Verwundete bei $hospital Behandlungsplätzen. Ausbau und Medizin werden benötigt.",
            "Lazarett und Heer öffnen", GameDestination.MILITARY, 87,
        )
        if (state.realm.wallIntegrity < 80) return NextAction(
            "Die Stadtmauer ist beschädigt",
            "Mauerzustand ${state.realm.wallIntegrity} %. Reparaturen verbessern deine Verteidigung.",
            "Stadt und Mauer öffnen", GameDestination.CITY, 75,
        )
        if (state.population.total >= state.city.housingCapacity) return NextAction(
            "Mehr Wohnraum schaffen",
            "${state.population.total} Einwohner teilen sich ${state.city.housingCapacity} Wohnplätze.",
            "Wohnviertel ausbauen", GameDestination.CITY, 70,
        )
        if (state.armyPools.any { it.equipment < 70 }) return NextAction(
            "Truppenausrüstung verbessern",
            "Mindestens ein Regiment ist schlecht ausgerüstet. Prüfe Arsenal und Truppen.",
            "Armee und Arsenal öffnen", GameDestination.MILITARY, 65,
        )
        if (state.player.skillPoints > 0) return NextAction(
            "Herrscher weiterentwickeln",
            "${state.player.skillPoints} Fertigkeitspunkte warten auf ihre Verteilung.",
            "Herrscherprofil öffnen", GameDestination.COURT, 45,
        )
        return NextAction(
            "Initiative im Reich übernehmen",
            "Deine unmittelbare Lage ist stabil. Plane Aufklärung, einen Ausbau oder einen sicheren Feldzug.",
            "Weltkarte öffnen", GameDestination.WORLD, 20,
        )
    }

    fun forecast(state: GameState, days: Int): Forecast {
        require(days in 1..14)
        var expected = state.resources
        var lower = state.resources
        var upper = state.resources
        var overflow = Resources(0, 0, 0, 0, 0)
        repeat(days) { offset ->
            val projected = state.copy(day = state.day + offset,
                realm = state.realm.copy(tradeBonusDays = (state.realm.tradeBonusDays - offset).coerceAtLeast(0)))
            ResourceKind.entries.forEach { kind ->
                val b = EconomyEngine.breakdown(projected, kind)
                // Known season changes are included; weather, actions, demand and politics remain uncertain.
                val uncertainty = (b.gross.toLong() * if (kind == ResourceKind.FOOD) 20 else 10) / 100
                val cap = maxOf(kind.value(state.city.storageCapacity), kind.value(state.resources)).toLong()
                val next = kind.value(expected).toLong() + b.net
                expected = kind.withValue(expected, next.coerceIn(0, cap).toInt())
                lower = kind.withValue(lower, (kind.value(lower).toLong() + b.net - uncertainty).coerceIn(0, cap).toInt())
                upper = kind.withValue(upper, (kind.value(upper).toLong() + b.net + uncertainty).coerceIn(0, cap).toInt())
                overflow = kind.withValue(overflow, (kind.value(overflow).toLong() + (next - cap).coerceAtLeast(0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
        }
        return Forecast(days, expected, lower, upper, overflow)
    }

    fun situations(state: GameState): List<Situation> {
        val rows = mutableListOf<Situation>()
        val food = EconomyEngine.breakdown(state, ResourceKind.FOOD)
        val farmer = state.society.groups.firstOrNull { it.kind == PoliticalGroupKind.FARMERS }?.loyalty ?: 60
        val field = state.world.playerFieldArmies.filter { it.supplyDays < 3 }
        if (food.net < 0 || state.society.hunger > 25 || field.isNotEmpty()) {
            val days = if (food.net < 0) state.resources.food.toLong() / abs(food.net.toLong()).coerceAtLeast(1) else null
            rows += Situation("Versorgung & Gesellschaft", days?.let { "Nahrung reicht etwa $it Tage" } ?: "Nachschub im Feld gefährdet",
                "Heimat: ${food.gross} Ertrag − ${food.upkeep} Unterhalt/Tag; ${state.awayArmySize} Soldaten auswärts. Bauernloyalität $farmer %, Hunger ${state.society.hunger} %. ${field.size} Feldheere mit weniger als 3 Tagen Vorrat. Risiko: Moralverlust und politische Forderungen. Reaktion: Nahrung priorisieren, Posten auffüllen oder Konvoi senden.",
                if (field.isNotEmpty()) GameDestination.WORLD else GameDestination.CITY,
                if (days != null && days <= 7 || state.society.hunger > 50) 100 else 70)
        }
        state.frontier.hordes.filter { it.discovered }.minByOrNull { it.daysToArrival }?.let {
            rows += Situation("Grenzlage", it.name, "${it.estimatedStrengthLabel}, Ankunft in ${it.daysToArrival} Tagen. Mauer ${state.realm.wallIntegrity} %, ${state.militaryStock.arrows} Pfeile. Risiko: lokale Breschen bei Geräteangriffen. Reaktion: aufklären, abfangen, Garnison oder Mauer vorbereiten.", GameDestination.FRONTIER, if (it.daysToArrival <= 3) 110 else 80)
        }
        state.campaign.pendingDecision?.let {
            rows += Situation("Herrscherentscheidung", it.title, "${it.text} Frist: Tag ${it.expiresDay}. Reaktion: Folgen im Rat prüfen und entscheiden.", GameDestination.DECISIONS, 90)
        }
        state.society.demands.minByOrNull { it.deadlineDay }?.let {
            rows += Situation("Politischer Fall", it.kind.label, "${it.group.label}: Frist Tag ${it.deadlineDay}. Kriegsmüdigkeit ${state.society.warExhaustion} %, politische Loyalität ${state.society.politicalLoyalty} %. Risiko: abgelaufene Forderungen kosten Gruppenloyalität. Reaktion: finanzieren oder begründet ablehnen.", GameDestination.COUNCIL, 75)
        }
        val patients = state.war.wounded.sumOf { it.soldiers }
        if (patients > 0) rows += Situation("Lazarett & Heer", "$patients Verwundete", "Kapazität ${WarEngine.hospitalCapacity(state)}, Medizin ${state.militaryStock.medicine}. Risiko: Überlastung verlängert Genesung. Reaktion: Lazarett und Medizin sichern; Heimatschutz bis zur Rückkehr prüfen.", GameDestination.HOSPITAL, if (patients > WarEngine.hospitalCapacity(state)) 85 else 40)
        val full = ResourceKind.entries.filter { EconomyEngine.breakdown(state, it).overflow > 0 }
        if (full.isNotEmpty()) rows += Situation("Wirtschaft", "Produktion läuft über", full.joinToString { "${it.label}: ${EconomyEngine.breakdown(state, it).overflow}/Tag" } + ". Ursache: Lagergrenze. Reaktion: Lagerhaus ausbauen, handeln oder einen geplanten Ausbau finanzieren.", GameDestination.CITY, 30)
        if (state.settings.dynasty && state.dynasty.heirId == null && state.player.age >= 40)
            rows += Situation("Langfristige Herrschaft", "Nachfolge ist offen", "Der Herrscher ist ${state.player.age}. Risiko: unklare Regentschaft belastet Legitimität. Reaktion: Erben, Mentoren und Regent im Familienbereich prüfen.", GameDestination.FAMILY, 55)
        return rows.sortedByDescending { it.urgency }.take(5)
    }

    fun societyDrivers(state: GameState): List<Pair<String, String>> = listOf(
        "Sicherheit" to "Stadt ${state.city.security} %, Kriminalität ${state.society.crime} %. Ungleichheit ${state.society.inequality} % und Hunger ${state.society.hunger} % erhöhen den Kriminalitätsdruck; Patrouillen senken ihn.",
        "Versorgung" to "${EconomyEngine.production(state).gross.food} Nahrungsertrag, ${EconomyEngine.upkeep(state)} Heimatunterhalt. Feldheere verbrauchen ihre eigenen Vorräte; leere Vorräte senken Moral.",
        "Gesundheit" to "${state.population.total}/${state.city.housingCapacity} Wohnplätze, Krankheit ${state.society.disease} %. Enge und Hunger erhöhen Krankheit; Lazarett und Kliniken senken sie.",
        "Politik" to "Kriegsmüdigkeit ${state.society.warExhaustion} %, kulturelle Spannung ${state.society.culturalTension} %. Teure Siege und lange Kriege belasten Soldaten; Integration, Versorgung und erfüllte Forderungen stabilisieren den Rat."
    )

    fun unitRole(type: UnitType): String = when {
        type == UnitType.DRAGON_ARTILLERY -> "Artillerie · Geräte und Strukturen bekämpfen; begrenzte Ladungen, geringe Nahkampfeignung."
        type == UnitType.KNIGHT -> "Mobile Reserve · Ausfall und Flanke auf offenem Terrain; Engpässe und Wald begrenzen die Wirkung."
        type == UnitType.CRANE_GUARD -> "Mauerspezialisten · Kranichwinden brauchen diese Truppen am selben Abschnitt und Feinde in Wandnähe."
        type.ranged >= 8 -> "Fernkampf · Pfeile und Sicht nötig; Mauerdeckung schützt, leere Köcher senken Schaden."
        type.defense >= type.attack -> "Linienhalter · verteidigt Tor, Bresche und Engpass; frische Reserven lösen erschöpfte Truppen ab."
        else -> "Sturminfanterie · wirkt erst bei echtem Kontakt; Leitern und Breschen begrenzen aktive Kämpfer."
    }
}
