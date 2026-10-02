package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class HeraldicSymbol(val label: String, val glyph: String) {
    WALL("Mauer", "▥"), STAR("Stern", "✦"), TREE("Baum", "♣"), SUN("Sonne", "☀"),
    CROWN("Krone", "♛"), SPEAR("Speer", "↑"),
}

@Serializable
data class Heraldry(
    val primaryArgb: Int = 0xFF203B57.toInt(),
    val secondaryArgb: Int = 0xFFFFE2A1.toInt(),
    val symbol: HeraldicSymbol = HeraldicSymbol.WALL,
)

@Serializable
enum class Achievement(val label: String, val description: String, val renown: Int) {
    FIRST_VICTORY("Erster Sieg", "Gewinne eine Schlacht.", 15),
    ARMY_10K("Zehntausend Banner", "Erreiche 10.000 Soldaten im gesamten Heer.", 30),
    ARMY_100K("Heer der Reiche", "Erreiche 100.000 Soldaten im gesamten Heer.", 100),
    KING("Die Krone", "Erreiche den Titel König oder Hochkönig.", 40),
    HIGH_KING("Die letzte Mauer", "Erreiche den Titel Hochkönig.", 80),
    LEGENDARY_GENERAL("Eine lebende Legende", "Ein General erreicht Stufe 6 und zehn Siege.", 40),
    DAYS_100("Hundert Tage", "Erlebe Tag 100.", 20),
    DAYS_365("Ein Jahr Geschichte", "Erlebe Tag 365.", 50),
    LOSSLESS_VICTORY("Ungebrochene Reihen", "Gewinne ohne Gefallene, Verwundete, Vermisste oder Gefangene.", 35),
    OUTNUMBERED_VICTORY("Gegen alle Chancen", "Gewinne mit weniger Soldaten als der Gegner.", 35),
}

@Serializable
data class AchievementUnlock(val achievement: Achievement, val day: Int)

@Serializable
enum class LegendKind { GENERAL, UNIT, BATTLE, RULER }

@Serializable
data class HallLegendEntry(
    val id: String,
    val name: String,
    val kind: LegendKind,
    val day: Int,
    val account: String,
    val commanderId: Long? = null,
)

@Serializable
data class CampaignRecords(
    val largestArmy: Int = 0,
    val largestVictory: Int = 0,
    val largestDefeat: Int = 0,
    val longestBattleMinutes: Int = 0,
    val highestPopulation: Int = 0,
    val mostTerritories: Int = 0,
    val highestDailyProduction: Long = 0,
)

@Serializable
data class PresentationState(
    val heraldry: Heraldry = Heraldry(),
    val achievements: List<AchievementUnlock> = emptyList(),
    val legends: List<HallLegendEntry> = emptyList(),
    val records: CampaignRecords = CampaignRecords(),
    val tutorialsSeen: Set<String> = emptySet(),
    val lastLegendMoraleDay: Int = 0,
)

enum class CityTime(val label: String) {
    MORNING("Morgen"), DAY("Tag"), EVENING("Abend"), NIGHT("Nacht");

    companion object {
        /** One campaign day has a repeatable visual clock; no real time changes the simulation. */
        fun at(day: Int): CityTime = entries[(day.coerceAtLeast(1) - 1) % entries.size]
    }
}

data class CityActivity(
    val citizens: Int, val merchants: Int, val wagons: Int, val soldiers: Int,
    val hungry: Boolean, val atWar: Boolean, val returningArmies: Int,
) {
    val groups: Int get() = citizens + merchants + wagons + soldiers + returningArmies
}

data class ContextTutorial(val id: String, val title: String, val text: String)
