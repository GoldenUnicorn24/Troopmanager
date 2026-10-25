package com.goldenunicorn.troopmanager.model

/**
 * v2.0 navigation: five areas, at most four tabs each, optional small sub-views.
 * Every place in the game is a [Spot]; engines point at spots, the UI resolves them.
 */
enum class MainArea(val label: String) { TODAY("Heute"), REALM("Reich"), ARMY("Heer"), COURT("Hof"), CITY("Stadt") }

data class NavTab(val label: String, val subs: List<String> = emptyList())

enum class Spot(val area: MainArea, val tab: Int, val sub: Int = 0) {
    INBOX(MainArea.TODAY, 0), LAGE(MainArea.TODAY, 1, 0), REPORT(MainArea.TODAY, 1, 1), TASKS(MainArea.TODAY, 2), CHRONICLE(MainArea.TODAY, 3),
    MAP(MainArea.REALM, 0), KINGS(MainArea.REALM, 1, 0), POWERS(MainArea.REALM, 1, 1), TRADE(MainArea.REALM, 2, 0), MARKET(MainArea.REALM, 2, 1), EDICTS(MainArea.REALM, 3),
    FRONT(MainArea.ARMY, 0), TROOPS(MainArea.ARMY, 1, 0), LEVY(MainArea.ARMY, 1, 1), ARSENAL(MainArea.ARMY, 1, 2), HOSPITAL(MainArea.ARMY, 1, 3),
    COMMANDERS(MainArea.ARMY, 2, 0), MISSIONS(MainArea.ARMY, 2, 1), ARTS(MainArea.ARMY, 3),
    PARTNER(MainArea.COURT, 0, 0), PAIR(MainArea.COURT, 0, 1), FAMILY(MainArea.COURT, 1), COUNCIL(MainArea.COURT, 2, 0), OFFICES(MainArea.COURT, 2, 1),
    NETWORK(MainArea.COURT, 2, 2), PROFILE(MainArea.COURT, 3, 0), SKILLS(MainArea.COURT, 3, 1),
    CITY_VIEW(MainArea.CITY, 0), WONDERS(MainArea.CITY, 1), RESEARCH(MainArea.CITY, 2),
}

object Navigation {
    val tabs: Map<MainArea, List<NavTab>> = mapOf(
        MainArea.TODAY to listOf(NavTab("Inbox"), NavTab("Lage", listOf("Überblick", "Tagesbericht")), NavTab("Aufgaben"), NavTab("Chronik")),
        MainArea.REALM to listOf(NavTab("Karte"), NavTab("Könige", listOf("Rivalen-Könige", "Weitere Mächte")), NavTab("Handel", listOf("Partner & Routen", "Markt")), NavTab("Edikte")),
        MainArea.ARMY to listOf(NavTab("Front"), NavTab("Truppen", listOf("Heer", "Heerbann", "Arsenal", "Lazarett")), NavTab("Feldherren", listOf("Feldherren", "Missionen")), NavTab("Kampfkunst")),
        MainArea.COURT to listOf(NavTab("Partnerin", listOf("Saga", "Zusammen")), NavTab("Familie"), NavTab("Rat", listOf("Rat", "Ämter", "Hofnetz")), NavTab("Herrscher", listOf("Profil", "Fertigkeiten"))),
        MainArea.CITY to listOf(NavTab("Stadt"), NavTab("Großbauten"), NavTab("Forschung")),
    )

    /** Primary spot of an area, used by the bottom bar. */
    fun home(area: MainArea): Spot = Spot.entries.first { it.area == area }

    /** Older saves and screens still speak in [GameDestination]s. */
    fun spotFor(destination: GameDestination): Spot = when (destination) {
        GameDestination.TODAY, GameDestination.DECISIONS -> Spot.INBOX
        GameDestination.COMMAND -> Spot.LAGE
        GameDestination.JOURNAL -> Spot.TASKS
        GameDestination.CHRONICLE -> Spot.CHRONICLE
        GameDestination.KINGDOM, GameDestination.WORLD -> Spot.MAP
        GameDestination.MILITARY -> Spot.TROOPS
        GameDestination.MISSIONS -> Spot.MISSIONS
        GameDestination.HOSPITAL -> Spot.HOSPITAL
        GameDestination.FRONTIER -> Spot.FRONT
        GameDestination.COURT -> Spot.PARTNER
        GameDestination.RULERS -> Spot.PAIR
        GameDestination.FAMILY -> Spot.FAMILY
        GameDestination.COUNCIL -> Spot.COUNCIL
        GameDestination.PALACE -> Spot.OFFICES
        GameDestination.CHARACTER -> Spot.PROFILE
        GameDestination.CITY -> Spot.CITY_VIEW
        GameDestination.RESEARCH -> Spot.RESEARCH
    }

    fun label(spot: Spot): String {
        val tab = tabs.getValue(spot.area)[spot.tab]
        return if (tab.subs.isEmpty()) "${spot.area.label} › ${tab.label}" else "${spot.area.label} › ${tab.label} › ${tab.subs[spot.sub]}"
    }
}
