package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

/** v2.0 state: decision inbox bookkeeping, great works, achievements and the weekly rhythm. */
@Serializable
data class ReignState(
    /** Inbox item key → day it first appeared; used to auto-settle ignored decisions. */
    val inboxSince: Map<String, Int> = emptyMap(),
    val autoSettled: Int = 0,
    val wonders: List<WonderProject> = emptyList(),
    val achievements: Set<String> = emptySet(),
    val weekly: WeeklyEvent? = null,
    val lastWeeklyDay: Int = 0,
    val feastsHeld: Int = 0,
    val lastFeastDay: Int = -99,
    val bribes: Int = 0,
    val tacticalVictories: Int = 0,
    val provisionsShortDays: Int = 0,
    val companionGear: Int = 0,
    /** March event key → day it last happened; events do not repeat within 30 days. */
    val marchSeen: Map<String, Int> = emptyMap(),
    val lastMarchDay: Int = -99,
    val firstRomanceDay: Int? = null,
)

@Serializable
enum class WonderKind(
    val label: String,
    val description: String,
    val gold: Int,
    val stone: Int,
    val wood: Int,
    val iron: Int,
    val days: Int,
    val effect: String,
) {
    GREAT_WALL("Große Mauer II", "Eine zweite Mauer aus Granit und Stahl hinter der ersten.", 40_000, 20_000, 8_000, 6_000, 10,
        "Mauerschlachten: Verteidiger +15 Kohäsion und mehr Deckung, Mauerschaden halbiert"),
    ROYAL_PALACE("Königspalast", "Ein Palast, der Könige einschüchtert und Gäste anzieht.", 60_000, 15_000, 15_000, 2_000, 12,
        "+15 Beziehung zu allen Königen, Feste kosten die Hälfte"),
    WAR_ACADEMY("Kriegsakademie", "Offiziere lernen Taktik, Rekruten werden schneller ausgebildet.", 50_000, 10_000, 10_000, 8_000, 10,
        "Ausbildung halb so lang, +10 % Kampfkraft auf Feldzügen"),
    GREAT_HARBOR("Großer Hafen", "Ein Hafen für Flotten aus allen Königreichen.", 45_000, 12_000, 20_000, 4_000, 9,
        "+35 % Handelseinkommen, Nahrung +1.500/Tag"),
    CATHEDRAL("Kathedrale der Mauer", "Ein Ort, an dem das Volk den Fall der Mauer nie vergisst.", 35_000, 25_000, 6_000, 1_000, 11,
        "Moral sinkt nie unter 55, Zufriedenheit +10"),
    DRAGON_BALLISTAE("Drachen-Ballisten", "Riesige Ballisten auf jedem Turm.", 55_000, 8_000, 12_000, 15_000, 9,
        "Feindliche Moral −20 zu Beginn jeder Mauerschlacht"),
    GRANARY("Königliche Kornkammer", "Speicher für ein ganzes Jahr.", 25_000, 10_000, 10_000, 0, 7,
        "Nahrungslager ×3, Proviant für Feldzüge halbiert"),
    STAR_FORGE("Sternenschmiede", "Eine Schmiede, deren Feuer nie erlischt.", 70_000, 12_000, 8_000, 25_000, 12,
        "Täglich +400 Schwerter und Rüstungen, +250 Bögen und Schilde"),
}

@Serializable
data class WonderProject(val kind: WonderKind, val startedDay: Int, val doneDay: Int, val finished: Boolean = false)

@Serializable
enum class WeeklyKind(val label: String, val text: String) {
    ORC_STORM("Ork-Sturm", "Späher melden einen großen Ork-Sturm. Wer die Mauer hält, gewinnt viel Ruhm."),
    HARVEST("Erntefest", "Die Ernte war gut. Ein Fest würde das Volk begeistern."),
    TOURNAMENT("Großes Turnier", "Ritter aus allen Königreichen kommen zum Turnier an die Mauer."),
    MERCHANTS("Händlerzug", "Ein großer Händlerzug bietet seltene Waren zu guten Preisen an."),
    PLAGUE("Fieber im Lager", "Ein Fieber breitet sich im Heerlager aus. Heiler kosten Gold."),
    ENVOYS("Gesandte", "Gesandte eines Königs bieten ein Bündnis gegen Geschenke an."),
}

@Serializable
data class WeeklyEvent(val kind: WeeklyKind, val startedDay: Int, val resolved: Boolean = false, val outcome: String = "")
