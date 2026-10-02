package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class MilitaryGood(val label: String) {
    SWORDS("Schwerter"),
    SPEARS("Speere"),
    BOWS("Bögen"),
    ARROWS("Pfeile"),
    ARMOR("Rüstungen"),
    SHIELDS("Schilde"),
    HORSES("Pferde"),
    SIEGE_PARTS("Belagerungsteile"),
    MEDICINE("Heilmittel"),
}

@Serializable
data class MilitaryStock(
    val swords: Int = 360,
    val spears: Int = 220,
    val bows: Int = 300,
    val arrows: Int = 6000,
    val armor: Int = 360,
    val shields: Int = 300,
    val horses: Int = 100,
    val siegeParts: Int = 80,
    val medicine: Int = 140,
) {
    fun amount(good: MilitaryGood): Int = when (good) {
        MilitaryGood.SWORDS -> swords
        MilitaryGood.SPEARS -> spears
        MilitaryGood.BOWS -> bows
        MilitaryGood.ARROWS -> arrows
        MilitaryGood.ARMOR -> armor
        MilitaryGood.SHIELDS -> shields
        MilitaryGood.HORSES -> horses
        MilitaryGood.SIEGE_PARTS -> siegeParts
        MilitaryGood.MEDICINE -> medicine
    }

    fun withAmount(good: MilitaryGood, value: Int): MilitaryStock {
        val safe = value.coerceIn(0, Int.MAX_VALUE)
        return when (good) {
            MilitaryGood.SWORDS -> copy(swords = safe)
            MilitaryGood.SPEARS -> copy(spears = safe)
            MilitaryGood.BOWS -> copy(bows = safe)
            MilitaryGood.ARROWS -> copy(arrows = safe)
            MilitaryGood.ARMOR -> copy(armor = safe)
            MilitaryGood.SHIELDS -> copy(shields = safe)
            MilitaryGood.HORSES -> copy(horses = safe)
            MilitaryGood.SIEGE_PARTS -> copy(siegeParts = safe)
            MilitaryGood.MEDICINE -> copy(medicine = safe)
        }
    }
}

@Serializable
enum class MilitaryDoctrine(val label: String, val description: String) {
    BALANCED("Ausgewogene Streitmacht", "Keine Spezialisierung; flexible Armee ohne Nachteile."),
    DISCIPLINED_LINE("Disziplinierte Linie", "Infanterie hält länger; etwas geringere Marschgeschwindigkeit."),
    MOBILE_WARFARE("Bewegungskrieg", "Reiter und Feldheere marschieren schneller; höherer Pferdebedarf."),
    RANGED_SUPREMACY("Fernkampfüberlegenheit", "Bogenschützen und Artillerie verursachen mehr Wirkung; hoher Pfeilverbrauch."),
    ELITE_CORE("Elitekern", "Kleine hochwertige Verbände profitieren stärker von Ausrüstung; Ausbildung dauert länger."),
    MASS_ARMY("Massenheer", "Ausbildung ist schneller und günstiger; durchschnittliche Ausrüstung wirkt etwas schwächer."),
    FOREST_WARFARE("Waldkrieg", "Waldelben und leichte Verbände kämpfen in Waldgebieten besonders effektiv."),
}

@Serializable
enum class ResearchTech(
    val label: String,
    val description: String,
    val branch: ResearchBranch,
    val days: Int,
    val goldCost: Int,
    val ironCost: Int = 0,
) {
    CROP_ROTATION("Fruchtfolge", "+8 % Nahrungsproduktion.", ResearchBranch.AGRICULTURE, 8, 450),
    FIELD_MEDICINE("Feldmedizin", "Mehr Verwundete überleben und erholen sich schneller.", ResearchBranch.LOGISTICS, 10, 650),
    SUPPLY_TRAINS("Versorgungskolonnen", "Feldheere verbrauchen weniger Nahrung.", ResearchBranch.LOGISTICS, 10, 700),
    FORGE_STANDARDIZATION("Standardisierte Schmieden", "Arsenal produziert mehr Schwerter und Rüstungen.", ResearchBranch.ENGINEERING, 12, 850, 120),
    COMPOSITE_BOWS("Verbundbögen", "Verbessert Fernkampfkraft und Bogenproduktion.", ResearchBranch.ENGINEERING, 12, 900, 100),
    SIEGE_ENGINEERING("Belagerungsingenieurwesen", "Belagerungsteile werden effizienter hergestellt.", ResearchBranch.ENGINEERING, 14, 1100, 180),
    OFFICER_CORPS("Professionelles Offizierskorps", "Kommandanten entwickeln sich schneller.", ResearchBranch.DIPLOMACY, 12, 900),
    CIVIC_ADMINISTRATION("Reichsverwaltung", "Mehr Wohlstand und bessere Steuerstabilität.", ResearchBranch.DIPLOMACY, 10, 800),
}

@Serializable
data class ResearchProject(
    val tech: ResearchTech,
    val remainingDays: Int,
    val totalDays: Int,
)

@Serializable
data class ResearchState(
    val completed: Set<ResearchTech> = emptySet(),
    val active: ResearchProject? = null,
)

@Serializable
enum class ReportCategory(val label: String) {
    ECONOMY("Wirtschaft"),
    MILITARY("Militär"),
    CITY("Stadt"),
    WORLD("Welt"),
    COURT("Hof"),
    DIPLOMACY("Diplomatie"),
    WARNING("Warnung"),
}

@Serializable
data class DailyReportEntry(
    val category: ReportCategory,
    val title: String,
    val detail: String,
    val important: Boolean = false,
)

@Serializable
data class DailyReport(
    val day: Int = 0,
    val entries: List<DailyReportEntry> = emptyList(),
)

@Serializable
data class PendingCommanderEvent(
    val id: String,
    val kind: CommanderEventKind,
    val commanderId: Long,
    val otherCommanderId: Long? = null,
    val title: String,
    val text: String,
    val expiresDay: Int,
)

@Serializable
data class CommanderEventState(
    val pending: PendingCommanderEvent? = null,
    val history: List<CommanderDevelopmentEvent> = emptyList(),
    val lastGeneratedDay: Int = 0,
)


@Serializable
enum class OccupationPolicy(val label: String, val description: String) {
    MILITARY_RULE(
        "Militärverwaltung",
        "Senkt Unruhe mit einer starken Garnison schnell, belastet aber Wohlstand und Beziehungen.",
    ),
    INTEGRATION(
        "Integration",
        "Kostet Verwaltung und Nahrung, senkt dafür Kulturspannung und Unruhe nachhaltig.",
    ),
    AUTONOMY(
        "Autonomie",
        "Weniger direkte Kontrolle und Einnahmen, dafür geringere politische Reibung.",
    ),
}

@Serializable
data class OccupiedRegion(
    val regionId: String,
    val previousOwnerId: String,
    val sinceDay: Int,
    val unrest: Int = 60,
    val policy: OccupationPolicy = OccupationPolicy.MILITARY_RULE,
    val governorId: Long? = null,
)
