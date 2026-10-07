@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName

@Serializable
enum class BattleContactState(val label: String, val allowsMelee: Boolean = false) {
    DISTANT("Außer Reichweite"), APPROACH("Im Anmarsch"), MISSILE_RANGE("Fernkampfweite"),
    SIEGE_CONTACT("Geräte an der Mauer"), WALL_ASSAULT("Kampf auf der Mauer", true),
    BREACHED("Lokale Bresche", true), COURTYARD("Innenhof", true), FIELD_CONTACT("Feldkontakt", true),
}

@Serializable
enum class RangedOrder(val label: String) { NORMAL("Normalfeuer"), HOLD("Feuer halten") }

@Serializable
enum class BattleAiIntent(val label: String) {
    APPROACH("Vorrücken"), MISSILES("Fernkampf"), ASSAULT("Mauersturm"),
    GATE("Tor angreifen"), ARTILLERY("Artillerie"), WITHDRAW("Abbrechen"),
}

@Serializable
enum class SiegeStage(val label: String) {
    SCOUTING("Aufklärung"), APPROACH("Annäherung"), MISSILE_FIRE("Fernkampf"),
    DEVICES_IN_RANGE("Geräte in Reichweite"), CONTACT("Mauer-/Torkontakt"),
    ASSAULT("Sturmversuch"), BREACH("Bresche"), REPULSED("Sturm zurückgeschlagen"),
    COURTYARD("Innenhof"), WITHDRAWAL("Rückzug / Flucht"), PURSUIT("Verfolgung"),
}

@Serializable
enum class BattleFormation(val label: String, val missileExposure: Double, val meleePower: Double) {
    LINE("Linie", 1.0, 1.0), LOOSE("Offene Ordnung", .75, .90),
    DENSE("Dichte Formation", 1.25, 1.12), SHIELD_WALL("Schildwall", .65, .95),
}

@Serializable
enum class BattleDamageCause(val label: String, val woundedPercent: Int) {
    ARROWS("Pfeile", 40), ARTILLERY("Artillerie", 20), WALL_WEAPONS("Mauerwaffen", 25),
    MELEE("Nahkampf", 35), BREACH("Bresche", 30), PURSUIT("Flucht / Verfolgung", 20),
    COLLAPSE("Einsturz / Strukturschaden", 15), LEGACY("Historische Ausfälle", 35),
}

/** Compact schema-5 keys keep aggregate saves within their existing size budget. */
@Serializable
data class BattleCauseBreakdown(
    @SerialName("s")
    val section: BattleSection,
    @SerialName("t")
    val type: UnitType,
    @SerialName("c")
    val cause: BattleDamageCause,
    @SerialName("o") @EncodeDefault(EncodeDefault.Mode.NEVER)
    val own: Int = 0,
    @SerialName("e") @EncodeDefault(EncodeDefault.Mode.NEVER)
    val enemy: Int = 0,
)

/** Projection of existing authoritative states, never a second simulation or ammunition store. */
data class BattleFrontStatus(
    val section: BattleSection, val distance: Int, val contact: BattleContactState,
    val frontage: Int, val ownSoldiers: Int, val enemySoldiers: Int,
    val terrain: BattleTerrain, val cover: Double, val morale: Int, val enemyMorale: Int,
    val cohesion: Int, val fatigue: Int, val visibility: Double, val arrows: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val formation: BattleFormation, val reserve: ReserveReinforcement?,
    val wallIntegrity: Int, val gateIntegrity: Int, val breachWidth: Int, val stage: SiegeStage,
)

@Serializable
data class FortificationSegmentState(
    val section: BattleSection,
    val integrity: Int = 100,
    val gateIntegrity: Int = 100,
    /** Fraction of ordinary missile exposure prevented by intact cover. */
    val cover: Double = 0.75,
    val breachWidth: Int = 0,
    val fire: Int = 0,
    val assaultProgress: Int = 0,
    val assaultWidth: Int = 0,
    val contactState: BattleContactState = BattleContactState.APPROACH,
    val rangedOrder: RangedOrder = RangedOrder.NORMAL,
    val gateOpen: Boolean = false,
    val fallenBack: Boolean = false,
    val devicePriority: Boolean = false,
    val siegeStage: SiegeStage = SiegeStage.APPROACH,
)

@Serializable
data class SiegeDeviceState(
    val id: String,
    val type: SiegeDevice,
    val section: BattleSection,
    val integrity: Int = 100,
    val distance: Int = 180,
    val progress: Int = 0,
    val crew: Int = 20,
    val disabled: Boolean = false,
    val detected: Boolean = true,
    val ammunition: Int = -1,
)

/** Persisted enemy formations are the sole source of enemy personnel attacks. */
@Serializable
data class EnemyBattleUnit(
    val type: UnitType,
    val section: BattleSection,
    val soldiers: Int,
    val startSoldiers: Int,
    val experience: Int = 0,
    val equipment: Int = 70,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val formation: BattleFormation = BattleFormation.LINE,
)

@Serializable
data class BattleDamageSources(
    val ranged: Int = 0,
    val melee: Int = 0,
    val splash: Int = 0,
    val wallWeapons: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val artillery: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pursuit: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val collapse: Int = 0,
) {
    val total: Int get() = ranged + melee + splash + wallWeapons + artillery + pursuit + collapse
}

@Serializable
data class FrontExchangeReport(
    val section: BattleSection,
    val contactState: BattleContactState,
    val frontage: Int = 0,
    val ownActive: Int = 0,
    val enemyActive: Int = 0,
    val ownShooters: Int = 0,
    val enemyShooters: Int = 0,
    val ownDamage: BattleDamageSources = BattleDamageSources(),
    val enemyDamage: BattleDamageSources = BattleDamageSources(),
    val preventedLosses: Double = 0.0,
    val structuralDamage: Int = 0,
    val gateDamage: Int = 0,
    val arrowsUsed: Int = 0,
    val enemyArrowsUsed: Int = 0,
    val deviceDamage: Int = 0,
    val artilleryChargesUsed: Int = 0,
    val suppression: Int = 0,
    val targetType: UnitType? = null,
    val targetDeviceId: String? = null,
    val volleys: Double = 0.0,
)

@Serializable
data class BattleExchangeReport(
    val minute: Int = 0,
    val fronts: List<FrontExchangeReport> = emptyList(),
    val events: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val causes: List<BattleCauseBreakdown> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val ownCasualties: CasualtyReport = CasualtyReport(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val enemyCasualties: CasualtyReport = CasualtyReport(),
) {
    val ownLosses: Int get() = fronts.sumOf { it.ownDamage.total }
    val enemyLosses: Int get() = fronts.sumOf { it.enemyDamage.total }
}

@Serializable
enum class BattleOutcomeGrade(val label: String) {
    DECISIVE_VICTORY("Entscheidender Sieg"), VICTORY("Sieg"), COSTLY_VICTORY("Kostspieliger Sieg"),
    TACTICAL_WITHDRAWAL("Taktischer Rückzug"), DEFEAT("Niederlage"),
    ROUT("Zusammenbruch"), CRUSHING_DEFEAT("Vernichtende Niederlage"),
}
