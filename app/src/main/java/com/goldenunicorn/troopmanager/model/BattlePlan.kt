@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.goldenunicorn.troopmanager.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

@Serializable
enum class BattleDoctrine(val label: String, val description: String) {
    HOLD_WALL("Mauer halten", "Fernkampf aus Deckung; Reserve erst bei Kontakt oder Bresche."),
    RANGED_SUPERIORITY("Fernkampf-Überlegenheit", "Frühe Salven und Unterdrückung; höherer Pfeilverbrauch."),
    KILL_ZONE("Kill Zone", "Feuer erst auf kurze Distanz; konzentrierter Burst, Risiko gegen schnelle Gegner."),
    PRESERVE_TROOPS("Truppen schonen", "Sparsame Schüsse, späte Reserve und geschlossene Tore."),
    FLEXIBLE_DEFENSE("Flexible Verteidigung", "Reserve verstärkt die am stärksten bedrängte Front."),
    COUNTERATTACK("Ausfall / Gegenstoß", "Kontrollierter Ausfall bei klarer Überlegenheit; höhere eigene Risiken."),
    ;
    val shortLabel: String get() = when (this) {
        HOLD_WALL -> "Mauer halten"; RANGED_SUPERIORITY -> "Fernkampf"; KILL_ZONE -> "Kill Zone"
        PRESERVE_TROOPS -> "Schonen"; FLEXIBLE_DEFENSE -> "Flexibel"; COUNTERATTACK -> "Ausfall"
    }
}

@Serializable
enum class TargetPriority(val label: String) {
    NEAREST("Nächstgelegene Feinde"), LIGHT_INFANTRY("Leichte Infanterie"), DEVICES("Belagerungsgeräte"),
    MONSTERS("Monster"), ELITE("Elite"), RANGED("Gegnerische Fernkämpfer"), LARGEST("Größte Formation"),
}

@Serializable
enum class WallTargetPriority(val label: String) {
    AUTO("Nach Waffenrolle"), DENSE_GROUP("Dichteste Gruppe"), DEVICES("Rammböcke / Leitern / Türme"),
    HEAVY("Schwere Ziele"), MONSTERS("Monster"), FRONT("Gewählte Front"),
}

@Serializable
enum class ReservePolicy(val label: String) {
    HOLD("Streng zurückhalten"), THREATENED_GATE("Bedrohtes Tor verstärken"),
    WEAKEST_FRONT("Schwächste Front unterstützen"), MANUAL("Nur manuell"),
}

@Serializable
enum class AmmunitionPolicy(val label: String, val fireRate: Double, val consumption: Double) {
    SPARING("Sparsam", .65, .60), NORMAL("Normal", 1.0, 1.0), VOLLEY("Salvenfeuer", 1.65, 2.4),
}

@Serializable
enum class GatePolicy(val label: String) {
    CLOSED("Tor geschlossen halten"), CONTROLLED_SORTIE("Kontrollierten Ausfall zulassen"),
    GATE_DEFENSE("Schwerpunkt Torverteidigung"),
}

@Serializable
enum class FallbackPolicy(val label: String, val moraleThreshold: Int, val cohesionThreshold: Int) {
    HOLD_LINE("Linie bis zur Bresche halten", 15, 15),
    WHEN_PRESSURED("Bei Moral / Kohäsion unter 30 zurückfallen", 30, 30),
    EARLY("Truppen bei Moral / Kohäsion unter 45 schützen", 45, 45),
}

@Serializable
enum class PursuitPolicy(val label: String) {
    HOLD("Stellung halten"), LIMITED("Begrenzte Verfolgung"), AGGRESSIVE("Aggressiver Ausfall"),
}

@Serializable
enum class CommanderRisk(val label: String) {
    CAUTIOUS("Vorsichtig"), BALANCED("Abgewogen"), BOLD("Risikobereit"),
}

/** Zero distance/threshold overrides keep the existing profile's automatic rules. */
@Serializable
data class BattlePlan(
    val doctrine: BattleDoctrine = BattleDoctrine.HOLD_WALL,
    val rangedPriority: TargetPriority = TargetPriority.NEAREST,
    val wallWeaponPriority: WallTargetPriority = WallTargetPriority.AUTO,
    val wallWeaponFront: BattleSection = BattleSection.CENTER,
    val reservePolicy: ReservePolicy = ReservePolicy.HOLD,
    val ammunitionPolicy: AmmunitionPolicy = AmmunitionPolicy.NORMAL,
    val gatePolicy: GatePolicy = GatePolicy.CLOSED,
    val breachReserveSection: BattleSection = BattleSection.CENTER,
    val breachReservePercent: Int = 30,
    val fallbackPolicy: FallbackPolicy = FallbackPolicy.HOLD_LINE,
    val pursuitPolicy: PursuitPolicy = PursuitPolicy.HOLD,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val preferredEngagementDistance: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fireReleaseDistance: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val holdFire: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frontPriorities: Map<BattleSection, TargetPriority> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val formations: Map<BattleSection, BattleFormation> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reserveThreshold: Int = 100,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val gateReservePercent: Int = 100,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fallbackThreshold: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val artilleryPriority: TargetPriority = TargetPriority.NEAREST,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val protectValuableUnits: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val commanderRisk: CommanderRisk = CommanderRisk.BALANCED,
) {
    fun priority(section: BattleSection): TargetPriority = frontPriorities[section] ?: rangedPriority
    val releaseDistance: Int get() = if (fireReleaseDistance > 0) fireReleaseDistance
        else if (doctrine == BattleDoctrine.KILL_ZONE) 110 else 350
    val moraleFallback: Int get() = if (fallbackThreshold > 0) fallbackThreshold else fallbackPolicy.moraleThreshold
    val cohesionFallback: Int get() = if (fallbackThreshold > 0) fallbackThreshold else fallbackPolicy.cohesionThreshold
    companion object {
        fun profile(doctrine: BattleDoctrine): BattlePlan = when (doctrine) {
            BattleDoctrine.HOLD_WALL -> BattlePlan()
            BattleDoctrine.RANGED_SUPERIORITY -> BattlePlan(doctrine = doctrine,
                rangedPriority = TargetPriority.LIGHT_INFANTRY, ammunitionPolicy = AmmunitionPolicy.VOLLEY)
            BattleDoctrine.KILL_ZONE -> BattlePlan(doctrine = doctrine,
                wallWeaponPriority = WallTargetPriority.DENSE_GROUP, ammunitionPolicy = AmmunitionPolicy.VOLLEY)
            BattleDoctrine.PRESERVE_TROOPS -> BattlePlan(doctrine = doctrine,
                ammunitionPolicy = AmmunitionPolicy.SPARING, fallbackPolicy = FallbackPolicy.EARLY)
            BattleDoctrine.FLEXIBLE_DEFENSE -> BattlePlan(doctrine = doctrine,
                reservePolicy = ReservePolicy.WEAKEST_FRONT, fallbackPolicy = FallbackPolicy.WHEN_PRESSURED)
            BattleDoctrine.COUNTERATTACK -> BattlePlan(doctrine = doctrine,
                reservePolicy = ReservePolicy.WEAKEST_FRONT, gatePolicy = GatePolicy.CONTROLLED_SORTIE,
                pursuitPolicy = PursuitPolicy.AGGRESSIVE)
        }
    }
}

@Serializable
data class ReserveReinforcement(val section: BattleSection, val soldiers: Int, val readyStep: Int,
    val order: BattleDecision? = null)
